(ns kuroi.plugins.chan-2ch-hk
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "2ch.hk")

(defmethod pp/json? +trademark+ [target]
  true)

(defmethod pp/paginate +trademark+ [n target]
  (str (:target target) "/"
      (if (zero? n)
        "index.json"
        (str n ".json"))))

(defmethod pp/meta-page-url +trademark+ [target]
  (str (:target target) "/" "index.html"))

(defmethod pp/get-json-thread-id +trademark+ [thread target]
  (thread "thread_num"))

(defmethod pp/get-json-post-images +trademark+ [post target]
  (post "files"))

(defmethod pp/get-oppost +trademark+ [thread target]
  (first (thread "posts")))

(defmethod pp/get-omitted-posts +trademark+ [thread target]
  (if (and (:multiple (meta target)) (< 3 (count (thread "posts"))))
    (inc (thread "posts_count"))))

; actually gets post id (for compatibility with html code)
(defmethod pp/get-thread-id +trademark+ [thread target]
  (thread "num"))

(defmethod pp/thread-url +trademark+ [thread-id target]
  (str (pp/get-scheme target) (:forum target) "/res/" thread-id ".json"))

(defmethod pp/html-thread-url +trademark+ [thread-id target]
  (str (pp/get-scheme target) (:forum target) "/res/" thread-id ".html"))

(defmethod pp/parse-post +trademark+ [root-node data target]
  (let [post-id (root-node "num")
       post-text (pp/fix-links (pp/sanitize (root-node "comment")) target)
       image (first (root-node "files"))
       flag (root-node "icon")]
      (merge data
             {:id (str post-id)
              :internal-id (str (:prefix target) post-id)
              :date (root-node "date")
              :link (str (pp/get-scheme target) (:forum target) "/res/" (or (:parent-id data) post-id) ".html")
              :thumb (when image (pp/fix-url (image "thumbnail") target))
              :image (when image (pp/fix-url (image "path") target))
              :image-size (if image [(image "width") (image "height")] [0 0])
              :text (pp/check-reflinks post-id (:parent-id data) post-text target)
              :flag (when flag [(pp/fix-url (get (re-find #"src=\"([^\"]*)\"" flag) 1) target)
                                ""])
              })))

(defmethod pp/json->image +trademark+ [thread-id image target]
  (let [image-link (pp/fix-url (image "path") target)]
      {:id thread-id
       :internal-id image-link
       :link (str/replace (pp/thread-url thread-id target) ".json" ".html")
       :thumb (pp/fix-url (image "thumbnail") target)
       :image image-link
       :image-size [(image "width") (image "height")]
       }))

(defmethod pp/parse-images +trademark+ [doc-tree target]
  (pp/parse-images-json doc-tree target))

(defmethod pp/parse-thread-images +trademark+ [doc-tree target]
  (pp/parse-images-json doc-tree target))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
  (pp/parse-json doc-tree target))

(defmethod pp/get-post-form +trademark+ [dom target]
  (pp/select dom "form#postform"))

(defmethod pp/extract-navbar +trademark+ [dom target]
  (pp/extract-navbar-menu dom "#fmenu" target))