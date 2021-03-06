;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.backend
  (:require
   [kuroi.io :as io]
   [kuroi.filters :as filt]
   [kuroi.settings :as opts]
   [kuroi.render :as render]
   [kuroi.page-parser :as pp]

   [clojure.string :as str]
   [cljs.reader :as reader]
   )
  (:use-macros [kuroi.macros :only [cb-let s-in?]]))

;; Since the original application was client-server, this one
;; is also separated to the frontend and backend. Backend tries
;; to use as little javascript-specific code as possible, while
;; frontend tires to utilize no direct io code.
;; This adds a potential ability to proxy/stub the frontend and
;; backend to leave heavy processing on the server, although
;; this separation may also be performed at the level of the 
;; io.cljs module

(def ^:const *forget-queue-size* 100)

(def the-list-is-empty "<div class=\"load-indicator\">The list is empty</div>")

(defn distinct-by [f coll]
  (loop [xs coll out [] seen #{}]
    (let [x (first xs)
          p (f x)]
      (if x
        (recur (rest xs) (if (seen p) out (conj out x)) (conj seen p))
        out))))

;; url parsing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-target [url]
  (let [settings (opts/get-for nil)
        addr (re-find #"(.*://)?(([^/]+)/([a-zA-Z0-9-]+))(.*)" url)
        scheme (if (get addr 1) (get addr 1) "chan://")
        protocol (.substr scheme 0 (- (.-length scheme) 3))
        domain (get addr 3)
        board (get addr 4)
        trade (if addr (re-find #"[^.]+\.[^.]+$" domain) url)
        forum (str trade "/" board)
        defaults (:default-params-map settings)
        url (if-let [forum-defaults (and defaults (defaults forum))]
              (str url (if (vector? forum-defaults) (first forum-defaults) forum-defaults))
              url)
        pages (get (re-find #":(\d+)p" url) 1)
        refresh (get (re-find #":(\d+)r" url) 1)
        replies (get (re-find #":(\d+)v" url) 1)
        term (get (re-find #":search\{([^}]+)\}" url) 1)
        hdlns (s-in? url ":hdlns")]
        (when (and domain board)
          {:url url
           :rewrite (when (and defaults (defaults forum) (vector? (defaults forum))) (second (defaults forum)))
           :params (get addr 5)
           :trade trade
           :board board
           :forum forum
           :domain domain
           :scheme scheme
           :protocol protocol
           :incomplete (some #{protocol} ["bb"])
           :hdlns hdlns ; take only opposts
           :fourchan (= trade "4chan.org")
           :kraut (= trade "krautchan.net")
           :prefix (str trade "-" board "-")
           :target (str "http://" (get addr 2))
           :pages (if pages (js/parseInt pages) 5) ; initial amount of pages
           :refresh (if refresh (js/parseInt refresh) 2) ; amount of pages scrapped on refresh
           :replies (if hdlns 0 (if replies (js/parseInt replies) 3)); amount of replies to take from a thread
           :txt (and (s-in? url ":txt") (not (s-in? url ":!txt"))) ; text only headlines
           :img (and (s-in? url ":img") (not (s-in? url ":!img"))) ; image stream
           :sortid (and (s-in? url ":sortid") (not (s-in? url ":!sortid"))) ; sort threads by id
           :rev (and (s-in? url ":rev") (not (s-in? url ":!rev"))) ; the oldest threads first
           :new (and (s-in? url ":new") (not (s-in? url ":!new"))) ; show only threads with new replies 
           :filter (when term (.toLowerCase term)) ; search text
           :deep (s-in? url ":deep") ; deep search
           :chain (s-in? url ":chain") ; chained query
           :first (s-in? url ":first") ; first query in chain
           :prox (s-in? url ":prox")
           :bypass (s-in? url ":bypass")
           :peek (s-in? url ":peek")
           })))  

;; data retrieval ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; search for the new threads boundary to add the red line
(defn mark-new-threads [last-id threads]  
  (let [old-id (js/parseInt last-id)
        first-id (js/parseInt (:id (first (drop-while :onwatch threads))))
        proceed (atom false) 
        threads (if (>= old-id first-id)
                  threads
                  (loop [ths threads out []]
                    (if-let [th (first ths)]
                      (if @proceed
                        (recur (next ths) (conj out th))
                        (let [id (js/parseInt (:id (first (next ths))))]
                          (if (and (not (:onwatch th)) (> old-id id))
                            (do 
                              (reset! proceed true)
                              (recur (next ths) 
                                     (conj out (assoc th :boundary true))))
                            (recur (next ths) (conj out th)))))
                      out)))]
    (loop [ths threads out []]
      (if-let [th (first ths)]
        (if (:boundary (first (next ths)))
          (recur (next ths) 
                 (conj out (assoc th :pre-boundary true)))
          (recur (next ths) (conj out th)))
        out))))

(defn load-threads [request callback]
  (let [{:keys [target key end]} request]
    (cb-let [response] (io/get-pages (pp/target-pages target key))
      (if (= (:state response) "ok") 
        (let [pages (:pages response)
              meta-page? (pp/meta-page-url target)
              meta-page (when meta-page? (first pages))
              pages (if meta-page? (rest pages) pages)
              threads (pp/parse-page (first pages) target 
                                     :multiple true
                                     :get-metadata (and (not meta-page?) (not (:subsequent target))))
              thread-meta (if meta-page?
                            (pp/get-page-meta meta-page target)
                            (meta threads))
              threads (concat threads
                              (reduce concat (map #(pp/parse-page % target :multiple true) 
                                                  (rest pages))))
              threads (distinct-by :internal-id threads)
              threads (if (:rev target) (reverse threads) threads)
              return #(callback (render/threads %1 target end) %2 thread-meta)
              return-fail #(callback nil nil %)]
          (if (and (:filter target) (not (seq threads)))
            ;; if the :search url option is specified and nothing was found
            (return-fail (merge thread-meta {:error "" 
                                             :alert (str "'" (:filter target) "' not found.")}))
            (if (:img target)
              (if (seq threads)
                (return threads {:images true :shown (count threads)})
                (return-fail (merge thread-meta {:error the-list-is-empty})))
              (cb-let [settings] (opts/load-settings)
                (let [target (if (or (:txt target) (:force-text settings)) 
                               (assoc target :force-text true) 
                               target)]
                  (cb-let [threads stats] (filt/filter-threads threads target)
                    (if (seq threads)
                      (let [return #(callback (render/threads % target) stats thread-meta)]
                        (if (:sortid target)
                          (cb-let [last-id] (io/get-data 'board 'last_id (:prefix target))
                            (io/put-data 'board (:prefix target) 
                                         {:last_id (:id (first (drop-while :onwatch threads)))})
                            (if (and last-id (not (:filter target)))
                              (return (mark-new-threads last-id threads))
                              (return threads)))
                          (return threads)))
                      (return-fail (merge thread-meta {:error the-list-is-empty})))))))))
        (callback nil nil nil)))))

(defn get-thread-posts [request callback]
  (let [{:keys [thread-id post-count target]} request
          target (if (string? target)
                     (make-target target)
                     target)
          thread-url (pp/thread-url thread-id target)]
    (cb-let [response] (io/get-page thread-url)
      (if (= (:state response) "ok")
        (cb-let [settings] (opts/load-settings)
          (let [target (if (or (:txt target) (:force-text settings)) 
                         (assoc target :force-text true) 
                         target)
                thread (pp/parse-page (:page response)
                                      (assoc target :replies post-count))]
            (callback (render/replies (first thread) target))))
        (callback nil)))))

(def thread-cache (atom {}))
(defn get-popup-post [request callback]
    (let [{:keys [thread-id url word-filter onwatch]} request
          target (make-target url)
          url (str/split url #"#")
          thread-url (if thread-id 
                       (pp/thread-url thread-id target)
                       (first url))
          reply (when (seq (second url)) (re-find #"\d+" (second url)))
          settings (opts/get-for nil) ; sic!
          target (if (or (:txt target) (:force-text settings))
                   (assoc target :force-text true) 
                   target)
          get-thread (fn [callback]
                       (if-let [thread (@thread-cache thread-url)]
                         (callback (if reply
                                     (when (or (= (:id thread) reply) 
                                               (some #(= (:id %) reply) 
                                                     (:children thread)))
                                       thread)
                                     thread))
                         (cb-let [response] (io/get-page thread-url)
                           (let [thread (first (pp/parse-page (:page response)
                                                              (assoc target :replies -1)))]
                             (swap! thread-cache assoc thread-url thread)
                             (callback thread)))))]
      (cb-let [thread] (get-thread)
        (let [thread (if reply
                       (when-let [child (some #(when (= (:id %) reply) %) (:children thread))]
                         (assoc thread :children [child]))
                       (when thread
                         (assoc thread :refs nil :children nil)))]
          (if thread 
            (callback (if reply 
                        (render/replies thread target :word-filter word-filter :fallback? true)
                        (render/threads [(assoc thread :onwatch onwatch)] target)))
            (callback nil))))))

(defn forget-thread [request]
  (let [thread-id request
        board (.substring thread-id 0 (inc (.lastIndexOf thread-id "-")))]
    (cb-let [queue-json] (io/get-data 'forgotten 'queue board)
      (let [queue (if queue-json
                      (.parse js/JSON queue-json)
                      (new js/Array))]
          (.push queue thread-id)
          (when (> (.-length queue) *forget-queue-size*)
            (.shift queue))
          (io/put-data 'forgotten board {:queue (.stringify js/JSON queue)})))))

(defn watch-thread [request callback]
  (let [{:keys [thread-id target]} request
          target (if (string? target)
                     (make-target target)
                     target)
          thread-url (pp/thread-url thread-id target)]
  (cb-let [response] (io/get-page thread-url)
    (if (= (:state response) "ok")
      (let [oppost (first (pp/parse-page (:page response) (assoc target :replies 0)))]
        (if oppost
          (let [oppost (assoc oppost 
                         :board (:forum target)
                         :fourchan (:fourchan target)
                         :page-index nil)]
            (io/put-data 'watch (:internal-id oppost) {:board (:prefix target) 
                                                       :oppost (pr-str oppost)})
            (callback true))
          (callback nil)))
      (callback nil)))))

(defn unwatch-thread [request callback]
  (let [thread request]
    (io/del-data 'watch thread))
  (callback true))

(defn lazy-get-watch [request callback]
  (let [{:keys [thread-id target]} request
        target (if (string? target)
                 (make-target target)
                 target)
        thread-url (pp/thread-url thread-id target)]
    (cb-let [response] (io/get-page thread-url)
      (if (= (:state response) "ok")        
        (if-let [thread (first (pp/parse-page (:page response) target))]
          (cb-let [stored-oppost-str] (io/get-data 'watch 'oppost (:internal-id thread))
            (if stored-oppost-str
              (let [watch-item (reader/read-string stored-oppost-str)
                    post-delta (- (:post-count thread) (:post-count watch-item))
                    replies-html (when (and (not (:hdlns target)) (> (:replies target) 0))
                                   (render/replies thread target))]
                (when (not= 0 post-delta)
                  (io/put-data 'watch (:internal-id watch-item)
                               {:oppost (pr-str 
                                         (assoc watch-item
                                           :post-count (:post-count thread)
                                           :last-id (:last-id thread)))}))
                (callback {:post-delta post-delta
                           :post-count (:post-count thread)
                           :replies replies-html}))
              (callback {:post-count (:post-count thread)
                         :replies (render/replies thread target)})
              ))
          (callback nil))
        (callback nil)))))

(defn load-watch-items [request callback]
  (cb-let [watch-item-strs] (io/get-data* 'watch 'oppost nil)
    (if (seq watch-item-strs)
      (cb-let [settings] (opts/load-settings)
        (let [target request
              target (if (or (:txt target) (:force-text settings)) 
                       (assoc target :force-text true) 
                       target)
              watch-items (sort-by :board < (map reader/read-string watch-item-strs))
              boards (distinct (map :board watch-items))
              watch-groups (partition-by :board watch-items)
              threads (flatten (interleave boards  watch-groups))]
          (callback (render/watch threads target))))
      (callback nil))))

(defn get-thread-images [request callback]
  (let [{:keys [target thread-id]} request
        thread-url (pp/thread-url thread-id target)]
    (cb-let [response] (io/get-page thread-url)
      (if (= (:state response) "ok")
        (let [images (pp/parse-page (:page response) target)
              images (if (:rev target) (reverse images) images)
              images (distinct-by :internal-id images)]
          (callback (render/threads images target)))
        (callback nil)))))

(defn post-form [request callback]
  (let [{:keys [target url form thread-id password]} request
         thread-url (if thread-id
                     (pp/thread-url thread-id target)
                     (pp/paginate 0 target))]
    (if password
      (io/put-data 'settings "password" {:content password}))
    (cb-let [response] (io/post-form url form thread-url)
      (if-let [error (pp/get-post-error response target)]
        (callback {:error error})
        (callback nil)))))

(def *captcha-cache* (atom {}))
(defn get-captcha [request callback]
  (let [{:keys [target parent thread-id force]} request
        thread-url (if thread-id
                     (pp/thread-url thread-id target)
                     (pp/paginate 0 target))]
    (let [kv (find @*captcha-cache* thread-url)]
      (if (and kv (not force))
        (callback (val kv))
        (cb-let [captcha] (pp/get-captcha thread-url parent target)
           (when captcha
             (swap! *captcha-cache* assoc thread-url captcha)
             (callback captcha)))))))

(defn forget-captcha [request]
  (let [{:keys [target thread-id]} request
        thread-url (if thread-id
                     (pp/thread-url thread-id target)
                     (pp/paginate 0 target))]
    (swap! *captcha-cache* dissoc thread-url)))

(defn get-password [callback]
  (cb-let [password] (io/get-data 'settings 'content "password")
    (if (not password)
      (let [password (.slice (.toString (.random js/Math) 36) (- 6))]
        (io/put-data 'settings "password" {:content password})
        (callback password))
      (callback password))))
  
(defn save-expanded [request]
  (js/setTimeout
   #(let [{:keys [target thread-id state]} request
          settings (opts/get-for target)]
      (when (:remember-expanded settings)
        (cb-let [expanded] (io/get-data 'board 'expanded (:prefix target))
                (let [expanded (when expanded (reader/read-string expanded))
                      to-save (condp = state
                                :expand (conj (or expanded #{}) thread-id)
                                :collapse (disj (or expanded #{}) thread-id)
                                #{})]
                  (io/put-data 'board (:prefix target) {:expanded (pr-str to-save)})))))
   500))

(defn get-frontend-html [_ callback]
  (callback (render/frontend)))

(defn get-settings-html [_ callback]
  (callback (render/settings)))

(defn get-inline-watch-stream-html [_ callback]
  (callback (render/inline-watch-stream)))

(defn get-inline-image-stream-html [_ callback]
  (callback (render/inline-image-stream)))

(defn get-manual-html [_ callback]
  (callback (render/manual)))

(defn get-urlbar-html [_ callback]
  (callback (render/urlbar)))

(defn get-video-html [_ callback]
  (callback (render/video)))
