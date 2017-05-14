;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.settings
   (:require
    [kuroi.io :as io]
    [kuroi.base :as base]

    [clojure.set :as sets]
    [clojure.string :as str]
    [cljs.reader :as reader]

    [goog.dom :as dom]
    [goog.Uri :as uri]
    [goog.array :as array]
    [goog.object :as goog-object]
    [goog.events :as events]
    [goog.ui.Tooltip :as tooltip]
    [goog.ui.Button :as button]
    [goog.ui.FlatButtonRenderer :as flat-button-rndr]
    )
   (:use-macros [kuroi.macros :only [cb-let with-page]]))

(def *default-settings*
  {:theme "dark"
   :remember-expanded true
   :pin-watch-items true
   :watch-autoload true})

(def *settings* (atom *default-settings*))

(defn enabled? [check]
  (when (.-checked check) true))

(defn sanitize [content]
  (str/escape content {\< "&lt;" \> "&gt;" \" "&quot;" \' "&#39;"}))

(defn remember-threads []
  (io/wipe-data 'forgotten))

(defn clear-watch []
  (io/wipe-data 'watch))

(defn clear-data []
  (io/wipe-data 'board)
  (io/wipe-data 'watch)
  (io/wipe-data 'forgotten))

(defn parse-wordfilter [words]
  (when words
    (doall
     (filter (complement nil?)
             (for [word (str/split (sanitize words) #"\n")]
               (let [word (str/trim word)]
                 (when (not (str/blank? word))
                   (if (= (get word 0) \#)
                     word
                     (str/lower-case word)))))))))

(defn parse-defs [defs]
  (when defs
    (into {}
     (filter (complement nil?)
             (for [chan (str/split defs #"\n")]
               (let [chan (str/trim chan)]
                 (when (not (str/blank? chan))
                   (let [colon (.indexOf chan ":")]
                     [(.substring chan 0 colon)
                      (.substring chan colon)]))))))))

(defn save-settings []
  (let [settings {:pin-watch-items (enabled? (dom/getElement "watch-first"))
                  :remember-expanded (enabled? (dom/getElement "remember-expanded"))
                  :wf-title (.-value (dom/getElement "wf-title-words"))
                  :wf-post (.-value (dom/getElement "wf-post-words"))
                  :wf-enabled (enabled? (dom/getElement "wf-enable"))
                  :theme (.-value (dom/getElement "theme"))
                  :force-text (enabled? (dom/getElement "force-textonly"))}
        wf-title-parsed (parse-wordfilter (.-value (dom/getElement "wf-title-words")))
        wf-post-parsed (parse-wordfilter (.-value (dom/getElement "wf-post-words")))
        settings (assoc settings
                   :wf-title-parsed wf-title-parsed
                   :wf-post-parsed wf-post-parsed
                   :default-params (.-value (dom/getElement "favorites"))
                   :default-params-map (parse-defs (.-value (dom/getElement "favorites"))))]
    (reset! *settings* settings)
    (io/put-data 'settings "theme" {:content (:theme settings)})
    (io/put-data 'settings "settings" {:content (pr-str settings)})
    (io/notify-settings-changed)))

(defn prepare-wordfilter [words]
  (for [w words]
    (if (= (get w 0) \#)
      (re-pattern (str "(?i)" (.substring w 1)))
      w)))

(defn ^:export load-settings [callback]
  (cb-let [settings-str] (io/get-data 'settings 'content "settings")
    (if settings-str
      (let [settings (reader/read-string settings-str)]
        (reset! *settings* 
                (assoc settings
                  :wf-title-parsed (prepare-wordfilter (:wf-title-parsed settings))
                  :wf-post-parsed (prepare-wordfilter (:wf-post-parsed settings))))
        (callback @*settings*))
      (callback @*settings*))))

(defn get-for [target]
  (if (and target (= (:trade target) "iichan.hk") (not (:bypass target)))
    (merge @*settings* ; inject dedicated wordfilter for iichan.hk
           {:wf-enabled true
            :wf-title-parsed
            (sets/union (:wf-title-parsed @*settings*)
                        (prepare-wordfilter (:wf-title-parsed base/*iichan-hk-wf*)))
            :wf-post-parsed
            (sets/union (:wf-post-parsed @*settings*)
                        (prepare-wordfilter (:wf-post-parsed base/*iichan-hk-wf*)))})
    @*settings*))
