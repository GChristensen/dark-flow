;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.frontend
  (:require
   [kuroi.base :as base]
   [kuroi.backend :as bk]
   [kuroi.settings :as opts]

   [clojure.string :as str]
   [clojure.browser.repl :as repl]
   [cljs.reader :as reader]

   [goog.dom :as dom]
   [goog.uri :as uri]
   [goog.Uri :as uri]
   [goog.math :as math]
   [goog.fx.Dragger :as dragger]
   [goog.object :as goog-object]
   [goog.dom.query :as query]
   [goog.events :as events]
   [goog.style :as style]
   [goog.array :as array]
   [goog.dom.forms :as forms]
   [goog.ui.Dialog :as dialog]
   [goog.Timer :as timer]
   [goog.ui.Tooltip :as tooltip]
   [goog.ui.Popup :as popup]
   [goog.ui.Button :as button]
   [goog.ui.FlatButtonRenderer :as flat-button-rndr]
   [goog.positioning :as pos]
   )
  (:use-macros [kuroi.macros :only [cb-let with-page s-in? log]]))

(def *target*)
(def *nav-popup*)

(def *expand-counter* (atom 0))

(def ^:const forget-trigger "<a class=\"forget-trigger\" href=\"\" onclick=\"frontend.forget_thread(this)\"  
			                 title=\"Forget thread\">&#x00d7;</a>")
(def ^:const empty-set "<div class=\"prohibit\">&#x20e0;</div>")
(def ^:const error-loading-page "<div class=\"prohibit load-indicator\">Error loading page</div>")
(def ^:const the-list-is-empty "<div class=\"load-indicator\">The list is empty</div>")

