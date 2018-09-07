(ns kuroi.plugins.chan-krautchan
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

;; rework needed

(def ^:const +trademark+ "krautchan.co")

(defmethod pp/paginate +trademark+ [n target]
  (let [n (inc n)]
    (str (:target target) "/"
        (if (= n 1)
          ""
          (str n "." (pp/get-ext target))))))

(defmethod pp/thread-url +trademark+ [thread-id target]
  (str (pp/get-scheme target) (:forum target) "/thread-" thread-id "." (pp/get-ext target)))

(defmethod pp/get-thread-id +trademark+ [root-node target]
  (let [elt (pp/select root-node ".intro > .post_no")]
      (.-textContent elt)))

(defmethod pp/get-oppost +trademark+ [thread target]
  (pp/select thread ".op"))

(defmethod pp/parse-post +trademark+ [root-node data target]
  (let [thread-id (pp/get-thread-id root-node target)
       flag-elt (pp/select root-node ".postheader > img[src*='ball']")
       flag (when flag-elt [(pp/fix-url (.-src flag-elt) target)
                            (get (re-find #"'([^']+)'"
                                          (.-value (.getAttributeNode flag-elt
                                                                      "onmouseover")))
                                 1)])
       title-elt (pp/select root-node ".postheader > span.postsubject")
       date-elt (pp/select root-node "time")
       date (.-datetime date-elt)
       image-link (pp/select root-node ".file_thread > a[target='_blank'],
                            .file_reply > a[target='_blank']")
       thumb-img (when image-link (pp/select image-link "img"))
       filesize (pp/select root-node ".file_thread > .fileinfo,
                          .file_reply > .fileinfo")
       post-text (pp/select root-node "blockquote")]
      (assoc (pp/build-post-data data thread-id date title-elt thumb-img
                              image-link filesize post-text target)
             :flag flag)))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
           (.log js/console (count (pp/select* doc-tree "div.thread")))
  (pp/parse-structured (pp/select* doc-tree "div.thread") ".reply" target))

(defmethod pp/parse-images +trademark+ [doc-tree target]
           [])

(defmethod pp/get-post-error +trademark+ [response target])
