;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.render
  (:require
   [kuroi.io :as io]
   [kuroi.base :as base]
   [kuroi.filters :as fltr]
   [kuroi.page-parser :as pp]
   [kuroi.settings :as settings]

   [clojure.string :as str]
   [cljs.reader :as reader]

   [enfocus.core :as ef]

   [goog.dom :as dom]
   )
  (:require-macros [enfocus.macros :as em]
                   [kuroi.macros :as mk]))

;; templates and transformations ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn flatten-markup [text]
  (-> text
      (str/replace #"<br[^>]*>" " ")
      (str/replace #"<object[^>]*>.*?</object>" "")
      (str/replace #"<embed[^>]*>" "")
      (str/replace #"((?:<blockquote)|(?:<div)|(?:<p))" "$1 style=\"display: inline\" ")))

(em/deftemplate watch :compiled "templates/watch-stream.html" 
  [threads target]
  [:.thread-line]
  (mk/clone-for [th threads]
    (fn [node]
      (if (string? th)
        ((em/do-> ; equilibristics to access the root node (.thread-line) through macros
          (em/set-attr :class "group-line")
          (fn [node]
            (em/at node
                   [:.watch-thread-control]
                   (em/do->
                    (em/remove-attr :style)
                    (em/set-attr :colspan "3")
                    (em/set-attr :class "thread-group")
                    (em/content th))
                   [:.watch-image-cell] (em/remove-node)
                   [:.thread-oppost] (em/remove-node)))) node)
        (em/at node
               [:.watch-thread-control]
               (em/remove-attr :style)
               [:.watch-thread-header]
               (em/remove-attr :style)
               [:.watch-image-cell]
               (em/do->
                (em/remove-attr :style)
                (mk/remove-when (:force-text target)))
               [:.watch-image]
               (mk/do-when (and (:image th) (not (:force-text target)))
                 (em/set-attr :src (:thumb th)))
               [:.image-link]
               (mk/do-when (and (:image th) (not (:force-text target)))
                 (em/do->
                  (em/set-attr :href (:image th))
                  (em/set-attr :onclick (str "return frontend.expand_image(this,"
                                             (first (:image-size th)) ","
                                             (second (:image-size th)) ")"))))
               [:.watch-image-container]
               (mk/do-when (not (:force-text target))
                  #(if (:image th)
                    %
                    ((em/html-content "<span class=\"noimage\">No image</span>") %)))
               [:.thread-oppost]
               (em/do->
                (em/remove-attr :style)
                (em/set-attr :id (:internal-id th)))
               [:.thread-no]
               (em/do->
                (em/set-attr :title (:date th))
                (em/set-attr :href (:link th))
                (em/content (:id th)))
               [:.delta-link]
               (em/set-attr :href (str (:link th) "#" (when (:fourchan th) "p") (:last-id th)))
               [:.post-count]
               (em/content (str "[" (:post-count th) "]"))
               [:.thread-title]
               (em/html-content (:title th))
               [:.lazy-load-trigger]
               (em/set-attr :src (str io/*file-base* "empty.png"))
               ;; [:.lazy-load-trigger]
               ;; (fn [node]
               ;;   (when (:always-calc-delta target)
               ;;     node))
               [:.watch-oppost-text]
               (em/html-content (flatten-markup (:text th))))))))

(defn inject-replies [thread target &{:keys [word-filter] :or {word-filter true}}]
  (let [settings (settings/get-for target)
        replies (if (and (:wf-enabled settings) word-filter)
                  (:children (fltr/filter-replies (:wf-post-parsed settings) thread target))
                  (:children thread))]
  (fn [node]
    ((mk/clone-for [rep replies]
                [:.reply-header]
                (em/remove-attr :style)
                [:img.flag]
                (mk/do-when (:flag rep)
                   (em/do->
                     (em/set-attr :class (if (:fourchan target) 
                                           (first (:flag rep)) 
                                           "flag")) 
                     ;(em/set-attr :src (first (:flag rep)))
                     (fn [node]
                       (if (not (:fourchan target))
                         ((em/set-attr :src (first (:flag rep))) node)
                         node))
                     (em/set-attr :title (second (:flag rep)))))
                [:.reply-no]
                (em/do->
                 (em/set-attr :title (:date rep))
                 (em/set-attr :href (str (:link rep) "#" (when (:fourchan target) "p") (:id rep)))
                 (em/content (:id rep)))
                [:.reply-ord]
                (em/content (str (:post-num rep)))
                [:.reply-title]
                (em/html-content (:title rep))
                [:.post-image]
                (mk/do-when (and (not (:force-text target)) (:thumb rep))
                  (em/set-attr :src (:thumb rep)))
                [:.image-link]
                (mk/do-when (and (not (:force-text target)) (:image rep))
                  (em/do->
                   (em/set-attr :href (:image rep))
                   (em/set-attr :onclick (str "return frontend.expand_image(this,"
                                           (first (:image-size rep)) ","
                                           (second (:image-size rep)) ")"))))
                [:.image-container]
                (mk/remove-when (:force-text target))
                [:.post-text]
                (em/do->
                 (em/html-content (:text rep))
                 (em/set-attr :class (if (and (not (:force-text target)) (:image rep))
                                    "image-indent post-text"
                                    "text-indent post-text")))
                [:.crossref-container]
                (mk/remove-when (not (seq (:refs rep))))
                [:.crossref-wrap]
                (em/clone-for [ref (:refs rep)]
                              [:.crossref]
                              (em/do->
                               (em/set-attr :href (str (pp/thread-url (:id thread) target) "#"
                                                       (when (:fourchan target) "p") ref))
                               (em/set-attr :onmouseover (str "frontend.show_popup(event, '"
                                                              ref  "', true)"))
                               (em/set-attr :onmouseout (str "frontend.show_popup(event, '"
                                                             ref  "', false)"))
                               (em/html-content (str "&gt;&gt;" ref)))))
     node))))

(em/defsnippet replies :compiled "templates/thread-stream.html" [:.replies]
  [thread target &{:keys [word-filter] :or {word-filter true}}]

  [:.expand-trigger]
  (em/set-attr :onclick (str "frontend.expand_thread(\""
                          (:internal-id thread)
                          "\")"))
  [:.image-indent]
  (fn [node]
    (if (:force-text target)
      ((em/set-attr :class "text-indent") node)
      node))
  [:.reply]
  (mk/do-when (and (seq (:children thread)) (not (:hdlns thread)))
   (inject-replies thread target :word-filter word-filter)))

(defn get-label [thread]
  (letfn [(print-index [thread]
            (if (number? (:page-index thread))
              (str "page: " (:page-index thread))
              (:page-index thread)))]
    (cond 
     (and (:date thread) (:page-index thread))
     (str (:date thread) "; " (print-index thread))
     (:date thread)
     (:date thread)
     (:page-index thread)
     (print-index thread))))
             
(em/deftemplate thread-stream :compiled "templates/thread-stream.html" 
  [threads target]
  [:.expand-all-trigger]
  (mk/do-when (:xpnd target)
    (em/set-attr :src (str io/*file-base* "empty.png")))
  [:.group-line]
  (mk/remove-when (not (:chain target)))
  [:.thread-line]
  (mk/clone-for [th threads]
                [:.thread-line]
                #(cond (:onwatch th) ((em/set-attr :onwatch "onwatch") %)
                       :else %)
                [:.thread-control]
                (em/remove-attr :style)
                [:.control-buttons]
                (mk/remove-when (:error th))
                [:.inline-view-trigger]
                (mk/remove-when (:error th))
                [:.iv-border]
                (mk/remove-when (:error th))
                [:.reply-btn]
                (mk/remove-when (or (:error th) (not io/*addon*) (base/*posting-not-impl* (:trade target))))
                [:.expand-trigger]
                (em/set-attr :onclick (str "frontend.expand_thread(\""
                                           (:internal-id th)
                                           "\")"))
                [:.thread-header]
                (em/do->
                 (em/remove-attr :style)
                 (em/set-attr :onclick (str "if (event.target == this) frontend.expand_thread(\""
                                            (:internal-id th)
                                            "\")")))
                [:.watch-trigger-disabled]
                (fn [node]
                  (if (:onwatch th)
                    ((em/do->
                      (em/set-attr :class "watch-trigger-enabled")
                      (em/set-attr :title "Unwatch thread")
                      (em/set-attr :onwatch "onwatch")
                      (em/html-content "&#x2605;"))
                     node)
                    node))
                [:.lazy-load-trigger]
                (mk/do-when (and (:onwatch th) (not (:post-delta th)))
                         (em/set-attr :src (str io/*file-base* "empty.png")))
                [:.service-pane]
                (fn [node]
                  (if (:onwatch th)
                    ((em/html-content
                      (str "<span class=\""
                           (if (:post-delta th) "delta-posts" "delta-posts-lazy")
                           "\""
                           (when (not (:post-delta th))
                             "onclick=\"frontend.lazy_get_watch(this)\"")
                           "title=\"Delta posts from the last visit\">&#x2206; ["
                           (if (:post-delta th) (:post-delta th) "?")
                           "]</span>"))
                     node)
                    node))
                [:.thread-control]
                (fn [node]
                  (cond (:onwatch th)
                        ((em/set-attr :class "thread-control onwatch") node)
                        (:boundary th)
                        ((em/set-attr :class "thread-control read") node)
                        (:pre-boundary th)
                        ((em/set-attr :class "thread-oppost before-read") node)
                        :else node))
                [:.thread-oppost]
                (em/do->
                 (em/remove-attr :style)
                 (em/set-attr :id (:internal-id th))
                 (fn [node]
                   (cond (:onwatch th)
                         ((em/set-attr :class "thread-oppost onwatch") node)
                         (:boundary th)
                         ((em/set-attr :class "thread-oppost read") node)
                         (:pre-boundary th)
                         ((em/set-attr :class "thread-oppost before-read") node)
                         :else node)))
                [:.flag]
                (mk/do-when (:flag th)
                         (em/do->
                          (em/set-attr :class (if (:fourchan target) 
                                                (first (:flag th)) 
                                                "flag")) 
                          ;(em/set-attr :src (first (:flag th)))
                          (fn [node]
                            (if (not (:fourchan target))
                              ((em/set-attr :src (first (:flag th))) node)
                              node))
                          (em/set-attr :title (second (:flag th)))))
                [:.thread-no]
                (em/do->
                 (em/set-attr :href (:link th))
                 (em/set-attr :title (get-label th))
                 (em/content (:id th)))
                [:.delta-link]
                (mk/do-when (:onwatch th)
                         (em/set-attr :href (str (:link th) "#"
                                                 (when (:fourchan target) "p")
                                                 (:last-id th))))
                [:.iv-delta]
                (mk/do-when (:onwatch th)
                         #(if (and (:post-delta th) (> (:post-delta th) 0))
                            %
                            ((em/set-attr :style "color: #757575") %)))
                [:.post-count]
                (em/content (str "[" (:post-count th) "]"))
                [:.thread-title]
                (em/html-content (:title th))
                [:.thread-oppost :> :.post-container :> :.image-container :>
                 :.image-link :> :.post-image]
                (mk/do-when (and (not (:force-text target)) (:thumb th))
                         (em/set-attr :src (:thumb th)))
                [:.thread-oppost :> :.post-container :> :.image-container :> :.image-link]
                (mk/do-when (and (not (:force-text target)) (:thumb th))
                         (em/do->
                          (em/set-attr :href (:image th))
                          (em/set-attr :onclick (str "return frontend.expand_image(this,"
                                                     (first (:image-size th)) ","
                                                     (second (:image-size th)) ")"))))
                ;; [:.thread-oppost :> :.image-container]
                ;; (mk/remove-when (not (:force-text target)))
                [:.header-exp]
                (fn [node] ;; TODO: replace with macro
                  (if (:expanded th)
                    ((em/do->
                      (em/html-content "&laquo;") 
                      (em/set-attr :expanded_ "true")) node)
                    node))
                [:.oppost-text]
                (em/do->
                 (fn [node]
                   (if (:expanded th)
                     ((em/set-attr :style "max-height: none;") node)
                     node))
                 (em/html-content (:text th))
                 (em/set-attr :class (if (and (not (:force-text target)) (:image th))
                                       "image-indent oppost-text"
                                       "text-indent oppost-text")))
                [:.replies]
                (fn [node]
                  (if (:expanded th)
                    ((em/set-attr :style "display: block;") node)
                    node))
                [:.replies :> :.image-indent]
                (fn [node]
                  (if (or (:force-text target) (not (:image th)))
                    ((em/set-attr :class "text-indent") node)
                    node))
                [:.reply]
                (mk/do-when (and (seq (:children th)) (not (:hdlns th)))
                         (inject-replies th target))))

(em/defsnippet image-stream :compiled "templates/image-stream.html" [:#image-stream]
  [images target]
  [:.stream-image-container]
  (mk/clone-for [im images]
                (em/do->
                 (em/remove-attr :style)
                 (fn [node]
                   (em/at node
                          [:.image-thread-links]
                          (em/do->
                           (em/remove-attr :style)
                           (mk/remove-when (not (:id im))))
                          [:.thread-no]
                          (em/do->
                           (em/set-attr :href (:link im))
                           (em/content (:id im)))
                          [:.stream-image]
                          (em/set-attr :src (:thumb im))
                          [:.load-indicator]
                          (em/remove-node)
                          [:.image-link]
                          (em/do->
                           (em/set-attr :href (:image im))
                           (em/set-attr :onclick (str "return frontend.expand_image(this,"
                                                      (first (:image-size im)) ","
                                                      (second (:image-size im)) ")"))))))))

(defn threads [data target]
  (if (:img target)
    (image-stream data target)
    (thread-stream data target)))

(em/deftemplate frontend :compiled "templates/frontend.html" [])

(em/deftemplate settings :compiled "templates/settings.html" [])

(em/deftemplate inline-watch-stream :compiled "templates/watch.html" [])

(em/deftemplate inline-image-stream :compiled "templates/images.html" [])

(em/deftemplate manual :compiled "templates/manual.html" [])

(em/deftemplate urlbar :compiled "templates/urlbar.html" [])

(em/deftemplate video :compiled "templates/video-stream.html" [])