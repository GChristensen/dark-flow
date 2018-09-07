;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.frontend
  (:require
   [kuroi.io :as io]
   [kuroi.base :as base]
   [kuroi.render :as rdr]
   [kuroi.backend :as bk]
   [kuroi.page-parser :as pp]
   [kuroi.settings :as opts]

   [clojure.string :as str]
   [clojure.browser.repl :as repl]
   [cljs.reader :as reader]

   [goog.dom :as dom]
   [goog.Uri :as uri]
   [goog.math :as math]
   [goog.fx.Dragger :as dragger]
   [goog.object :as goog-object]
   [goog.dom.query :as query]
   [goog.events :as events]
   [goog.style :as style]
   [goog.array :as array]
   [goog.dom.forms :as forms]
   [goog.net.cookies :as cookies]
   [goog.ui.Dialog :as dialog]
   [goog.Timer :as timer]
   [goog.ui.Tooltip :as tooltip]
   [goog.ui.Popup :as popup]
   [goog.ui.Button :as button]
   [goog.ui.FlatButtonRenderer :as flat-button-rndr]
   [goog.positioning :as pos]
   )
   (:import [goog.positioning AnchoredViewportPosition ClientPosition Corner])
  (:use-macros [kuroi.macros :only [cb-let with-page s-in? log]]))

(def *target*)
(def *resource*)

(def *nav-popup*)

(def *expand-counter* (atom 0))

(def ^:const forget-trigger "<a class=\"forget-trigger\" title=\"Forget thread\">&#x00d7;</a>")
(def ^:const empty-set "<div class=\"prohibit\">&#x20e0;</div>")
(def ^:const error-loading-page "<div class=\"prohibit load-indicator\">Error loading page</div>")
(def ^:const the-list-is-empty "<div class=\"load-indicator\">The list is empty</div>")


(def *theme*)
(def *addon*)
(def *file-base*)
(def loading-post)
(def service-loading)
(def obtaining-data)

(def *active-dialog*)

(def *dom-helper* (dom/DomHelper. js/document))

(defn dom-get-element [id]
  (dom/getElement id))

(defn dom-get-elements-by-class [name]
  (dom/getElementsByClass name))

(defn dom-find-node [root f]
  (dom/findNode root f))


(defn file-base [file]
  (str *file-base* file))

(defn themed-image [file-name]
  (file-base (str "themes/" *theme* "/images/" file-name)))

(defn setup-themed-vars [theme]
  (set! *theme* theme)
  (let [loading-bar (themed-image "loading-bar.gif")]
    (set! loading-post (str "<div class=\"service-pane\"><img src=\"" 
                            loading-bar
                            "\"/> Loading...</div>"))
    (set! service-loading (str "<img class=\"service-loading\" src=\"" 
                               loading-bar
                               "\"/>"))
    (set! obtaining-data (str "<div class=\"load-indicator\"><img src=\""
                              loading-bar
                              "\"/> Processing...</div>"))))

(defn  delta-posts [n]
  (str "<span class=\"delta-posts\" title=\"Delta posts from the last visit\">&#x2206; [" n "]</span>"))

;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn child-by-class [root class-name]
  (dom-find-node root (fn [node]
                       (when-let [node-classname (.-className node)]
                         (>= (.indexOf node-classname class-name) 0)))))

(defn sibling-by-class [node class-name]
  (loop [sibling (.-previousSibling node)]
    (when sibling
      (let [sibling-classname (.-className sibling)]
        (if (and sibling sibling-classname (>= (.indexOf sibling-classname class-name) 0))
          sibling
          (recur (.-previousSibling sibling)))))))

(defn parent-by-class [node class-name]
  (loop [parent (.-parentNode node)]
    (when parent
      (let [parent-classname (.-className parent)]
        (if (and parent parent-classname (>= (.indexOf parent-classname class-name) 0))
          parent
          (recur (.-parentNode parent)))))))

(defn parent-by-id [node id]
  (loop [parent (.-parentNode node)]
    (when parent
      (let [parent-id (.-id parent)]
        (if (and parent parent-id (>= (.indexOf parent-id id) 0))
          parent
          (recur (.-parentNode parent)))))))