(def *theme*)
(def *file-base*)
(def loading-post)
(def service-loading)
(def obtaining-data)

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
  (dom/findNode root (fn [node]
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
  (dom/findNode root #(= (.-id %) id))) 

(defn create-elt [tag attrs]
  (dom/createDom tag (.-strobj attrs)))

(defn hide-elt [e]
  (set! (.-display (.-style e)) "none"))

(defn show-elt [e display]
  (set! (.-display (.-style e)) display))

(defn show-output [content]
  (let [output (dom/getElement "output")]
    (set! (.-innerHTML output) content)))

(defn load-external [filename filetype]
  (letfn [(append [elt]
            (.appendChild (aget (.getElementsByTagName js/document "head") 0) elt))]
    (condp = filetype
      "css" (let [elt (.createElement js/document "link")]
              (set! (.-rel elt) "stylesheet")
              (set! (.-type elt) "text/css")
              (set! (.-href elt) filename)
              (append elt))
      "ico" (let [elt (.createElement js/document "link")]
              (set! (.-rel elt) "icon")
              (set! (.-href elt) filename)
              (append elt))
      nil)))

(defn load-css [theme filename]
  (load-external (str *file-base* "themes/" theme "/css/" filename) "css"))
 
(defn load-styles [theme &{:keys [settings]}]
    (load-css theme "main.css")
    (load-css theme "closure.css")
    (if settings
      (load-css theme "settings.css")
      (load-css theme "frontend.css")))

(defn format-stats [stats target]  
  (let [shown (if (:img target) (:shown stats) (+ (:shown stats) (:watch stats)))
        additional (if (:img target) 0 (- (:watch-total stats) (:watch stats)))
        filtered (:filtered stats)
        forgotten (:forgotten stats)
        hidden (if (:img target) 0 (+ filtered forgotten))]
    (if (string? stats)
      stats
      (str (when (not (or (:img target) (base/*posting-not-impl* (:trade target))))
             "<a class=\"new-thread\" title=\"New thread\" style=\"cursor: pointer !important;\"
                      onclick=\"frontend.show_reply_form(this, true)\">+</a>&nbsp;")
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
            (set! (.-src i) (themed-image (.-src i)))))
        (.removeChild t-h (.-firstChild t-h))
        (.appendChild t-h threads))
      (set! (.-innerHTML t-h) err-msg))
    (when (:alert meta)
      (js/alert (:alert meta)))))

(defn append-threads [threads &{:keys [hide-indicator]}]
  (when hide-indicator
    (when-let [loading-thread (.querySelector threads ".loading-thread")]
      (hide-elt loading-thread)))
  (let [t-h (dom/getElement "thread-headlines")]
    (.appendChild t-h threads)))

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
                   (.preventDefault e)
                   (set! (.-curX el) (- (.-clientX e) (js/parseInt (.-left (.-style el)))))
                   (set! (.-curY el) (- (.-clientY e) (js/parseInt (.-top (.-style el)))))
                   (set-events (.-body js/document)
                               {"mousemove" move-elt "mouseup" stop-elt}))})))

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
  (let [full (.-singleNodeValue (.evaluate js/document ".//img[@id=\"_fullimg\"]" a nil 8 nil))]
    (if full ; shown
      (if (not (.-moved full))
        (do
          (set! (.-display (.-style full))
                (if (= (.-display (.-style full)) "none") "" "none"))
          (js/setTimeout #(.removeChild (.-parentNode full) full) 0))
        (set! (.-moved full) false))
      ; none
      (let [full (.createElement js/document "img")
            existing (dom/getElement "_fullimg")
            scr-w (.-clientWidth (.-body js/document))
            scr-h (.-innerHeight js/window)
            [new-w new-h] (if (or (> full-w scr-w) (> full-h scr-h))
                            (let [new-w (/ (* full-w scr-h) full-h)
                                  new-h (/ (* full-h scr-w) full-w)]
                              [(if (> new-w scr-w) scr-w new-w) (if (<= new-w scr-w) scr-h new-h)])
                            [full-w full-h])]
        (when existing
          (.removeChild (.-parentNode existing) existing))
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
                    })
        (.appendChild a full))))
  false)

;; inline view ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn inline-dialog [title target]
  (let [dialog (goog.ui.Dialog.)
        close-elt (. dialog (getTitleCloseElement))]
    (if (vector? title)
      (let [title-elt (. dialog (getTitleTextElement))]
        (set! (.-innerHTML title-elt) (first title)))
      (.setTitle dialog title))
    (.setContent dialog (str "<iframe src=\"" target "\"/>")
    (.setButtonSet dialog nil)
    (set! (.-innerHTML close-elt) "Close")
    (set! (.-className close-elt) "modal-dialog-title-close goog-flat-button")
    (.setVisible dialog true))
    false))

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
        iframe-link (str base/*protocol* ":images?target=" (js/encodeURI (pr-str target))
                         "&thread-id=" (.-textContent thread-no))
        title (str "<a class=\"title-link\" target=\"_blank\" href=\"" thread-link "\">" 
                   thread-link "</a>")]    
    (inline-dialog [title] iframe-link)))

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
        popup-elt (dom/getElement (str "popup-" post))
        out (dom/getElement "output")]
    (when (and popup popup-elt)
      (let [box (style/getBounds popup-elt)
            point (goog.math.Coordinate. (.-clientX e) (.-clientY e))]
        (when (not (inside? box point))
          (.setVisible popup false))))))

(defn dismiss-popup [popup]
  (let [post (.-post_ popup)
        popup-elt (dom/getElement (str "popup-" post))]
    (when popup-elt
      (events/unlisten popup-elt goog.events.EventType/MOUSEOUT)
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
  (let [make-popup (fn [node]
                     (let  [post (if node post "loading")
                            popup-elt
                            (create-elt "div"
                                        {"class" "popup",
                                         "id" (str "popup-" post),
                                         "innerHTML" (if node "<div/>" loading-post)})]
                       (.appendChild (.-body js/document) popup-elt)
                       (when node
                         (let [reply-elt (.cloneNode node true)
                               oppost-text (child-by-class reply-elt "oppost-text")]
                           (when oppost-text
                             (set! (.-maxHeight (.-style oppost-text)) "none"))
                           (.removeChild popup-elt (.-firstChild popup-elt))
                           (.appendChild popup-elt reply-elt)))
                       (let [popup (goog.ui.Popup. popup-elt)
                             corner (quadrant-dispatch (.-clientX e) (.-clientY e)
                                                       goog.positioning.Corner/TOP_LEFT
                                                       goog.positioning.Corner/TOP_RIGHT
                                                       goog.positioning.Corner/BOTTOM_LEFT
                                                       goog.positioning.Corner/BOTTOM_RIGHT)
                             dd (quadrant-dispatch (.-clientX e) (.-clientY e)
                                                   [-5 -5] [5 -5] [-5 5] [5 5])]
                         (set! popups (assoc popups post popup))
                         (set! (.-post_ popup) post)
                         (.setPinnedCorner popup corner)
                         (.setPosition popup (goog.positioning.ClientPosition.
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
                        (cb-let [reply] (bk/get-popup-post {:url (.-href (.-target e))})
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
        (let [nodes (dom/getElementsByClass "reply-no" (dom/getElement "thread-stream"))
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

;; thread control handlers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export lazy-get-watch [element watch-stream?]
  (let [thread-line (parent-by-class element "thread-line")
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
                  oppost (child-by-class thread-line "thread-oppost")]
              (set! (.-innerHTML service-pane) (delta-posts (:post-delta response)))
              (set! (.-innerHTML post-count) (str "[" (:post-count response) "]"))
              (when (:replies response)
                (let [expand-trigger (child-by-class thread-line "expand-trigger")
                      existing-replies (child-by-class oppost "replies")
                      replies (.-firstChild (:replies response))]
                  (.removeChild oppost existing-replies)
                  (.appendChild oppost replies)
                  (if (.-expanded_ expand-trigger)
                    (set! (.-display (.-style replies)) "block")))))
            (set! (.-innerHTML service-pane) empty-set))))
      ;; if the dom isn't constructed yet
      (js/setTimeout #(lazy-get-watch element watch-stream?) 2000))))

(defn ^:export expand-thread [post-id expand? &{:keys [mass]}]
  (let [post (dom/getElement post-id)
        thread-headlines (dom/getElement "thread-headlines")
        expand-btn (when (not mass) (dom/getElement "expand-btn"))
        expand-trigger (child-by-class (.-parentNode post) "expand-trigger")
        post-text (child-by-class post "oppost-text")
        replies (child-by-class post "replies")
        nav-popup (dom/getElement "nav-popup")]
    (hide-elt nav-popup)
    (cond (or (false? expand?) (and (= expand? js/undefined) (.-expanded_ expand-trigger)))
          (do
            (set! (.-maxHeight (.-style post-text)) "65px")
            (set! (.-display (.-style replies)) "none")
            (set! (.-innerHTML expand-trigger) "&raquo;")
            (set! (.-expanded_ expand-trigger) false)
            (when (not mass)
              (swap! *expand-counter* dec)
              (when (= 0 @*expand-counter*)
                (set! (.-textContent expand-btn) "Expand")))
              (let [vp-offset (- (.-y (style/getClientPosition post))
                                 (.-y (style/getClientPosition thread-headlines)))]
                (when (< vp-offset 0)
                  (. post (scrollIntoView)))))
          (or (true? expand?) (and (= expand? js/undefined) (not (.-expanded_ expand-trigger))))
          (let [service-pane (child-by-class (.-parentNode post) "service-pane")]
            (when (not mass)
              (swap! *expand-counter* inc)
            (set! (.-textContent expand-btn) "Collapse"))
            (when (> (.indexOf (.-innerHTML service-pane) "[?]") 0)
              (lazy-get-watch post false))
            (set! (.-maxHeight (.-style post-text)) "none")
            (set! (.-display (.-style replies)) "block")
            (set! (.-innerHTML expand-trigger) "&laquo;")
            (set! (.-expanded_ expand-trigger) true)))
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
              (set! (.-innerHTML service-pane) forget-trigger))
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
        (let [oppost (child-by-class thread-line "thread-oppost")
              existing-replies (child-by-class oppost "replies")]
          (when-let [indicator (.querySelector posts ".load-indicator > img")]
            (set! (.-src indicator) (themed-image (.-src indicator))))
          (.removeChild oppost existing-replies)
          (.appendChild oppost (.-firstChild posts))
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
        (when-let [post-text (child-by-class reply "oppost-text")]
          (set! (.-maxHeight (.-style post-text)) "none"))
        (.insertBefore reply-elt (child-by-class reply "post-container")
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
        (load-external (str "http://" (:trade *target*) "/favicon.ico") "ico")
        (set! (.-innerHTML (dom/getElement "thread-list-caption") )
              (str "<b>" (:trade target) "</b> board chain"))
        (cb-let [threads stats meta] (bk/load-threads {:target target :key :pages})
          (when-let [form (:form meta)]
            (.appendChild (.-body js/document) (:form meta)))
          (when-let [navbar (:navbar meta)]
            (.appendChild (dom/getElement "nav-popup") navbar))
          (when threads
            (set! (.-innerHTML (child-by-class threads "thread-group"))
                  (format-stats stats target))
            (insert-threads threads :meta meta)
            (js/setTimeout #(continue-chain (rest links) key) 200))))
      (let [t-h (dom/getElement "thread-headlines")]
        (set! (.-innerHTML t-h) error-loading-page)))))

(defn refresh-handler [pages]
  (fn [e]
    (let [thread-headlines (dom/getElement "thread-headlines")
          url (.-href (.-location js/document))]
      (set! (.-innerHTML thread-headlines) obtaining-data)
      (if (and (> (.indexOf url "]") 0)
               (> (.indexOf url "[") 0))
        (start-chain url pages)
        (do     
          (set! *target* (bk/make-target (js/decodeURI url)))
          (if  *target*
            (do 
              (load-external (str "http://" (:trade *target*) "/favicon.ico") "ico")
              (set! (.-innerHTML (dom/getElement "thread-list-caption"))
                    (str "Loading " (pages *target*)
                         (if (not= (pages *target*) 1)
                           " pages..."
                           " page...")))
              (cb-let [threads stats meta] (bk/load-threads {:target *target* :key pages})
                      (when-let [form (:form meta)]
                        (.appendChild (.-body js/document) form))
                      (when-let [navbar (:navbar meta)]
                        (.appendChild (dom/getElement "nav-popup") navbar))
                      (set! (.-innerHTML (dom/getElement "thread-list-caption"))
                            (format-stats stats *target*))
                      (insert-threads threads :meta meta)))
            (let [t-h (dom/getElement "thread-headlines")]
              (set! (.-innerHTML t-h) error-loading-page))))))))

(defn load-threads 
  ([]
     ((refresh-handler :pages) nil))
  ([pages]
     ((refresh-handler pages) nil)))

(defn ^:export expand-btn-handler [e]
  (let [expand-btn (dom/getElement "expand-btn")
        nav-popup (dom/getElement "nav-popup")
        tab-page (dom/getElement "tab-page")
        threads (dom/getElementsByClass "thread-oppost" tab-page)
        expand? (if (= (.-textContent expand-btn) "Expand") true false)]
    (hide-elt nav-popup) ; popup uses the 'visibility' css rule, but we need to set 'display'
    (if (= (.-textContent expand-btn) "Expand")
      (do
        (reset! *expand-counter* (.-length threads))
        (set! (.-textContent expand-btn) "Collapse")
        (set! (.-title expand-btn) "Collapse all threads"))
      (do
        (reset! *expand-counter* 0)
        (set! (.-textContent expand-btn) "Expand")
        (set! (.-title expand-btn) "Expand all threads")))
    (array/forEach threads
                   #(expand-thread (.-id %) expand? :mass true))
    (show-elt nav-popup "block")))

(defn setup-snapin-buttons []
  (let [s-btn (dom/getElement "settings-btn")]
    (events/listen s-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Settings" (str base/*protocol* ":settings?target=" 
                                                   (:trade *target*)))))

  (let [w-btn (dom/getElement "watch-btn")]
    (events/listen w-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Watch" (str base/*protocol* ":watch"))))

  (let [h-btn (dom/getElement "help-btn")]
    (events/listen h-btn
                   goog.events.EventType/CLICK
                   #(inline-dialog "Help" (str base/*protocol* ":help"))))

  (when (:chain *target*)
    (set! (.-display (.-style (dom/getElement "go-again-btn"))) "none")
    (set! (.-display (.-style (dom/getElement "refresh-btn"))) "none"))

  (let [expand-btn (goog.ui/decorate (dom/getElement "expand-btn"))
        go-again-btn (goog.ui/decorate (dom/getElement "go-again-btn"))
        refresh-btn (goog.ui/decorate (dom/getElement "refresh-btn"))]
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
        popup-elt (dom/getElement "post-form-popup")
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
                                                    <span class=\"insertion-point\">
                                                     &nbsp;</span>"
                                                 service-loading
                                                 "<a class=\"form-close-btn header-btn\"
                                                      title=\"Close form\">&#x25a0;</a>
                                                    <div class=\"clear\"></div>
                                                 </div>
                                                 <div class=\"post-error\"></div>")})
             header (.-firstChild popup-elt)
             load-indicator (child-by-class header "service-loading")
             reply-to (child-by-class header "reply-to")
             form (dom/getElement (str (:prefix target) "postform"))
             form-clone (.cloneNode form true)
             textarea (.querySelector form-clone "textarea")
             parent-key (.-__parent_key__ form)
             captcha-elt (.-__captcha_elt__ form)
             captcha-attr (.-__captcha_attr__ form)
             captcha-challenge (.-__captcha_challenge__ form)
             captcha-elt (.querySelector form-clone captcha-elt)
             set-captcha (fn [captcha-elt force]
                           (cb-let [c] (bk/get-captcha 
                                        {:target target :thread-id thread-id :force force})
                             (.setAttribute captcha-elt captcha-attr 
                                            (if force 
                                              (str (:link c) "#i" (.random js/Math))
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
                                                            "value" (:challenge c)}))))))
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
                                            (load-threads :refresh)
                                            (iv-load-posts element (:replies target)))))))
             ]

        (new goog.fx.Dragger popup-elt (.-firstChild popup-elt))
        (set! (.-_anchor popup-elt) element)

        (.insertBefore popup-elt form-clone (.-lastChild popup-elt))
        (.appendChild (.-body js/document) popup-elt)

        (set! (.-id form-clone) "postform")
        (show-elt form-clone "block")
        (hide-elt load-indicator)

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
            (let [parent-elt (.querySelector form-clone (str "input[name='" parent-key "']"))]
              (when parent-elt
                (.removeChild (.-parentNode parent-elt) parent-elt))
              (.appendChild form-clone
                            (create-elt "input" 
                                        {"type" "hidden"
                                         "name" parent-key 
                                         "value" thread-id})))

            (let [post-text (str ">>" post-id "\n\n")
                  selection (.toString (.getSelection js/window))]

              (if (> (.-length selection) 0)
                (set! (.-value textarea) 
                      (str post-text 
                           (reduce str (map #(str ">" % "\n")
                                            (filter (complement str/blank?)
                                                    (map #(str/trim %)
                                                         (str/split selection #"\n")))))
                           "\n"))
                (set! (.-value textarea) (str post-text))))
            
            (set! (.-onmouseover post-no)
                  (fn [e]
                    (show-popup e (.-textContent (.-target e)) true)))))

        (when captcha-elt
          (set! (.-cursor (.-style captcha-elt)) "pointer")
          (set-captcha captcha-elt false)
          (set! (.-onmousedown captcha-elt) #(set-captcha (.-target %) true)))

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
          (.setPinnedCorner popup goog.positioning.Corner/TOP_LEFT)
          (.setPosition popup (new goog.positioning.AnchoredViewportPosition 
                                   (.-nextSibling (.-nextSibling element))
                                   goog.positioning.Corner/TOP_RIGHT))
          (.setVisible popup true)
          (.focus textarea)
          (let [pos (.-length (.-value textarea))]
            (set! (.-selectionStart textarea) pos)
            (set! (.-selectionEnd textarea) pos)))))))

;;;;;;;;;;;;;;;;;;;

(defn setup-nav-popup []
  (let [nav-img (.querySelector js/document "#nav-btn > img")
        popup-elt
        (create-elt "div"
                    {"class" "nav-popup",
                     "id" "nav-popup"})]
    (set! (.-src nav-img) (themed-image (.-src nav-img)))
    (.appendChild (.-body js/document) popup-elt)
    (set! *nav-popup* (goog.ui.Popup. popup-elt))
    (.setPosition *nav-popup* (new goog.positioning.AnchoredViewportPosition
                                   (dom/getElement "nav-btn")
                                   goog.positioning.Corner/BOTTOM_LEFT))
    (.setPinnedCorner *nav-popup* goog.positioning.Corner/BOTTOM_LEFT)))

(defn ^:export show_nav_popup [e]
  (dom/getElement "nav-popup")
  (.setVisible *nav-popup* true))

;; external entry points ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export init [file-base]
  (set! *file-base* file-base))

(defn ^:export settings [settings]
  (with-page settings [settings]
    (let [wf-lbl (goog.ui.Tooltip. "wf-label")]
      (.setHtml wf-lbl "Place each wordfilter entry on a single line.<br/>
                     A regexp should be prefixed with the '#' character,
                     for example: <span class=\"gold\">#\\bpony\\b</span>."))

    (let [fav-lbl (goog.ui.Tooltip. "fav-lbl")]
      (.setHtml fav-lbl "Default parameters for a board on a single line,
                     for example: <span class=\"gold\">4chan.org/c:10p:3r:img</span>.<br/>
                     When loading the board it's possible to disable a switch specified here
                     by adding <br/>a exclamation mark in front of it, for example: 
                     <span class=\"gold\">4chan.org/c:5p:!img<span class=\"gold\">."))

    (let [uri (goog.Uri. (.-href (.-location js/document)))
          warning (dom/getElement "warning")]
      (when (= "iichan.hk" (.getParameterValue uri "target"))
        (set! (.-display (.-style warning)) "inline")))
    
    (let [remember-btn (goog.ui/decorate (dom/getElement "remember-btn"))]
      (events/listen remember-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/remember-threads))))
    
    (let [clear-watch-btn (goog.ui/decorate (dom/getElement "clear-watch-btn"))]
      (events/listen clear-watch-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/clear-watch))))

    (let [save-btn (goog.ui/decorate (dom/getElement "save-settings-btn"))]
      (events/listen save-btn
                     goog.ui.Component.EventType/ACTION
                     (fn [e] (opts/save-settings))))

    (set! (.-checked (dom/getElement "watch-first")) (:pin-watch-items settings))
    (set! (.-value (dom/getElement "wf-title-words")) (if (:wf-title settings)
                                                        (:wf-title settings)
                                                        ""))
    (set! (.-value (dom/getElement "wf-post-words")) (if (:wf-post settings)
                                                       (:wf-post settings)
                                                       ""))
    (set! (.-checked (dom/getElement "wf-enable")) (:wf-enabled settings))
    (set! (.-value (dom/getElement "theme")) (:theme settings))
    (set! (.-value (dom/getElement "favorites")) (if (:default-params settings)
                                                   (:default-params settings)))
    (set! (.-checked (dom/getElement "force-textonly")) (:force-text settings))))

(defn ^:export inline-watch-stream [settings]
  (with-page inline-watch-stream [settings]
    (cb-let [threads] (bk/load-watch-items nil)
      (if threads
        (insert-threads threads)
        (let [t-h (dom/getElement "thread-headlines")]
          (set! (.-innerHTML t-h) the-list-is-empty))))))

(defn ^:export inline-image-stream [settings]
  (with-page inline-image-stream [settings]
    (let [thread-headlines (dom/getElement "thread-headlines")]
      (set! (.-innerHTML thread-headlines) obtaining-data))

    (let [uri (goog.Uri. (.-href (.-location js/document)))
          target (reader/read-string (.getParameterValue uri "target"))
          thread-id (.getParameterValue uri "thread-id")]

      (cb-let [images] (bk/get-thread-images {:target target :thread-id thread-id})
              (insert-threads images)))))

(defn ^:export display-help [settings]
  (with-page manual [settings]   
    (let [manual (.querySelector js/document "#manual")]
      (set! (.-src manual) (file-base (.-src manual))))))

(defn ^:export main [settings]
;;;;;
;;  (repl/connect "http://localhost:9000/repl")
;;;;;
  (with-page frontend [settings]
    (setup-snapin-buttons)
    (setup-nav-popup)
    (load-threads)))