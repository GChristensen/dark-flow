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
  (:use-macros [kuroi.macros :only [cb-let]]))

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

(defn- sort-by-id
  ([threads]
     (sort-by #(let [n (js/parseInt (:id %))]
                 (if (js/isNaN n) 0 n))
               >
               threads))
  ([threads target]
     (if (:sortid target)
       (sort-by #(let [n (js/parseInt (:id %))]
                   (if (js/isNaN n) 0 n))
                (if (:rev target) < >) 
                threads)
       threads)))

(defn- do-filter [threads target callback forgotten-threads threads-on-watch]
  (let [settings (settings/get-for target)
        pin-watched? (:pin-watch-items settings)
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

        (if (and pin-watched? threads-on-watch)
          (let [shown (sort-by-id shown target)
                out-of-scope (map (fn [kv] (let [w (second kv)]
                                             (assoc w
                                               :onwatch true
                                               :last-id (:last-id w))))
                                  (filter (fn [[k v]] (not (contains? watch k))) 
                                          threads-on-watch))
                threads-on-watch (sort-by-id (concat (map (fn [kv] (second kv))
                                                          watch) 
                                                     out-of-scope))
                all-threads (concat threads-on-watch shown)]
            (callback 
             (if wf-enabled? 
               (map #(filter-replies wf-post % target) all-threads) 
               all-threads)
             {:shown (count shown) 
              :filtered filtered 
              :forgotten forgotten 
              :watch (count watch)
              :watch-total (count threads-on-watch)}))
          (let [shown (sort-by-id shown target)]
            (callback
             (if wf-enabled? (map #(filter-replies wf-post % target) shown) shown)
             {:shown (count shown) 
              :filtered filtered 
              :forgotten forgotten 
              :watch 0 
              :watch-total 0}))))))))

(defn filter-threads [threads target callback]
  (cb-let [forgotten-threads-str] (io/get-data 'forgotten 'queue (:prefix target))
    (cb-let [threads-strs] (io/get-data* 'watch 'oppost {:where "board" :eq (:prefix target)})
      (let [forgotten-threads (set (seq (JSON/parse forgotten-threads-str)))
            threads-on-watch (when (and (seq threads-strs) (not (:filter target)))
                               (into {} (map (fn [w] [(:internal-id w) w])
                                             (map reader/read-string threads-strs))))]
        (do-filter threads target callback forgotten-threads threads-on-watch)))))