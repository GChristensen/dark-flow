;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.io
  (:require [goog.math :as math]))

;; ideomatically abstracted communications with the addon ;;;;;;;;;;;;;;;;;;;;;;

(def *port*)
(def *addon*)
(def *file-base* nil)

(defn ^:export init [opts]
  (set! *port* (.-port opts))
  (set! *addon* (.-addon opts))
  (set! *file-base* (.-file-base opts)))

(defn gen-uuid []
  (str "uuid-" (.getTime (new js/Date)) "-" (math/randomInt 65535)))

(defn js-map
  [cljmap]
  (let [out (js-obj)]
    (doall (map #(aset out (name (first %)) (second %)) cljmap))
    out))

(defn clj-map
  [jsmap]
  (let [keys (array-seq (.keys js/Object jsmap))]
    (loop [keys keys out {}]
      (if-let [k (first keys)]
        (recur (rest keys) (assoc out (keyword k) (aget jsmap k)))
        out))))
    
(defn log [msg]
  (.log js/console msg))

(defn request [request data callback]
  (let [msg-id (gen-uuid)
        pack (js-obj "message" msg-id
                     "payload" data)]
    (.once *port* msg-id callback)
    (.emit *port* request pack)))

(defn get-pages [urls callback]
  (let [input (if (seq? urls) 
                (when (seq urls)
                  (apply array urls))
                urls)]
    (if input
      (request "get-pages" input
        (fn [result]
          (let [result (clj-map result)]
            (callback (assoc result :pages (map clj-map (array-seq (:pages result))))))))
      (callback nil))))

(defn get-page [url callback]
  (get-pages (array url) 
    (fn [response]
      (let [page (dissoc (first (:pages response)) :index)
            response (assoc response :page page)]
        (callback (dissoc response :pages))))))
                       
(defn post-form [url form referer callback]
  (request "post-form" (js-obj "url" url "form" form "referer" referer)
    (fn [response]
      (callback (clj-map response)))))

(defn put-data [table id values]
  (let [msg-id (gen-uuid)
        pack (js-obj "message" msg-id
                     "table" (name table)
                     "id" id)]
    (set! (.-values pack) (js-map values))
    (.emit *port* "put-data" pack)))

(defn get-data* [table field id callback]
  (let [msg-id (gen-uuid)
        pack (js-obj "message" msg-id
                     "table" (name table)
                     "field" (name field))]
    (set! (.-id pack)
          (when id
            (if (string? id)
              (js-map {:where "id" :eq id})
              (js-map id))))
    (.once *port* msg-id 
      (fn [rows]
        (if rows
          (callback (array-seq rows))
          (callback nil))))
    (.emit *port* "get-data" pack)))

(defn get-data [table field id callback]
  (get-data* table field id 
    (fn [result]
      (callback (first result)))))

(defn del-data [table id]
  (let [msg-id (gen-uuid)
        pack (js-obj "message" msg-id
                     "table" (name table)
                     "id" id)]
    (.emit *port* "del-data" pack)))

(defn wipe-data [table]
  (let [msg-id (gen-uuid)
        pack (js-obj "message" msg-id, 
                     "table" (name table))]
    (.emit *port* "wipe-data" pack)))

(defn notify-settings-changed []
  (.emit *port* "settings-changed" ""))