(defn child-by-id [root id]
  (dom-find-node root #(= (.-id %) id))) 

(defn create-elt [tag attrs]
  (.createDom *dom-helper* tag (clj->js attrs)))

(defn hide-elt [e]
  (set! (.-display (.-style e)) "none"))

(defn show-elt [e display]
  (set! (.-display (.-style e)) display))

(defn show-output [content]
  (let [output (dom-get-element "output")]
    (set! (.-innerHTML output) content)))

(defn load-external [filename filetype]
  (letfn [(append [elt]
            (.appendChild (aget (.getElementsByTagName js/document "head") 0) elt))]
    (condp = filetype
      "css" (let [elt (.createElement js/document "link")]
              (set! (.-rel elt) "stylesheet")
              (set! (.-type elt) "text/css")
              (.setAttribute elt "async" "false")
              (set! (.-href elt) filename)
              (append elt)
           ;; TODO add callback parameter for load to move body style into macros.clj
           (.addEventListener elt "load" #(when
                                            (.endsWith filename "closure.css")
                                                (set! (.-display (.-style (.-body js/document))) "block")
                                            (let [address-txt (dom-get-element "address-txt")]
                                                 (when address-txt
                                                       (.focus address-txt))))))
      "ico" (let [elt (.createElement js/document "link")]
              (set! (.-rel elt) "icon")
              (set! (.-href elt) filename)
              (append elt))
      nil)))

(defn load-css [theme filename]
  (load-external (str *file-base* "themes/" theme "/css/" filename) "css"))
 
(defn load-styles [theme &{:keys [settings]}]
  (let [ua (str/lower-case (.-userAgent js/navigator))]
    (if (or (s-in? ua "android") (s-in? ua "mobile"))
      (load-css theme "mobile.css")))
  (if settings
    (load-css theme "settings.css")
    (load-css theme "frontend.css"))
  (load-css theme "main.css")
  (load-css theme "closure.css"))

(defn format-stats [stats target]  
  (let [shown (if (:img target) (:shown stats) (+ (:shown stats) (:watch stats)))
        additional (if (:img target) 0 (- (:watch-total stats) (:watch stats)))
        filtered (:filtered stats)
        forgotten (:forgotten stats)
        hidden (if (:img target) 0 (+ filtered forgotten))]
    (if (string? stats)
      stats
      (str (when (not (or (:img target) (not *addon*) (base/*posting-not-impl* (:trade target))))
             "<a class=\"new-thread\" title=\"New thread\" style=\"cursor: pointer !important;\">+</a>&nbsp;")
           "<a target=\"_blank\" class=\"board-link\" href=\"" (:target target) "\">"
           (:trade target) "/" (:board target) "</a>&nbsp;"
           (if (:img target) "images" "threads")
           " [displayed: " (if shown shown "0") (when (> additional 0) (str "+" additional))
           (when (> hidden 0)
             (str ", hidden: " hidden))
           "]"))))

(defn insert-threads [threads &{:keys [meta]}]

  (let [t-h (dom/getElement "thread-headlines")
        err-msg (if (:error meta)
                  (:error meta)
                  error-loading-page)]
    (if threads
      (do
        (let [indicators (.querySelectorAll threads ".load-indicator > img")]
          (doseq [i (seq indicators)]
            (set! (.-src i) (themed-image (.getAttribute i "src")))))
        (.removeChild t-h (.-firstChild t-h))
        (.appendChild t-h threads)
        (let [new-thread-elts (.querySelectorAll js/document ".new-thread")]
             (.forEach new-thread-elts #(set! (.-onclick %) (fn [] (show-reply-form % true)))))
        #_(let [loading-line (.querySelector t-h ".thread-line")]
          (.removeChild loading-line (.-patentNode loading-line))))
      (set! (.-innerHTML t-h) err-msg))
    (when (:alert meta)
      (js/alert (:alert meta)))))

(defn append-threads [threads &{:keys [hide-indicator]}]
  (when hide-indicator
    (when-let [loading-thread (.querySelector threads ".loading-thread")]
      (hide-elt loading-thread)))

  (let [t-h (dom-get-element "thread-headlines")]
    (.appendChild t-h threads)
    (let [new-thread-elts (.querySelectorAll js/document ".new-thread")]
      (.forEach new-thread-elts #(set! (.-onclick %) (fn [] (show-reply-form % true)))))))

(defn set-events [el events]
  (doseq [[k v] events]
    (.addEventListener el k v false)))

(defn del-events [el events]
  (doseq [[k v] events]
    (.removeEventListener el k v false)))

(defn set-attrs [el attrs]
  (doseq [[k v] attrs]
    (.setAttribute el k v)))

(defn get-target [thread-line]
  (if (or (not *target*) (:chain *target*))
                           ;; general case (button in thread stream)
    (let [thread-group (or (when-let [group-line (sibling-by-class thread-line "group-line")]
                             (child-by-class group-line "thread-group"))
                           (when-let [thread-stream (parent-by-class thread-line "thread-stream")]
                             (.querySelector thread-stream ".thread-group"))
                           ;; button in the stats line
                           thread-line)]
      (when thread-group
        (when-let [board-link (if (:chain *target*) 
                                ;; <a> element
                                (child-by-class thread-group "board-link")
                                ;; group line in the watch list
                                thread-group)]
          (bk/make-target (str (.-innerHTML board-link))))))
    *target*))

;; inline view ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn inline-dialog [title target]
  (let [dialog (goog.ui.Dialog.)
        close-elt (. dialog (getTitleCloseElement))]
    (set! *active-dialog* dialog)
    (if (vector? title)
      (let [title-elt (. dialog (getTitleTextElement))]
        (set! (.-innerHTML title-elt) (first title)))
      (.setTitle dialog title))
    (.setContent dialog (str "<iframe src=\"" target "\"/>")
    (.setButtonSet dialog nil)
    (set! (.-innerHTML close-elt) "Close")
    (set! (.-className close-elt) "modal-dialog-title-close goog-flat-button")
    (.setDisposeOnHide dialog true)
    (.setVisible dialog true))
    false))

(defn close-active-dialog []
      (when *active-dialog*
            (.dispose *active-dialog*)
            (set! *active-dialog* nil)))

(defn inline-view-link [link]
  (let [title (str "<a class=\"title-link\" target=\"_blank\" href=\"" link "\">" 
                   link "</a>")]    
    (inline-dialog [title] link)))

(defn ^:export inline-view-thread [a]
  (let [link-elt (or (child-by-class (.-parentNode a) "thread-no")
                     (child-by-class (.-parentNode a) "reply-no"))
        thread-link (.-href link-elt)]
    (inline-view-link thread-link)))

(defn ^:export inline-view-reflink [a]
  (inline-view-link (.-href a)))

(defn ^:export show-thread-images [a from-thread-stream?]
  (let [thread-no (child-by-class (.-parentNode a) "thread-no")
        thread-link (.-href thread-no)
        target (if from-thread-stream?
                 (let [thread-line (parent-by-class a "thread-line")]
                       (get-target thread-line))
                 *target*)
        target (assoc target :img true :inline true)
        iframe-link (str (get-base-url) "?images&target=" (js/encodeURI (pr-str target))
                         "&thread-id=" (.-textContent thread-no))
        title (str "<a class=\"title-link\" target=\"_blank\" href=\"" thread-link "\">" 
                   thread-link "</a>")]    
    (inline-dialog [title] iframe-link)))

(defn ^:export show-thread-video [video-pic]
  (let [video-id (second (re-find #".*ytimg.com/[^/]+/([^/]+)/" video-pic ))
        url (when video-id (str "http://youtube.com/embed/" video-id "?autoplay=1"))]
    (when url
      (inline-dialog "Video" (str (get-base-url) "?video?video=" (js/encodeURIComponent url))))))

;; post image expansion ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-moveable [el]
  (letfn [(move-elt [e]
            (set! (.-left (.-style el)) (str (- (.-clientX e) (.-curX el)) "px"))
            (set! (.-top (.-style el)) (str (- (.-clientY e) (.-curY el)) "px"))
            (set! (.-moved el) true))

          (stop-elt []
            (del-events (.-body js/document) 
                        {"mousemove" move-elt "mouseup" stop-elt}))]
    (set-events el
                {"mousedown"
                 (fn [e]
                   (let [left (js/parseInt (.-left (.-style el)))
                         top (js/parseInt (.-top (.-style el)))
                         ;client-top (.-clientTop el)
                         ;width (.-clientWidth el)
                         ;height (.-clientHeight el)
                         client-x (.-clientX e)
                         client-y (.-clientY e)]
                     (.preventDefault e)
                     (set! (.-curX el) (- client-x left))
                     (set! (.-curY el) (- client-y top))
                     (set-events (.-body js/document)
                                 {"mousemove" move-elt "mouseup" stop-elt})))})))

(defn resize-image [e]
  (.preventDefault e)
  (this-as self
    (let [cur-x (.-clientX e)
          cur-y (.-clientY e)
          old-l (js/parseInt (.-left (.-style self)))
          old-t (js/parseInt (.-top (.-style self)))
          old-w (.-width self)
          old-h (.-height self)
          d (- (.-detail e)) ; Chrome: (.-wheelDelta e)
          new-w (js/parseInt (* (.-width self) (if (> d 0) 1.25 0.8)))
          new-h (js/parseInt (* (.-height self) (if (> d 0) 1.25 0.8)))]
      (set! (.-width self) new-w)
      (set! (.-height self) new-h)
      (set! (.-left (.-style self)) 
            (str (- cur-x (* (/ new-w old-w) (- cur-x old-l))) "px"))
      (set! (.-top (.-style self)) 
            (str (- cur-y (* (/ new-h old-h) (- cur-y old-t))) "px")))))


(defn ^:export expand-image [a full-w full-h]
  (if (or (str/blank? (.-href a)) (= "#" (.-href a)))
    (let [img (.querySelector a "img")
          src (when img (.-src img))]
      (when src 
        (show-thread-video src)))
    (let [full (.-singleNodeValue (.evaluate js/document ".//*[@id=\"_fullimg\"]" a nil 8 nil))
          video? (.endsWith (.-href a) ".webm")]
      (if full ; shown
        (if (not (.-moved full))
          (do
            (set! (.-display (.-style full))
                  (if (= (.-display (.-style full)) "none") "" "none"))
            (js/setTimeout #(.removeChild (.-parentNode full) full) 0))
          (set! (.-moved full) false))
         ; none
        (let [full (.createElement js/document (if video? "video" "img"))
              existing (dom-get-element "_fullimg")
              realign? (and (= full-w 0) (= full-h 0))
              scr-w (.-clientWidth (.-body js/document))
              scr-h (.-innerHeight js/window)
              configure-img (fn [full full-w full-h]
                                (let[
                                  [new-w new-h] (if (or (> full-w scr-w) (> full-h scr-h))
                                                  (let [new-w (/ (* full-w scr-h) full-h)
                                                        new-h (/ (* full-h scr-w) full-w)]
                                                    [(if (> new-w scr-w) scr-w new-w) (if (<= new-w scr-w) scr-h new-h)])
                                                  [full-w full-h])]
                              (when existing
                                (.removeChild (.-parentNode existing) existing))
                              (when video?
                                (set! (.-controls full) true)
                                (set! (.-autoplay full) true))
                              (.addEventListener full "DOMMouseScroll" resize-image) ; Chrome: 'mousewheel'
                              (make-moveable full)
                              (set-attrs full
                                         {"id"     "_fullimg"
                                          "src"    (.-href a)
                                          "title"  (.-href a)
                                          "alt"    (.-href a)
                                          "width"  new-w
                                          "height" new-h
                                          "style"  (str "display: block;"
                                                        "position: fixed;"
                                                        "z-index: 5000;"
                                                        " border: 1px solid black;"
                                                        "left:" (/ (- scr-w new-w) 2) "px;"
                                                        "top:" (/ (- scr-h new-h) 2) "px")
                                          })))]
              (if realign?
                (do
                  (set! (.-onload full)
                         #(let [rect (.getBoundingClientRect full)]
                             (log (.-width rect))
                          (configure-img full (.-width rect) (.-height rect))
                             (set! (.-onload full) nil)))
                  (set! (.-src full) (.-href a))
                  (.appendChild a full))
                (do
                  (configure-img full full-w full-h)
                  (.appendChild a full)
                  (set! (.-display (.-style full)) "block")))))))
    false)

;; popups ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def popups {})

(defn inside? [box point]
  (and 
   (> (.-x point) (.-left box))
   (< (.-x point) (+ (.-left box) (.-width box)))
   (> (.-y point) (.-top box))
   (< (.-y point) (+ (.-top box) (.-height box)))))

(defn hide-popup [e post]
  (let [popup (popups post)
        popup-elt (dom-get-element (str "popup-" post))
        out (dom-get-element "output")]
    (when (and popup popup-elt)
      (let [box (style/getBounds popup-elt)
            point (goog.math.Coordinate. (.-clientX e) (.-clientY e))]
        (when (not (inside? box point))
          (.setVisible popup false))))))

(defn dismiss-popup [popup]
  (let [post (.-post_ popup)
        popup-elt (dom-get-element (str "popup-" post))]
    (when popup-elt
      (events/removeAll popup-elt goog.events.EventType/MOUSEOUT)
      (.removeChild (.-body js/document) popup-elt))
    (set! popups (dissoc popups post))))

(defn quadrant-dispatch [x y lt rt lb rb]
  (let [viewport (style/getBounds (style/getClientViewportElement))
        h-sec (/ (.-width viewport) 2)
        v-sec (/ (.-height viewport) 2)]
    (cond
     (and (< x h-sec) (< y v-sec)) lt
     (and (> x h-sec) (< y v-sec)) rt
     (and (< x h-sec) (> y v-sec)) lb
     (and (> x h-sec) (> y v-sec)) rb)))

(def failed-node nil)
(def loading-popup? nil)
(defn ^:export show-popup [e post show?]
  (let [get-thread-id (fn [element]
                        (let [post-container (parent-by-class element "post-container")]
                          (when post-container  (.getAttribute post-container "data-thread-id"))))
        make-popup (fn [node]
                     (let  [post (if node post "loading")
                            popup-elt
                            (create-elt "div"
                                        {"class" "popup",
                                         "id" (str "popup-" post),
                                         "innerHTML" (if node "<div/>" loading-post)})]
                       (.appendChild (.-body js/document) popup-elt)
                       (let [links (.querySelectorAll popup-elt "a[data-onmouseover")]

                            (.forEach links #(do
                                               (set! (.-onmouseover %) (fn [event] (show-popup event, post, true)))
                                                 (set! (.-onmouseout %) (fn [event] (show-popup event, post, false))))))
                       (when node
                         (let [reply-elt (.cloneNode node true)
                               oppost-text (child-by-class reply-elt "oppost-text")]
                              (rdr/replace-events reply-elt)
                           (when oppost-text
                             (set! (.-maxHeight (.-style oppost-text)) "none"))
                           (.removeChild popup-elt (.-firstChild popup-elt))
                           (.appendChild popup-elt reply-elt)))
                       (let [popup (goog.ui.Popup. popup-elt)
                             corner (quadrant-dispatch (.-clientX e) (.-clientY e)
                                                       Corner/TOP_LEFT
                                                       Corner/TOP_RIGHT
                                                       Corner/BOTTOM_LEFT
                                                       Corner/BOTTOM_RIGHT)
                             dd (quadrant-dispatch (.-clientX e) (.-clientY e)
                                                   [-5 -5] [5 -5] [-5 5] [5 5])]
                         (set! popups (assoc popups post popup))
                         (set! (.-post_ popup) post)
                         (.setPinnedCorner popup corner)
                         (.setPosition popup (ClientPosition.
                                              (+ (.-clientX e) (first dd))
                                              (+ (.-clientY e) (second dd))))
                         (events/listen popup
                                        goog.ui.Component.EventType/HIDE
                                        #(dismiss-popup (.-currentTarget %)))
                         (events/listen popup-elt
                                        goog.events.EventType/MOUSEOUT
                                        #(hide-popup % post))
                         (.setVisible popup true)
                         popup)))
        do-show (fn [node]
                  (let [popup (popups post)]
                    (when popup
                      (dismiss-popup popup))
                    (let [popup (make-popup node)]
                      (when (not node)
                        (set! loading-popup? true)
                        (cb-let [reply] (bk/get-popup-post {:thread-id (get-thread-id (.-target e))
                                                            :url (.-href (.-target e))})
                           (if reply
                             (do
                               (dismiss-popup popup)
                               (make-popup (child-by-class (.-firstChild reply)
                                                           "post-container")))
                             (do
                               (set! failed-node post)
                               (dismiss-popup popup)))
                           (set! loading-popup? nil))))))]
    (when (not loading-popup?)
      (if (and (not= failed-node post) show?)
        (let [nodes (dom-get-elements-by-class "reply-no" (dom-get-element "thread-stream"))
              node (loop [n (dec (.-length nodes))]
                     (when (>= n 0)
                       (let [node (aget nodes n)]
                         (if (= (.-textContent node) post)
                           node
                           (recur (dec n))))))]
          (do-show (when node (parent-by-class node "post-container"))))
        (do
          (set! failed-node nil)
          (hide-popup e post))))))


(def ^:const tiny-urlbar-popup-elt (create-elt "div"
                                               {"class" "gold popup zpopup",
                                                "id" "tiny-urlbar-popup",
                                                "innerHTML" "Go: <input id=\"address-txt\" class=\"gold\" type=\"text\" size=\"50\"
                                                 \"style=\"width: 500px;\" autofocus/>"}))
(def tiny-urlbar-popup (goog.ui.Popup. tiny-urlbar-popup-elt))

(defn setup-tiny-urlbar-popup []
      (.appendChild (.-body js/document) tiny-urlbar-popup-elt)
      (.setPinnedCorner tiny-urlbar-popup Corner/TOP_LEFT)
      (let [address-txt (dom-get-element "address-txt")]
        (set! (.-value address-txt) *resource*)
        (events/listen address-txt
                       goog.events.EventType/KEYPRESS
                       (fn [e]
                           (cond (= (.-keyCode e) 13) ; enter
                                 (do (.emit io/*port* "load-threads" (js-obj "url" (.-value address-txt)))
                                     (.setVisible tiny-urlbar-popup false))
                                 (= (.-keyCode e) 27) ; esc
                                 (.setVisible tiny-urlbar-popup false))))
        (events/listen js/document
                       goog.events.EventType/KEYDOWN
                       (fn [e]
                           (cond (and (= (.-keyCode e) 81) (.-ctrlKey e)) ; ctrl+q
                                 (.click (dom-get-element "follow-btn")))))))


;; thread control handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export lazy-get-watch [element watch-stream?]
  (let [thread-line (parent-by-class element "thread-line")
        oppost (child-by-class thread-line "thread-oppost")
        thread-control (child-by-class thread-line "thread-control")
        incomplete? (>= (.indexOf (.-className thread-control) "incomplete") 0)
        target (get-target thread-line)]
    (if target
      (let [target (if watch-stream? (assoc target :replies 0) target)
            service-pane (child-by-class thread-line "service-pane")
            thread-no (child-by-class thread-line "thread-no")
            thread-id (.-textContent thread-no)]
        (set! (.-innerHTML service-pane) service-loading)
        (cb-let [response] (bk/lazy-get-watch {:thread-id thread-id :target target})
          (if response
            (let [post-count (child-by-class thread-line "post-count")
                  existing-replies (child-by-class oppost "replies")
                  replies (:replies response)]
                  (if incomplete?
                   (let [observer (js/MutationObserver. (fn [mutations]
                                                            (doseq [m mutations]
                                                                   (when (= (.-type m)"childList")
                                                                         (doseq [n (.-addedNodes m)]
                                                                                (when (and (= (.-className n) "reply")
                                                                                           (= (.-textContent (child-by-class n "reply-ord")) "1"))
                                                                                      (.disconnect (.-observer oppost))
                                                                                      (set! (.-observer oppost) nil)
                                                                                      (let [oppost-text (child-by-class oppost "oppost-text")
                                                                                            lazy-image-link (child-by-class n "image-link")
                                                                                            lazy-post-text (child-by-class n "post-text")]
                                                                                           ;(rdr/replace-events (.-parentNode lazy-image-link))
                                                                                           (.removeChild (.-parentNode lazy-image-link) lazy-image-link)
                                                                                           (.appendChild (child-by-class oppost "image-container") lazy-image-link)
                                                                                           (.removeChild (.-parentNode lazy-post-text) lazy-post-text)
                                                                                           (set! (.-className lazy-post-text) (str (.-className lazy-post-text) " oppost-text"))
                                                                                           (.insertBefore (child-by-class oppost "post-container") lazy-post-text oppost-text)
                                                                                           (.removeChild (.-parentNode oppost-text) oppost-text)
                                                                                           (.removeChild (.-parentNode n) n)
                                                                                               )))))))
                         ]
                        (set! (.-observer oppost) observer)
                        (.observe observer
                                  oppost
                                  (js-obj "childList" true "subtree" true))
                     ))
                   (do
                     (if incomplete?
                        (set! (.-innerHTML service-pane) forget-trigger)
                        (set! (.-innerHTML service-pane) (delta-posts (:post-delta response))))
                      (set! (.-innerHTML post-count) (str "[" (:post-count response) "]"))
                      (when replies
                        (let [expand-trigger (child-by-class thread-line "expand-trigger")
                              replies (.-firstChild replies)
                              collapse-bar (child-by-class replies "collapse-bar")]
                          (.removeChild (.-parentNode collapse-bar) collapse-bar)
                          (.removeChild oppost existing-replies)
                          (.appendChild oppost replies)
                          (if (.-expanded_ expand-trigger)
                            (set! (.-display (.-style replies)) "block"))))));)
                  (set! (.-innerHTML service-pane) empty-set))))
      ;; if the dom isn't constructed yet
      (js/setTimeout #(lazy-get-watch element watch-stream?) 2000))))

(defn ^:export expand-thread [post-id expand? &{:keys [mass]}]
  (let [
        post (dom-get-element post-id)
        thread-headlines (dom-get-element "thread-headlines")
        thread-line (parent-by-class post "thread-line")
        target (get-target thread-line)
        thread-no (child-by-class thread-line "thread-no")
        thread-id (.-textContent thread-no)
        expand-btn (when (not mass) (dom-get-element "expand-btn"))
        expand-trigger (child-by-class (.-parentNode post) "expand-trigger")
        post-text (child-by-class post "oppost-text")
        replies (child-by-class post "replies")
        nav-popup (dom-get-element "nav-popup")
        expanded_? (= "true" (.getAttribute expand-trigger "expanded_"))]
    (hide-elt nav-popup)
    (cond (or (false? expand?) (and (= expand? js/undefined) expanded_?))
          (do
            (set! (.-maxHeight (.-style post-text)) "65px")
            (set! (.-display (.-style replies)) "none")
            (set! (.-innerHTML expand-trigger) "&raquo;")
            (.setAttribute expand-trigger "expanded_" "false")
            (when (not mass)
              (bk/save-expanded {:target target :thread-id thread-id :state :collapse})
              (swap! *expand-counter* dec)
              (when (= 0 @*expand-counter*)
                (set! (.-textContent expand-btn) "Expand"))
              (let [vp-offset (- (.-y (style/getClientPosition post))
                                 (.-y (style/getClientPosition thread-headlines)))]
                (when (< vp-offset 0)
                  (. post (scrollIntoView))))))
          (or (true? expand?) (and (= expand? js/undefined) (not expanded_?)))
          (let [service-pane (child-by-class (.-parentNode post) "service-pane")]
            (when (not mass)
              (bk/save-expanded {:target target :thread-id thread-id :state :expand})
              (swap! *expand-counter* inc)
              (set! (.-textContent expand-btn) "Collapse"))
            (when (> (.indexOf (.-innerHTML service-pane) "[?]") 0)
              (lazy-get-watch post false))
            (set! (.-maxHeight (.-style post-text)) "none")
            (set! (.-display (.-style replies)) "block")
            (set! (.-innerHTML expand-trigger) "&laquo;")
            (.setAttribute expand-trigger "expanded_" "true")))
    (show-elt nav-popup "block"))
  false)

(defn ^:export watch-thread [element]
  (let [thread-line (parent-by-class element "thread-line")
        thread-oppost (child-by-class thread-line "thread-oppost")
        target (get-target thread-line)
        service-pane (child-by-class thread-line "service-pane")
        thread-no (child-by-class thread-line "thread-no")
        thread-id (.-textContent thread-no)]
    (set! (.-innerHTML service-pane) service-loading)
    (if (or (= (.getAttribute element "onwatch") "onwatch") (.-onwatch element))
      (cb-let [response] (bk/unwatch-thread (.-id thread-oppost))
        (if response
          (do
            (set! (.-innerHTML element) "&#x2606;")
            (set! (.-className element) "watch-trigger-disabled")
            (set! (.-title element) "Watch thread")
            (if (not *target*)
              (set! (.-innerHTML service-pane) "")
              (do (set! (.-innerHTML service-pane) forget-trigger)
                  (let [trigger (child-by-class service-pane "forget-trigger")]
                     (set! (.-onclick trigger) #(forget-thread trigger)))))
            (.removeAttribute element "onwatch")
            (set! (.-onwatch element) nil))
          (set! (.-innerHTML service-pane) empty-set)))
      (cb-let [response] (bk/watch-thread {:thread-id thread-id :target target})
        (if response
          (do                         ;"&#x2329;<span class=\"italic\">&#x03c5;</span>&#x232a;"
            (set! (.-innerHTML element) "&#x2605;")
            (set! (.-className element) "watch-trigger-enabled")
            (set! (.-title element) "Unwatch thread")
            (set! (.-innerHTML service-pane) (delta-posts 0))
            (set! (.-onwatch element) true))
          (set! (.-innerHTML service-pane) empty-set))))))

(defn ^:export forget-thread [target]
  (let [thread-line (parent-by-class target "thread-line")
        op-post (child-by-class thread-line "thread-oppost")]
    (bk/forget-thread (.-id op-post))
    (set! (.-backgroundColor (.-style thread-line)) "#757575")
    (timer/callOnce #(.removeChild (.-parentNode thread-line) thread-line) 200)))

;; integrated view
(defn ^:export iv-load-posts [element posts]
  (let [thread-line (parent-by-class element "thread-line")
        posts (if (= posts -2) ; delta
                (let [delta-posts (child-by-class thread-line "delta-posts")]
                  (js/parseInt (re-find #"\d+" (.-textContent delta-posts))))
                posts)
        target (get-target thread-line)
        service-pane (child-by-class thread-line "service-pane")
        service-html (.-innerHTML service-pane)
        thread-no (child-by-class thread-line "thread-no")
        thread-id (.-textContent thread-no)]
    (set! (.-innerHTML service-pane) service-loading)
    (cb-let [posts] (bk/get-thread-posts {:thread-id thread-id :post-count posts :target target})
      (if posts
        (let [posts (.-firstChild posts)
              oppost (child-by-class thread-line "thread-oppost")
              existing-replies (child-by-class oppost "replies")
              orig-collapse-bar (child-by-class oppost "collapse-bar")
              collapse-bar (child-by-class posts "collapse-bar")]
           (.removeChild (.-parentNode orig-collapse-bar) orig-collapse-bar)
           (.removeChild (.-parentNode collapse-bar) collapse-bar)
          (when-let [indicator (.querySelector posts ".load-indicator > img")]
            (set! (.-src indicator) (themed-image (.getAttribute indicator "src"))))
          (.removeChild oppost existing-replies)
          (.appendChild oppost posts)
          (set! (.-innerHTML service-pane) service-html)
             (expand-thread (.-id oppost) true))
        (set! (.-innerHTML service-pane) empty-set)))))

;; expansion of the abbreviated posts
(defn ^:export iv-expand-post [element link]
  (let [thread-line (parent-by-class element "thread-line")
        service-pane (child-by-class thread-line "service-pane")
        service-html (.-innerHTML service-pane)
        post-container (parent-by-class element "post-container")
        reply-elt (or (parent-by-class element "reply")
                      (parent-by-class element "thread-oppost"))
        onwatch? (s-in? (.-className reply-elt) "onwatch")]
    (set! (.-innerHTML service-pane) service-loading)
    (cb-let [reply] (bk/get-popup-post {:url link :word-filter false :onwatch onwatch?})
      (set! (.-innerHTML service-pane) service-html)
      (when reply
                  ; in the case of an oppost expansion
        (when-let [post-text (.querySelector reply ".oppost-text")]
          (set! (.-maxHeight (.-style post-text)) "none"))
        (.insertBefore reply-elt (.querySelector reply ".post-container")
                       post-container)
        (.removeChild reply-elt post-container))))
  false)

;; snapin button handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn continue-chain [link-queue key]
  (if-let [url (first link-queue)]
    (let [target (bk/make-target url)]
      (cb-let [threads stats meta] (bk/load-threads {:target target :key :pages})
        (when-let [form (:form meta)]
          (.appendChild (.-body js/document) (:form meta)))
        (when threads
          (set! (.-innerHTML (child-by-class threads "thread-group"))
                (format-stats stats target))
          (append-threads threads :hide-indicator true))
        (js/setTimeout #(continue-chain (rest link-queue) key) 200)))))

(defn start-chain [url key]
  (let [match (.match url (js/RegExp "([^\\[]+)\\[([^\\]]+)\\](.*)"))
        prefix (aget match 1)
        boards (vec (.split (aget match 2) (js/RegExp "[\\s\\+,]")))
        postfix (aget match 3)
        links (for [board boards]
                (str prefix (str (. board (trim)) postfix) ":chain"))
        target (bk/make-target (str (first links) ":first"))]
    (set! *target* target)
    (if  *target*
      (do 
;        (load-external (str "http://" (:trade *target*) "/favicon.ico") "ico")
        (set! (.-innerHTML (dom-get-element "thread-list-caption") )
              (str "<b>" (:trade target) "</b> board chain"))
        (cb-let [threads stats meta] (bk/load-threads {:target target :key :pages})
          (when-let [form (:form meta)]
            (.appendChild (.-body js/document) (:form meta)))
          (when-let [navbar (:navbar meta)]
            (let [navbar-popup (dom-get-element "nav-popup")
                  navbar-elt (.-firstChild navbar-popup)]
               (when navbar-elt (.removeChild navbar-popup navbar-elt))
               (.appendChild navbar-popup navbar)))
          (when threads
            (set! (.-innerHTML (child-by-class threads "thread-group"))
                  (format-stats stats target))
            (insert-threads threads :meta meta)
            (js/setTimeout #(continue-chain (rest links) key) 200))))
      (let [t-h (dom-get-element "thread-headlines")]
        (set! (.-innerHTML t-h) error-loading-page)))))

(defn load-threads [pages subsequent]
  (let [thread-headlines (dom-get-element "thread-headlines")
        url *resource*]
    (set! (.-innerHTML thread-headlines) obtaining-data)
    (if (and (> (.indexOf url "]") 0)
             (> (.indexOf url "[") 0))
      (start-chain url pages)
      (do     
        (set! *target* (bk/make-target (js/decodeURI url)))
        (when (:fourchan *target*)
          (load-css *theme* "flags-4chan.css"))
        (if *target*
          (let [target (assoc *target* :subsequent subsequent)]
;            (load-external (str "http://" (:trade target) "/favicon.ico") "ico")
            (set! (.-innerHTML (dom-get-element "thread-list-caption"))
                  (str "Loading " (pages target)
                       (if (not= (pages target) 1)
                         " pages..."
                         " page...")))
            (cb-let [threads stats meta] (bk/load-threads {:target target :key pages})
                    (when-let [form (:form meta)]
                              (.appendChild (.-body js/document) form))
                    (when-let [navbar (:navbar meta)]
                              (let [navbar-popup (dom-get-element "nav-popup")
                                    navbar-elt (.-firstChild navbar-popup)]
                                   (when navbar-elt (.removeChild navbar-popup navbar-elt))
                                   (.appendChild navbar-popup navbar)))
                    (set! (.-innerHTML (dom-get-element "thread-list-caption"))
                          (format-stats stats target))
                    (insert-threads threads :meta meta)))
          (let [t-h (dom-get-element "thread-headlines")]
            (set! (.-innerHTML t-h) error-loading-page)))))))

(defn refresh-handler [pages]
  (fn [e]
    (load-threads pages true)))

(defn ^:export expand-btn-handler [e]
  (let [expand-btn (dom-get-element "expand-btn")
        nav-popup (dom-get-element "nav-popup")
        tab-page (dom-get-element "tab-page")
        threads (dom-get-elements-by-class "thread-oppost" tab-page)
        expand? (if (= (.-textContent expand-btn) "Expand") true false)]
    (hide-elt nav-popup) ; popup uses the 'visibility' css rule, but we need to set 'display'
    (if (= (.-textContent expand-btn) "Expand")
      (do
        (reset! *expand-counter* (.-length threads))
        (set! (.-textContent expand-btn) "Collapse")
        (set! (.-title expand-btn) "Collapse all threads"))
      (do
        (when (and *target* (not (:chain *target*)))
          (bk/save-expanded {:target *target* :state :clear}))
        (reset! *expand-counter* 0)
        (set! (.-textContent expand-btn) "Expand")
        (set! (.-title expand-btn) "Expand all threads")))
    (array/forEach threads
                   #(expand-thread (.-id %) expand? :mass true))
    (show-elt nav-popup "block")))

(defn get-base-url[]
      (str (.-pathname js/location)))

(defn setup-snapin-buttons []
  (let [a-btn (dom-get-element "follow-btn")
        container (dom-get-element "address-btn-container")]
       (events/listen a-btn
                      goog.events.EventType/CLICK
                           #(let [a-rect (.getBoundingClientRect container)]
                              (.setPosition tiny-urlbar-popup (ClientPosition. (.-left a-rect) (.-top a-rect)))
                              (.setVisible tiny-urlbar-popup true)
                              (.focus (dom-get-element "address-txt")))))
                      #_(inline-dialog "Follow" (str (get-base-url) "?urlbar#frame"))

  (let [s-btn (dom-get-element "settings-btn")]
    (events/listen s-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Settings" (str (get-base-url) "?settings&target="
                                                   (:trade *target*)))))

  (let [w-btn (dom-get-element "watch-btn")]
    (events/listen w-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Watch" (str (get-base-url)  "?watch"))))

  (let [h-btn (dom-get-element "help-btn")]
    (events/listen h-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Help" (str (get-base-url)  "?help"))))

  (when (:chain *target*)
    (set! (.-display (.-style (dom-get-element "go-again-btn"))) "none")
    (set! (.-display (.-style (dom-get-element "refresh-btn"))) "none"))

  (let [expand-btn (goog.ui/decorate (dom-get-element "expand-btn"))
        go-again-btn (goog.ui/decorate (dom-get-element "go-again-btn"))
        refresh-btn (goog.ui/decorate (dom-get-element "refresh-btn"))]
    (when expand-btn
      (events/listen expand-btn
                     goog.ui.Component.EventType/ACTION
                     expand-btn-handler))
    (when go-again-btn
      (events/listen go-again-btn
                     goog.ui.Component.EventType/ACTION
                     (refresh-handler :pages)))
    (when refresh-btn
      (events/listen refresh-btn
                     goog.ui.Component.EventType/ACTION
                     (refresh-handler :refresh)))))

;; reply form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; take ketchup at the fridge
(defn ^:export show-reply-form [element new-thread?]
  (let [thread-line (or (parent-by-class element "group-line") ; for the case of chain
                        (parent-by-class element "thread-line"))
        target (get-target thread-line)
        thread-header (parent-by-class element "thread-header")
        thread-header (or thread-header (child-by-class thread-line "thread-header"))
        thread-no (when thread-header (child-by-class thread-header "thread-no"))
        thread-id (when thread-no (.-textContent thread-no))
        popup-elt (dom-get-element "post-form-popup")
        anchor (when popup-elt (.-_anchor popup-elt))]
    (when popup-elt ; hide existing form
      (when (not new-thread?) 
        (set! (.-innerHTML anchor) "[+]"))
      (.removeChild (.-body js/document) popup-elt))
    (when (not (identical? anchor element)) ; show form
      (let  [popup-elt (create-elt "div" 
                                   {"class" "popup",
                                    "id" "post-form-popup",
                                    "innerHTML" (str
                                                 "<div class=\"form-header\">
                                                    <span class=\"gold reply-to\">Reply to: </span>
                                                    <span class=\"insertion-point\">&nbsp;</span>"
                                                 service-loading
                                                 "<a class=\"form-close-btn header-btn\"
                                                      title=\"Close form\">&#x25a0;</a>
                                                    <div class=\"clear\"></div>
                                                 </div>
                                                 <div class=\"post-error\"></div>")})
             header (.-firstChild popup-elt)
             load-indicator (child-by-class header "service-loading")
             reply-to (child-by-class header "reply-to")
             form (dom-get-element (str (:prefix target) "postform"))
             form-clone (.cloneNode form true)
             textarea (.querySelector form-clone "textarea")
             parent-key (.-__parent_key__ form)
             set-width (.-__set_width__ form)
             set-height (.-__set_height__ form)
             iframe? (.-__is_iframe__ form)
             captcha-elt (.-__captcha_elt__ form)
             captcha-attr (.-__captcha_attr__ form)
             captcha-challenge (.-__captcha_challenge__ form)
             captcha-elt (.querySelector form-clone captcha-elt)
             captcha-row (.-__captcha_row__ form)
             captcha-row-elt (when captcha-row (.querySelector form-clone captcha-row))

             set-captcha (fn [captcha-elt force]
                           (cb-let [c] (bk/get-captcha 
                                        {:target target :parent (not new-thread?)
                                         :thread-id thread-id :force force})
                            (if (:link c)
                             (do
                              (.setAttribute captcha-elt captcha-attr 
                                             (if force 
                                               (str (:link c) "#" (.random js/Math))
                                               (:link c)))
                              (when captcha-challenge
                                (let [challenge-elt (.querySelector 
                                                     form-clone
                                                     (str "input[name='" captcha-challenge "']"))]
 
                                  (when challenge-elt
                                    (.removeChild (.-parentNode challenge-elt) challenge-elt))
                                  (.appendChild form-clone
                                                (create-elt "input" 
                                                            {"type" "hidden"
                                                             "name" captcha-challenge
                                                             "value" (:challenge c)})))))
                             (if captcha-row
                               (.removeChild (.-parentNode captcha-row-elt) captcha-row-elt)
                               (.removeChild (.-parentNode captcha-elt) captcha-elt)))))
             handle-post-response (fn [response]
                                    (hide-elt (child-by-class popup-elt "service-loading"))
                                    (bk/forget-captcha {:target target :thread-id thread-id})
                                    (let [error-node (child-by-class popup-elt "post-error")]
                                      (if (:error response)
                                        (do
                                          (show-elt error-node "block")
                                          (set! (.-innerHTML error-node) (:error response)))
                                        (do
                                          (show-reply-form element new-thread?)
                                          (if new-thread?
                                            (load-threads :refresh true)
                                            (iv-load-posts element (:replies target)))))))
             ]

        (when set-width
              (set! (.-width (.-style popup-elt)) set-width))
        (when set-height
              (set! (.-height (.-style popup-elt)) set-height))

        (new goog.fx.Dragger popup-elt (.-firstChild popup-elt))
        (set! (.-_anchor popup-elt) element)

        (.insertBefore popup-elt form-clone (.-lastChild popup-elt))
        (.appendChild (.-body js/document) popup-elt)

        (set! (.-id form-clone) "postform")
        (show-elt form-clone "block")
        (hide-elt load-indicator)

        (when iframe?
              (let [iframe (.querySelector form-clone "iframe")]
                   (show-elt load-indicator "inline")
                   (if new-thread?
                     (.setAttribute (.querySelector form-clone "iframe") "src" (str (pp/get-scheme target) (:forum target) "#form"))
                     (let [url (str (pp/html-thread-url thread-id target) "#form")]
                          (.setAttribute iframe "src" url)))
                   (.once io/*port* "dark-flow:post-form-iframe-loaded" #(do
                                                                           (hide-elt load-indicator)
                                                                           (set! (.-display (.-style iframe)) "block")))
                   (.once io/*port* "dark-flow:post-form-iframe-submitted" #(handle-post-response {}))))


            (when new-thread?
          (set! (.-textContent reply-to) (str "New thread in /" (:board target))))

        (when (not new-thread?)
              (let [post-header (or (parent-by-class element "reply-header") thread-header)
                    post-no (or (child-by-class post-header "reply-no") thread-no)
                    post-no (.cloneNode post-no true)
                    post-id (.-textContent post-no)]

                   (set! (.-innerHTML element) "[=]")
                   (.insertBefore header post-no (child-by-class header "insertion-point"))
                   (set! (.-className post-no) "thread-no form-reply-no")

                   ;; when replying to a thread, add the thread id to the form
                   (let [parent-elt (.querySelector form-clone (str "input[name='" parent-key "']"))
                         ;                  usercode-elt (.querySelector form-clone ".qr-usercode-input")
                         new-parent (create-elt "input"
                                                {"type" "hidden"
                                                 "name" parent-key
                                                 "value" thread-id})]
                        (when parent-elt
                              (.removeChild (.-parentNode parent-elt) parent-elt))

                        ;(if usercode-elt
                        ;  (.insertBefore form-clone new-parent usercode-elt)
                        (.appendChild form-clone new-parent));)

            (let [post-text (str ">>" post-id "\n\n")
                  selection (.toString (.getSelection js/window))]

              (if (and textarea (> (.-length selection) 0))
                (set! (.-value textarea)
                      (str post-text
                           (reduce str (map #(str ">" % "\n")
                                            (filter (complement str/blank?)
                                                    (map #(str/trim %)
                                                         (str/split selection #"\n")))))
                           "\n"))
                (when textarea (set! (.-value textarea) (str post-text)))))
            
            (set! (.-onmouseover post-no)
                  (fn [e]
                    (show-popup e (.-textContent (.-target e)) true)))))

        (when captcha-elt
          (set! (.-cursor (.-style captcha-elt)) "pointer")
          (set-captcha captcha-elt false)
          (set! (.-onmousedown captcha-elt) #(set-captcha (.-target %) true)))

        (when-let [sage-box (child-by-id form-clone "sagecheckbox")]
          (let [e-mail (child-by-id form-clone "e-mail")]
          ;(.setAttribute (.-parentNode sage-box) "onclick" "")
          (set! (.-onclick sage-box) (fn [e]
                                       (if (.-checked (.-target e))
                                         (set! (.-value e-mail) "sage")
                                         (set! (.-value e-mail) ""))))))

        (cb-let [password] (bk/get-password)
          (when-let [password-elt (.querySelector form-clone "input[type='password']")]
            (set! (.-value password-elt) password)))

        (set! (.-onclick (child-by-class header "form-close-btn"))
              (fn [e]
                (show-reply-form element new-thread?)))
        (set! (.-onsubmit form-clone)
              (fn [e]
                (show-elt load-indicator "inline")
                (let [form (.-target e)
                      url (.-action form)
                      data (.toObject (forms/getFormDataMap (.-target e)))
                      file-field (.querySelector form "input[type='file']")
                      pwd-field (.querySelector form "input[type='password']")
                      password (when pwd-field (.-value pwd-field))
                      post-file-form 
                      (fn [callback]
                        (let [reader (new js/FileReader)
                              file-obj (aget (.-files file-field) 0)]
                          (set! (.-onload reader)
                                (fn [e]
                                  (let [blob (.-result (.-target e))]
                                    (aset data (.-name file-field)
                                          (js-obj "type" (.-type file-obj)
                                                  "name" (.-name file-obj)
                                                  "size" (.-size file-obj)
                                                  "data" blob))
                                    (bk/post-form {:target target :url url :form data 
                                                   :thread-id thread-id :password password} 
                                                  callback))))
                          (.readAsBinaryString reader file-obj)))] 
                  (if (and file-field (> (.-length (.-files file-field)) 0))
                    (cb-let [response] (post-file-form)
                      (handle-post-response response))
                    (cb-let [response] (bk/post-form {:target target :url url :form data 
                                                      :thread-id thread-id :password password})
                      (handle-post-response response))))
                false))

        (let [popup (goog.ui.Popup. popup-elt)]
          (.setAutoHide popup false)
          (.setPinnedCorner popup Corner/TOP_LEFT)
          (.setPosition popup (AnchoredViewportPosition.
                                   (.-nextSibling (.-nextSibling element))
                                   Corner/TOP_RIGHT))
          (.setVisible popup true)
          (when textarea
            (.focus textarea)
            (let [pos (.-length (.-value textarea))]
              (set! (.-selectionStart textarea) pos)
              (set! (.-selectionEnd textarea) pos))))))))

;;;;;;;;;;;;;;;;;;;

(defn setup-nav-popup []
  (let [nav-img (.querySelector js/document "#nav-btn > img")
        popup-elt
        (create-elt "div"
                    {"class" "nav-popup",
                     "id" "nav-popup"})]
    (set! (.-src nav-img) (themed-image (.getAttribute nav-img "src")))
    (.appendChild (.-body js/document) popup-elt)
    (set! *nav-popup* (goog.ui.Popup. popup-elt))
    (let [nav-elt (dom-get-element "nav-btn")]
      (.setPosition *nav-popup* (AnchoredViewportPosition.
                                     nav-elt
                                     goog.positioning.Corner/BOTTOM_LEFT))
      (set! (.-onclick nav-elt) (fn [event] (show-nav-popup event))))
    (.setPinnedCorner *nav-popup* goog.positioning.Corner/BOTTOM_LEFT)))

(defn ^:export show_nav_popup [e]
  (dom-get-element "nav-popup")
  (.setVisible *nav-popup* true))

;; external entry points ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export init [opts]
  (set! *file-base* (.-file-base opts))
  (set! *addon* (.-addon opts)))

(defn ^:export settings [settings]
  (with-page settings [settings]
    (let [wf-lbl (goog.ui.Tooltip. "wf-label")]
      (.setHtml wf-lbl "Place each wordfilter entry on a single line.<br/>
                     A regexp should be prefixed with the hash character ('#'),
                     for example: <span class=\"gold\">#\\bpony\\b</span>."))

    (let [fav-lbl (goog.ui.Tooltip. "fav-lbl")]
      (.setHtml fav-lbl "Default parameters for a board on a single line,
                     for example: <span class=\"gold\">4chan.org/c:10p:3r:img</span>.<br/>
                     When loading the board it's possible to disable a default switch specified here
                     by <br/>adding an exclamation mark in front of it, for example: 
                     <span class=\"gold\">4chan.org/c:5p:!img<span class=\"gold\">."))

    (let [uri (goog.Uri. (.-href (.-location js/document)))
          warning (dom-get-element "warning")]
      (when (= "iichan.hk" (.getParameterValue uri "target"))
        (set! (.-display (.-style warning)) "inline")))
    
    (let [remember-btn (goog.ui/decorate (dom-get-element "remember-btn"))]
      (events/listen remember-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/remember-threads))))
    
    (let [clear-watch-btn (goog.ui/decorate (dom-get-element "clear-watch-btn"))]
      (events/listen clear-watch-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/clear-watch))))

    (let [clear-data-btn (goog.ui/decorate (dom-get-element "clear-data-btn"))]
      (events/listen clear-data-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/clear-data))))

    (let [save-btn (goog.ui/decorate (dom-get-element "save-settings-btn"))]
      (events/listen save-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/save-settings))))

    (set! (.-checked (dom-get-element "watch-first")) (:pin-watch-items settings))
    (set! (.-checked (dom-get-element "remember-expanded")) (:remember-expanded settings))
    (set! (.-value (dom-get-element "wf-title-words")) (if (:wf-title settings)
                                                        (:wf-title settings)
                                                        ""))
    (set! (.-value (dom-get-element "wf-post-words")) (if (:wf-post settings)
                                                       (:wf-post settings)
                                                       ""))
    (set! (.-checked (dom-get-element "wf-enable")) (:wf-enabled settings))
    (set! (.-value (dom-get-element "theme")) (:theme settings))
    (set! (.-value (dom-get-element "favorites")) (if (:default-params settings)
                                                   (:default-params settings)))
    (set! (.-checked (dom-get-element "force-textonly")) (:force-text settings))))

(defn ^:export watch [settings]
  (with-page inline-watch-stream [settings]
    (cb-let [threads] (bk/load-watch-items nil)
      (if threads
        (insert-threads threads)
        (let [t-h (dom-get-element "thread-headlines")]
          (set! (.-innerHTML t-h) the-list-is-empty))))))

(defn ^:export images [settings]
  (with-page inline-image-stream [settings]
    (let [thread-headlines (dom-get-element "thread-headlines")]
      (set! (.-innerHTML thread-headlines) obtaining-data))

    (let [uri (goog.Uri. (.-href (.-location js/document)))
          target (reader/read-string (.getParameterValue uri "target"))
          thread-id (.getParameterValue uri "thread-id")]

      (cb-let [images] (bk/get-thread-images {:target target :thread-id thread-id})
              (insert-threads images)))))

(defn ^:export video [settings]
  (with-page video [settings]
    (let [uri (goog.Uri. (.-href (.-location js/document)))
          video-link (js/decodeURIComponent (.getParameterValue uri "video"))
          video-elt (dom-get-element "video")]
      (.setAttribute video-elt "src" video-link)

)))

(defn ^:export help [settings]
  (with-page manual [settings]   
    (let [manual (.querySelector js/document "#manual")]
      (set! (.-src manual) (file-base (.getAttribute manual "src"))))))

(defn ^:export urlbar [settings url]
  (with-page urlbar [settings]
    (set! (.-title js/document) "Dark Flow")
    (let [hash (.-hash js/location)
          frame? (when hash (.startsWith hash "#frame"))
          address-txt (dom-get-element "address-txt")
          go-btn (goog.ui/decorate (dom-get-element "go-btn"))
          go-btn-handler (fn [e]
                           (when (not (str/blank? (.-value address-txt)))
                             (let [loc (.-value address-txt)
                                   ]
                                  (if frame?
                                     (.emit io/*port* "load-threads" (js-obj "url" loc "parent" true))
                                     (.emit io/*port* "follow-url" (js-obj "url" (str "?front&url=" (if (> (.indexOf loc "://") 0) loc (str "chan://" loc))
                                                                                  (when (.-checked (dom-get-element "text-only"))
                                                                                        ":txt"))
                                                                       "parent" false))))))]
      (when url (set! (.-value address-txt) url))
      (events/listen address-txt
                     goog.events.EventType/KEYPRESS
                     (fn [e]
                       (when (= (.-keyCode e) 13)
                         (go-btn-handler e))))
      (events/listen go-btn
                     goog.ui.Component.EventType/ACTION
                     go-btn-handler))))


(defn ^:export front [settings url]
;;;;;
;;  (repl/connect "http://localhost:9000/repl")
;;;;;
  (set! *resource* url)
  (.emit io/*port* "url-followed" url)
  (set! (.-title js/document) url)
  (.on io/*port* "load-threads" #(do (.emit io/*port* "url-followed" (.-url %))
                                     (set! *resource* (.-url %))
                                     (set! (.-title js/document) (.-url %))
                                     (close-active-dialog)
                                     (load-threads :pages false)))
  (with-page frontend [settings]
    (setup-snapin-buttons)
    (setup-nav-popup)
    (load-threads :pages false)
    (setup-tiny-urlbar-popup)))

