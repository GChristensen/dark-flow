(ns kuroi.plugins.bb-rutracker-org
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

;; work in progress

(def ^:const +trademark+ "piracy.gov")

(defmethod pp/paginate +trademark+ [n target]
   (str "https://" (:rewrite target) "&start=" (* n 50)))

(defmethod pp/thread-url +trademark+ [thread-id target]
  (str "https://rutracker.org/forum/viewtopic.php?t=" thread-id))

(defmethod pp/meta-page-url +trademark+ [_ _])

(defmethod pp/get-thread-id +trademark+ [root-node target]
           (when root-node
                 (.getAttribute root-node "data-topic_id")))

(defmethod pp/get-oppost +trademark+ [thread target]
           thread)

(defmethod pp/parse-post +trademark+ [root-node data target] ;.torTopic
   (pp/build-post-data data (pp/get-thread-id root-node target) nil (pp/select root-node "a.torTopic")
                            nil nil nil nil target)
   )

(defn parse-thread-post [doc-tree root-node data target]
  (let [thread-id (get (.split (.getAttribute root-node "id") "_") 1)
        date-elt (pp/select root-node ".post-time a")
        date-text (when date-elt (.-textContent date-elt))
        date-val (when date-text (str/trim date-text))
        title-elt nil
        thumb-img (pp/select root-node ".postImg")
        image-link (pp/select root-node ".postImg")
        filesize nil
        post-text (pp/select root-node ".post_body")]
       (when thumb-img (.setAttribute thumb-img "src" (.getAttribute thumb-img "title")))
       (when image-link (.setAttribute image-link "href" (.getAttribute thumb-img "title")))
       (pp/build-post-data data thread-id date-val title-elt thumb-img image-link
                        filesize post-text target)))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
  (if (pp/exists-in doc-tree "table#topic_main")
    (let [data (pp/cons-post-data :omitted 0
                                  :page-index (:page-index (meta target))
                                  :oppost true)
          oppost (parse-thread-post doc-tree (pp/select doc-tree "#topic_main tbody[id]") data target)
          replies (loop [replies (take 4 (pp/select* doc-tree "#topic_main tbody[id]")) num 1 out  []]
                        (let [reply (first replies)]
                             (if reply
                               (let [data (pp/cons-post-data :parent-id (:id oppost)
                                                          :post-num num)
                                     spoilers (pp/select* reply ".sp-wrap")
                                     hrs (pp/select* reply "hr")]
                                    (doseq [s spoilers] (.removeChild (.-parentNode s) s))
                                    (doseq [h hrs] (.setAttribute h "style" "visibility: hidden"))
                                    (recur (next replies)
                                           (inc num)
                                           (conj out (parse-thread-post doc-tree reply data target))))
                               out)))]
         [(assoc oppost :children replies :post-count (count replies) :completion true
                 :id (get (.split (.getAttribute (pp/select doc-tree "#topic-title") "href") "=") 1))])

    (for [thread (pp/select* doc-tree ".hl-tr")]
      (assoc
          (pp/parse-post thread
                         (pp/cons-post-data :omitted 0
                                            :-headline true
                                            :page-index (:page-index (meta target))
                                            :oppost true)
                         target)
          :incomplete true))))
