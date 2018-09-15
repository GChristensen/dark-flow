(ns kuroi.plugins.bb-common-ru
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str]
    [goog.dom :as dom])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

;; work in progress

; TODO: scrap search
;https://rutracker.org/forum/tracker.php?nm=%s
;https://rutracker.org/forum/tracker.php?f=7&nm=%s

(defn paginate [n target]
  (str "https://" (:rewrite target) "&start=" (* n 50)))

(defn thread-url [thread-id target]
  (str "https://" (get (.split (.replace (:rewrite target) "viewforum.php?f" "viewtopic.php?t") "=") 0) "=" thread-id))

(defn parse-post [root-node data target selector]
  (pp/build-post-data data (pp/get-thread-id root-node target) nil (pp/select root-node selector)
                      nil nil nil nil target))

(defn parse-thread-post [doc-tree root-node data target]
      (let [thread-id (get (.split (.getAttribute root-node "id") "_") 1)
            date-elt (pp/select root-node ".post-time a")
            date-text (when date-elt (.-textContent date-elt))
            date-val (when date-text (str/trim date-text))
            title-elt nil
            thumb-img (pp/select root-node ".postImg")
            image-link (pp/select root-node ".postImg")
            filesize nil
            post-text (pp/select root-node ".post_body")
            post-text (if (== (:post-num data) 1)
                        (let [magnet (pp/select root-node ".dl-link, .magnet-link")
                              magnet (when magnet (.cloneNode magnet true))
                              forms (pp/select* root-node "form")
                              attach (pp/select root-node ".attach")
                              spoilers (pp/select* root-node ".sp-wrap")
                              images (pp/select* root-node ".sp-wrap .postLink")
                              hrs (pp/select* root-node "hr")]
                             (doseq [h hrs] (.setAttribute h "style" "visibility: hidden"))
                             (doseq [f forms]
                                    (dom/removeNode f))
                             (doseq [im (take 20 images)]
                               (let [var (pp/select im "var")]
                                 (when var
                                    (set! (.-innerHTML im) (str "<img src='" (.-title var) "'/>"))
                                    (dom/removeNode im)
                                    (.appendChild post-text im))))
                             (doseq [s spoilers] (dom/removeNode s))
                             (when magnet
                               (let [href (.-href magnet)]
                                  (when (not (.startsWith href "magnet:"))
                                        (let [prefix (str "https://" (.substring (:rewrite target) 0 (.lastIndexOf (:rewrite target) "/")))
                                              suffix (.substring href (.lastIndexOf href "/"))]
                                        (set! (.-href magnet) (str prefix suffix)))))
                                   (set! (.-textContent magnet) "[MAGNET]")
                                   (when (.-firstChild post-text)
                                         (.insertBefore post-text magnet (.-firstChild post-text))))
                             (when attach (dom/removeNode attach))
                             post-text)
                        post-text)
            colored (pp/select* root-node (str "*[style*='color: blue;'], *[style*='color: darkblue;'], *[style*='color: navy;'],"
                                               "*[style*='color: black;'], *[style*='color: indigo;'], *[style*='color: red;'],"
                                               "*[style*='color: darkred;']"))]
           (doseq [c colored]
                  (let [style (.getAttribute c "style")]
                       (.setAttribute c "style" (str/replace style #"color: [^;]+;" ""))))
           (when thumb-img (.setAttribute thumb-img "src" (.getAttribute thumb-img "title")))
           (when image-link (.setAttribute image-link "href" (.getAttribute thumb-img "title")))
           (pp/build-post-data data thread-id date-val title-elt thumb-img image-link
                               filesize post-text target)))

(defn parse-threads [doc-tree target thread-url-selector thread-selector]
           (if (pp/exists-in doc-tree "table#topic_main")
             (let [data (pp/cons-post-data :omitted 0
                                           :page-index (:page-index (meta target))
                                           :oppost true)
                   oppost (parse-thread-post doc-tree (pp/select doc-tree "#topic_main tbody[id]") data target)
                   replies (loop [replies (take 4 (pp/select* doc-tree "#topic_main tbody[id]")) num 1 out  []]
                                 (let [reply (first replies)]
                                      (if reply
                                        (let [data (pp/cons-post-data :parent-id (:id oppost)
                                                                      :post-num num)]
                                             (recur (next replies)
                                                    (inc num)
                                                    (conj out (parse-thread-post doc-tree reply data target))))
                                        out)))]
                  [(assoc oppost :children replies :post-count (count replies) :completion true
                          :id (get (.split (.getAttribute (pp/select doc-tree thread-url-selector) "href") "=") 1))])

             (let [headlines (pp/select* doc-tree "tr[id^='tr-']")
                   headlines (filter #(not (pp/select % "img[src*='sticky'], img[src*='announce']")) headlines)]
               (for [thread headlines]
                    (assoc
                      (pp/parse-post thread
                                     (pp/cons-post-data :omitted 0
                                                        :-headline true
                                                        :page-index (:page-index (meta target))
                                                        :oppost true)
                                     target)
                      :incomplete true)))))
