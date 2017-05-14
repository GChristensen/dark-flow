;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.filters
  (:require
   [kuroi.io :as io]
   [kuroi.settings :as settings]

   [clojure.string :as str]
   [cljs.reader :as reader]
   )
  (:use-macros [kuroi.macros :only [cb-let cb-let?]]))

(defn re-quote [s]
  (let [special (set ".?*+^$[]\\(){}|")
        escfn #(if (special %) (str \\ %) %)]
    (apply str (map escfn s))))

;; filter supplied in the url :search command ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-pattern [target] true
  (when (:filter target)
    (re-pattern 
     (str "(?i)(?:^|>)[^<]*" (re-quote (:filter target)) "[^<]*(?:$|<)"))))

(defn post-matches? [pattern post]
  (or (and (:title post) (re-find pattern (:title post)))
      (and (:text post) (re-find pattern (:text post)))))

(defn filter-shallow [pattern post target]
  (if pattern
    (if (:deep target)
      post
      (when (post-matches? pattern post)
        post))
    post))

(defn filter-deep [pattern threads target]
  (if pattern
    (if (:deep target)
      (filter #(or (post-matches? pattern %)
                   (some (fn [child] (post-matches? pattern child)) (:children %)))
              threads)
      (filter (complement nil?) threads))
    threads))

;; main program filter to filter out hidden threads, wf, etc. ;;;;;;;;;;;;;;;;;;

(defn wordfilter-matches? [text words]
  (when (and text words)
    (let [text-lc (str/lower-case text)]
      (loop [words words]
        (let [word (first words)]
          (when word
            (if (string? word)
              (if (>= (.indexOf text-lc word) 0)
                true
                (recur (next words)))
              (if (re-find word text)
                true
                (recur (next words))))))))))

(defn filter-replies [wf-post thread target]
  (let [replies (:children thread)]
    (assoc thread :children
           (map (fn [reply]
                  (if (wordfilter-matches? (:text reply) wf-post)
                    (assoc reply 
                      :text (str "<span class='red'>Cut by wordfilter 
                                 (<a class=\"red wf-trigger\" 
                                     onclick=\"frontend.iv_expand_post(this, '" 
                                     (str (:link reply) "#" (when (:fourchan target) "p") 
                                          (:id reply))
                                      "')\">view</a>)</span>")
                      :thumb nil
                      :image nil
                      :title nil
                      :refs nil)
                    reply))
                (:children thread)))))

(defn- sort-by-id [threads]
  (sort-by #(let [n (js/parseInt (:id %))]
              (if (js/isNaN n) 0 n))
           >
           threads))

(defn- sort-by-id? [target threads]
  (if (:sortid target)
    (sort-by #(let [n (js/parseInt (:id %))]
                (if (js/isNaN n) 0 n))
             (if (:rev target) < >) 
             threads)
    threads))

