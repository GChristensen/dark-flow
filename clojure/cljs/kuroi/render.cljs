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
   )
  (:require-macros [kuroi.macros :as mk] 
                   [enfocus.macros :as em]))

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
        ((ef/do-> ; equilibristics to access the root node (.thread-line) through macros
          (ef/set-attr :class "group-line")
          (fn [node]
            (ef/at node
                   [:.watch-thread-control]
                   (ef/do->
                    (ef/remove-attr :style)
                    (ef/set-attr :colspan "3")
                    (ef/set-attr :class "thread-group")
                    (ef/content th))
                   [:.watch-image-cell] (ef/remove-node)
                   [:.thread-oppost] (ef/remove-node)))) node)
        (ef/at node
               [:.watch-thread-control]
               (ef/remove-attr :style)
               [:.watch-thread-header]
               (ef/remove-attr :style)
               [:.watch-image-cell]
               (ef/do->
                (ef/remove-attr :style)
                (mk/remove-when (:force-text target)))
               [:.watch-image]
               (mk/do-when (and (:image th) (not (:force-text target)))
                 (ef/set-attr :src (:thumb th)))
               [:.image-link]
               (mk/do-when (and (:image th) (not (:force-text target)))
                 (ef/do->
                  (ef/set-attr :href (:image th))
                  (ef/set-attr :onclick (str "return frontend.expand_image(this,"
                                             (first (:image-size th)) ","
                                             (second (:image-size th)) ")"))))
               [:.watch-image-container]
               (mk/do-when (not (:force-text target))
                  #(if (:image th)
                    %
                    ((ef/html-content "<span class=\"noimage\">No image</span>") %)))
               [:.thread-oppost]
               (ef/do->
                (ef/remove-attr :style)
                (ef/set-attr :id (:internal-id th)))
               [:.thread-no]
               (ef/do->
                (ef/set-attr :title (:date th))
                (ef/set-attr :href (:link th))
                (ef/content (str (:id th))))
               [:.delta-link]
               (ef/set-attr :href (str (:link th) "#" (when (:fourchan th) "p") (:last-id th)))
               [:.post-count]
               (ef/content (str "[" (:post-count th) "]"))
               [:.thread-title]
               (ef/html-content (:title th))
               [:.lazy-load-trigger]
               (ef/set-attr :src (str io/*file-base* "empty.png"))
               ;; [:.lazy-load-trigger]
               ;; (fn [node]
               ;;   (when (:always-calc-delta target)
               ;;     node))
               [:.watch-oppost-text]
               (ef/html-content (flatten-markup (:text th))))))))


(defn inject-replies [root-node thread target &{:keys [word-filter] :or {word-filter true}}]
  (let [settings (settings/get-for target)
        replies (if (and (:wf-enabled settings) word-filter)
                  (:children (fltr/filter-replies (:wf-post-parsed settings) thread target))
                  (:children thread))]
  (fn [node]
    ((mk/clone-for-live-node root-node 10 [rep replies]
                [:.post-container]
                (ef/set-attr :data-thread-id (:id thread))
                [:.reply-header]
                (ef/remove-attr :style)
                [:.flag]
                (mk/do-when (:flag rep)
                   (ef/do->
                     (ef/set-attr :class (if (:fourchan target) 
                                           (first (:flag rep)) 
                                           "flag")) 
                     ;(ef/set-attr :src (first (:flag rep)))
                     (fn [node]
                       (if (not (:fourchan target))
                         ((ef/set-attr :src (first (:flag rep))) node)
                         ((ef/remove-attr :src) node)))
                     (ef/set-attr :title (second (:flag rep)))))
                [:.reply-no]
                (ef/do->
                 (ef/set-attr :title (:date rep))
                 (ef/set-attr :href (str (:link rep) "#" (when (:fourchan target) "p") (:id rep)))
                 (ef/content (str (:id rep))))
                [:.reply-ord]
                (ef/content (str (:post-num rep)))
                [:.reply-title]
                (ef/html-content (:title rep))
                [:.post-image]
                (mk/do-when (and (not (:force-text target)) (:thumb rep))
                  (ef/set-attr :src (:thumb rep)))
                [:.image-link]
                (mk/do-when (and (not (:force-text target)) (:image rep))
                  (ef/do->
                   (ef/set-attr :href (:image rep))
                   (ef/set-attr :onclick (str "return frontend.expand_image(this,"
                                           (first (:image-size rep)) ","
                                           (second (:image-size rep)) ")"))))
                [:.image-container]
                (mk/remove-when (:force-text target))
                [:.post-text]
                (ef/do->
                 (ef/html-content (:text rep))
                 (ef/set-attr :class (if (and (not (:force-text target)) (:image rep))
                                    "image-indent post-text"
                                    "text-indent post-text")))
                [:.crossref-container]
                (mk/remove-when (not (seq (:refs rep))))
                [:.crossref-wrap]
                (em/clone-for [ref (:refs rep)]
                              [:.crossref]
                              (ef/do-> 
                               (ef/set-attr :href (str (:link rep) "#"
                                                       (when (:fourchan target) "p") ref))
                               (ef/set-attr :onmouseover (str "frontend.show_popup(event, '"
                                                              ref  "', true)"))
                               (ef/set-attr :onmouseout (str "frontend.show_popup(event, '"
                                                             ref  "', false)"))
                               (ef/html-content (str "&gt;&gt;" ref)))))
     node))))


(em/defsnippet replies :compiled "templates/thread-stream.html" [:.replies]
  [thread target &{:keys [word-filter fallback?] :or {word-filter true}}]

  [:.expand-trigger]
  (ef/set-attr :onclick (str "frontend.expand_thread(\""
                          (:internal-id thread)
                          "\")"))
  [:.image-indent]
  (ef/do->
   (fn [node]
     (if (:force-text target)
       ((ef/set-attr :class "text-indent") node)
       node))
   (fn [node]
     (ef/at node
           [:.reply]
           (mk/do-when (and (seq (:children thread)) (not (:hdlns thread)))
                 (inject-replies (when (not fallback?) node) thread target 
                                 :word-filter word-filter))))))
    

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
    (ef/set-attr :src (str io/*file-base* "empty.png")))
  [:.group-line]
  (mk/remove-when (not (:chain target)))
  [:tbody]
   (fn [node]
     (ef/at node
       [:.thread-line]
          (mk/clone-for-live-node node 10 [th threads]
                [:.thread-line]
                #(cond (:onwatch th) ((ef/set-attr :onwatch "onwatch") %)
                       :else %)
                [:.thread-control]
                (ef/remove-attr :style)
                [:.control-buttons]
                (mk/remove-when (:error th))
                [:.inline-view-trigger]
                (mk/remove-when (:error th))
                [:.iv-border]
                (mk/remove-when (:error th))
                [:.reply-btn]
                (mk/remove-when (or (:error th) (not io/*addon*) (base/*posting-not-impl* (:trade target))))
                [:.expand-trigger]
                (ef/set-attr :onclick (str "frontend.expand_thread(\""
                                           (:internal-id th)
                                           "\")"))
                [:.post-container]
                (ef/set-attr :data-thread-id (:id th))
                [:.thread-header]
                (ef/do->
                 (ef/remove-attr :style)
                 (ef/set-attr :onclick (str "if (event.target == this) frontend.expand_thread(\""
                                            (:internal-id th)
                                            "\")")))
                [:.watch-trigger-disabled]
                (fn [node]
                  (if (:onwatch th)
                    ((ef/do->
                      (ef/set-attr :class "watch-trigger-enabled")
                      (ef/set-attr :title "Unwatch thread")
                      (ef/set-attr :onwatch "onwatch")
                      (ef/html-content "&#x2605;"))
                     node)
                    node))
                [:.lazy-load-trigger]
                (mk/do-when (and (:onwatch th) (not (:post-delta th)))
                         (ef/set-attr :src (str io/*file-base* "empty.png")))
                [:.service-pane]
                (fn [node]
                  (if (:onwatch th)
                    ((ef/html-content
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
                        ((ef/set-attr :class "thread-control onwatch") node)
                        (:boundary th)
                        ((ef/set-attr :class "thread-control read") node)
                        (:pre-boundary th)
                        ((ef/set-attr :class "thread-oppost before-read") node)
                        :else node))
                [:.thread-oppost]
                (ef/do->
                 (ef/remove-attr :style)
                 (ef/set-attr :id (:internal-id th))
                 (fn [node]
                   (cond (:onwatch th)
                         ((ef/set-attr :class "thread-oppost onwatch") node)
                         (:boundary th)
                         ((ef/set-attr :class "thread-oppost read") node)
                         (:pre-boundary th)
                         ((ef/set-attr :class "thread-oppost before-read") node)
                         :else node)))
                [:.flag]
                (mk/do-when (:flag th)
                         (ef/do->
                          (ef/set-attr :class (if (:fourchan target) 
                                                (first (:flag th)) 
                                                "flag")) 
                          ;(ef/set-attr :src (first (:flag th)))
                          (fn [node]
                            (if (not (:fourchan target))
                              ((ef/set-attr :src (first (:flag th))) node)
                              ((ef/remove-attr :src) node)))
                          (ef/set-attr :title (second (:flag th)))))
                [:.thread-no]
                (ef/do->
                 (ef/set-attr :href (:link th))
                 (ef/set-attr :title (get-label th))
                 (ef/content (str (:id th))))
                [:.delta-link]
                (mk/do-when (:onwatch th)
                         (ef/set-attr :href (str (:link th) "#"
                                                 (when (:fourchan target) "p")
                                                 (:last-id th))))
                [:.iv-delta]
                (mk/do-when (:onwatch th)
                         #(if (and (:post-delta th) (> (:post-delta th) 0))
                            %
                            ((ef/set-attr :style "color: #757575") %)))
                [:.post-count]
                (ef/content (str "[" (:post-count th) "]"))
                [:.thread-title]
                (ef/html-content (:title th))
                [:.thread-oppost :> :.post-container :> :.image-container :>
                 :.image-link :> :.post-image]
                (mk/do-when (and (not (:force-text target)) (:thumb th))
                         (ef/set-attr :src (:thumb th)))
                [:.thread-oppost :> :.post-container :> :.image-container :> :.image-link]
                (mk/do-when (and (not (:force-text target)) (:thumb th))
                         (ef/do->
                          (ef/set-attr :href (:image th))
                          (ef/set-attr :onclick (str "return frontend.expand_image(this,"
                                                     (first (:image-size th)) ","
                                                     (second (:image-size th)) ")"))))
                ;; [:.thread-oppost :> :.image-container]
                ;; (mk/remove-when (not (:force-text target)))
                [:.header-exp]
                (fn [node] ;; TODO: replace with macro
                  (if (:expanded th)
                    ((ef/do->
                      (ef/html-content "&laquo;") 
                      (ef/set-attr :expanded_ "true")) node)
                    node))
                [:.oppost-text]
                (ef/do->
                 (fn [node]
                   (if (:expanded th)
                     ((ef/set-attr :style "max-height: none;") node)
                     node))
                 (ef/html-content (:text th))
                 (ef/set-attr :class (if (and (not (:force-text target)) (:image th))
                                       "image-indent oppost-text"
                                       "text-indent oppost-text")))
                [:.replies]
                (fn [node]
                  (if (:expanded th)
                    ((ef/set-attr :style "display: block;") node)
                    node))
                [:.replies :> :.image-indent]
                (fn [node]
                  (if (or (:force-text target) (not (:image th)))
                    ((ef/set-attr :class "text-indent") node)
                    node))
                [:.reply]
                (mk/do-when (and (seq (:children th)) (not (:hdlns th)))
                         (inject-replies nil th target))))))


(em/defsnippet image-stream :compiled "templates/image-stream.html" [:#image-stream]
  [images target]
  [:#image-stream]
   (fn [node]
     (ef/at node
       [:.stream-image-container]
       (mk/clone-for-live-node node 5 [im images]
                (ef/do->
                 (ef/remove-attr :style)
                 (fn [node]
                   (ef/at node
                          [:.image-thread-links]
                          (ef/do->
                           (ef/remove-attr :style)
                           (mk/remove-when (not (:id im))))
                          [:.thread-no]
                          (ef/do->
                           (ef/set-attr :href (:link im))
                           (ef/content (str (:id im))))
                          [:.stream-image]
                          (ef/set-attr :src (:thumb im))
                          [:.load-indicator]
                          (ef/remove-node)
                          [:.image-link]
                          (ef/do->
                           (ef/set-attr :href (:image im))
                           (ef/set-attr :onclick (str "return frontend.expand_image(this,"
                                                      (first (:image-size im)) ","
                                                      (second (:image-size im)) ")"))))))))))


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