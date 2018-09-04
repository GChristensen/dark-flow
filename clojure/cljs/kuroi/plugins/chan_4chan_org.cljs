(ns kuroi.plugins.chan-4chan-org
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "4chan.org")

(defmethod pp/json? +trademark+ [target]
  true)

(defmethod pp/paginate +trademark+ [n target]
  (str "https://a.4cdn.org/" (:board target) "/" (inc n) ".json"))

(defmethod pp/meta-page-url +trademark+ [target]
  (:target target))

(defmethod pp/thread-url +trademark+ [thread-id target]
  (str "https://a.4cdn.org/" (:board target) "/thread/" thread-id ".json"))

(defmethod pp/html-thread-url +trademark+ [thread-id target]
  (str "https://a.4cdn.org/" (:board target) "/thread/" thread-id ".html"))

(defmethod pp/get-thread-id +trademark+ [root-node target]
  (root-node "no"))

(defmethod pp/get-json-thread-id +trademark+ [thread target]
  (let [oppost (pp/get-oppost thread target)]
      (when (and (:multiple (meta target)) oppost)
            (oppost "no"))))

(defmethod pp/get-omitted-posts +trademark+ [thread target]
  (let [oppost (first (thread "posts"))]
      (if (and (:multiple (meta target)) oppost (oppost "omitted_posts"))
        (inc (oppost "omitted_posts")))))

(defmethod pp/get-oppost +trademark+ [thread target]
  (first (thread "posts")))

(defmethod pp/get-json-post-images +trademark+ [post target]
  (when (post "filename")
     [post]))

(defmethod pp/json->image +trademark+ [thread-id image target]
  (let [image-link (pp/fix-url (image "path") target)]
      {:id thread-id
       :link (str "http://boards." (:trade target) "/"
                  (:board target) "/thread/" thread-id)
       :internal-id image-link
       :thumb (when image (str "https://t.4cdn.org/" (:board target) "/"
                               (image "tim") "s.jpg"))
       :image (when image (str "https://i.4cdn.org/" (:board target) "/"
                               (image "tim") (image "ext")))
       :image-size (if image [(image "w") (image "h")] [0 0])
       }))

(defmethod pp/parse-images +trademark+ [doc-tree target]
  (pp/parse-images-json doc-tree target))

(defmethod pp/parse-thread-images +trademark+ [doc-tree target]
 (parse-images-json doc-tree target))

(defmethod pp/parse-post +trademark+ [root-node data target]
  (let [post-id (root-node "no")
       post-text (root-node "com")
       post-text (when post-text (pp/fix-links (pp/sanitize post-text) target))
       image (when (root-node "filename") root-node)
       flag (root-node "country")]
      (merge data
             {:id (str post-id)
              :internal-id (str (:prefix target) post-id)
              :date (root-node "now")
              :link (str "https://boards.4chan.org/" (:board target) "/thread/"
                         (or (:parent-id data) post-id))
              :thumb (when image (str "https://t.4cdn.org/" (:board target) "/"
                                      (image "tim") "s.jpg"))
              :image (when image (str "https://i.4cdn.org/" (:board target) "/"
                                      (image "tim") (image "ext")))
              :image-size (if image [(image "w") (image "h")] [0 0])
              :text (when post-text (pp/check-reflinks post-id (:parent-id data) post-text target))
              :flag (when flag [(str "flag flag-" (str/lower-case flag))
                                (root-node "country_name")])
              })))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
  (pp/parse-json doc-tree target))

(defmethod pp/transform-page-text +trademark+ [text target]
  (if (not (.startsWith text "{\"threads\":["))
   (str "{\"threads\":[" text "]}")
   text))

(defmethod pp/extract-navbar +trademark+ [dom target]
  (let [navbar-elt (pp/select dom "#boardNavDesktop > .boardList")]
      (pp/transform-navbar dom target (seq (.-childNodes navbar-elt))
                        ;#(re-matches #".*\.org/..*" (.getAttribute % "href"))
                        #(re-matches #"/[^/]+/" (.getAttribute % "href"))
                        #(do
                           (a-> href % (str (:orig-scheme target) (:trade target) (.getAttribute % "href") ))
                           %))))

;(defmethod get-post-error +trademark+ [response target]
;  (find-post-error "#errmsg" response target))

(defmethod pp/adjust-form +trademark+ [form target]
  (set! (.-__parent_key__ form) "resto")
  (set! (.-__is_iframe__ form) true)
  (set! (.-__set_width__ form) "545px")
  (set! (.-__set_height__ form) "537px")
  (set! (.-innerHTML form) (str "<iframe style=\"height:510px; display: none;\"/>"))
  form)