(defn- filter-seen? [target threads seen-threads]
  (let [seen (into {} (map (fn [th] 
                             (let [last-reply (:id (last (:children th)))]
                               [(:id th) (if (nil? last-reply) "0" last-reply)]))
                           threads))
        to-show (when (and seen-threads (:new target))
                  (set (map key (filter #(let [prev (find seen-threads (key %))]
                                                 (or (not prev) 
                                                     (> (js/parseInt (val %))
                                                        (js/parseInt (val prev)))))
                                              seen))))]
    (io/put-data 'board (:prefix target) {:seen (pr-str seen)})
    (if to-show
      (filter #(to-show (:id %)) threads)
      threads)))

(defn- filter-expanded? [target threads expanded-threads]
  (if expanded-threads
    (map #(if (expanded-threads (:id %)) (assoc % :expanded true) %) threads)
    threads))  

(defn- do-filter [threads target callback settings forgotten-threads threads-on-watch 
                  seen-threads expanded-threads]
  (let [pin-watched? (:pin-watch-items settings)
        wf-enabled? (:wf-enabled settings)
        wf-title (:wf-title-parsed settings)
        wf-post (:wf-post-parsed settings)]

  (loop [all threads shown [] watch {} filtered 0 forgotten 0]
    (let [th (first all)]
      (if th
        (cond (and forgotten-threads (contains? forgotten-threads (:internal-id th)))
              (recur (next all) shown watch filtered (inc forgotten))
              
              (and wf-enabled?
                   (or (wordfilter-matches? (:title th) wf-title)
                       (wordfilter-matches? (:text th) wf-post)))
              (recur (next all) shown watch (inc filtered) forgotten)

              (and threads-on-watch (contains? threads-on-watch (:internal-id th)))
              (let [thread-id (:internal-id th)
                    watch-item (threads-on-watch thread-id)
                    post-count (:post-count th)
                    post-delta (- post-count (:post-count watch-item))
                    watched-thread (assoc th :onwatch true :post-delta post-delta 
                                        :last-id (:last-id watch-item))]
                (when (not= 0 post-delta)
                  (io/put-data 'watch thread-id 
                               {:oppost (pr-str 
                                         (assoc watch-item
                                           :post-count post-count
                                           :last-id (:last-id th)))}))
                (recur (next all)
                       (if pin-watched? shown (conj shown watched-thread))
                       (if pin-watched? (assoc watch thread-id watched-thread) watch)
                       filtered
                       forgotten))
              
              :default (recur (next all) (conj shown th) watch filtered forgotten))

        (let [shown (sort-by-id? target shown)
              shown (filter-seen? target shown seen-threads)]
          (if (and pin-watched? threads-on-watch)
            (let [out-of-scope (map (fn [kv] (let [w (second kv)]
                                               (assoc w
                                                 :onwatch true
                                                 :last-id (:last-id w)
                                                 :page-index "out of scope")))
                                    (filter (fn [[k v]] (not (contains? watch k))) 
                                            threads-on-watch))
                  threads-on-watch (sort-by-id (concat (map (fn [kv] (second kv))
                                                            watch) 
                                                       out-of-scope))
                  all-threads (concat threads-on-watch shown)
                  all-threads (filter-expanded? target all-threads expanded-threads)]
              (callback 
               (if wf-enabled? 
                 (map #(filter-replies wf-post % target) all-threads) 
                 all-threads)
               {:shown (count shown) 
                :filtered filtered 
                :forgotten forgotten 
                :watch (count watch)
                :watch-total (count threads-on-watch)}))
            (let [shown (filter-expanded? target shown expanded-threads)]
              (callback
               (if wf-enabled? (map #(filter-replies wf-post % target) shown) shown)
               {:shown (count shown) 
                :filtered filtered 
                :forgotten forgotten 
                :watch 0 
                :watch-total 0})))))))))

(defn filter-threads [threads target callback]
  (let [settings (settings/get-for target)]
    (cb-let [forgotten-threads-str] (io/get-data 'forgotten 'queue (:prefix target))
      (cb-let [watch-thread-strs] (io/get-data* 'watch 'oppost {:where "board" :eq (:prefix target)})
        (cb-let? (:new target) [seen-threads-str] (io/get-data 'board 'seen (:prefix target))
          (cb-let? (:remember-expanded settings) [expanded-threads-str] 
                   (io/get-data 'board 'expanded (:prefix target))
          (let [forgotten-threads (set (seq (.parse js/JSON forgotten-threads-str)))
                threads-on-watch (when (and (seq watch-thread-strs) (not (:filter target)))
                                   (into {} (map (fn [w] [(:internal-id w) w])
                                                 (map reader/read-string watch-thread-strs))))
                seen-threads (when seen-threads-str
                               (reader/read-string seen-threads-str))
                expanded-threads (when expanded-threads-str
                                   (reader/read-string expanded-threads-str))]
            (do-filter threads target callback settings forgotten-threads threads-on-watch 
                       seen-threads expanded-threads))))))))