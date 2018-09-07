(ns kuroi.plugins.chan-dobrochan-ru
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "dobrochan.ru")

(defmethod pp/get-page-ext +trademark+ [target]
  "xhtml")

(defmethod pp/paginate +trademark+ [n target]
  (str (:target target) "/"
      (if (zero? n)
        (str "index." (pp/get-ext target))
        (str n "." (pp/get-ext target)))))

(defmethod pp/get-thread-id +trademark+ [root-node target]
  (let [elt (pp/select root-node "a[name]")]
       (.substring (.-name elt) 1)))

(defmethod pp/get-summary-node +trademark+ [thread target]
  (if-let [omitted (pp/select thread "div.abbrev > span:last-of-type")]
         (if (re-find #"\d+" (.-textContent omitted))
           omitted
           0)
         0))

;; (defmethod parse-thread-images +trademark+ [doc-tree target]
;;   (let [images (select doc-tree {[:.fileinfo] [:.file]})]
;;     (for [filesz images]
;;       (make-image-headline nil (first (select filesz [:img])) (first (select filesz [:a])) filesz target))))

(defmethod pp/extract-navbar +trademark+ [dom target]
  (when-let [navbar-elt (pp/select dom ".adminbar")]
           (pp/transform-navbar dom target (seq (.-childNodes navbar-elt))
                             #(when-let [href (.getAttribute % "href")]
                                        (re-matches #"/[a-zA-Z0-9-]+/index.xhtml" href))
                             #(let [l (.getAttribute % "href")]
                                   (set! (.-href %) (str "?front&url=" (:scheme target) (:domain target)
                                                         (.substring l 0 (.lastIndexOf l "/"))))
                                   %))))

(defmethod pp/get-captcha +trademark+ [url parent target callback]
  (let [link (let [[_ board] (re-find #"dobrochan\.ru/([^/]*)/.*" url)]
                 (str "http://dobrochan.ru/captcha/" board "/" (.getTime (new js/Date)) ".png"))]
      (callback {:link link})))

(defmethod pp/get-post-error +trademark+ [response target]
  (pp/find-post-error ".post-error" response target))

(defmethod pp/adjust-form +trademark+ [form target]
  (set! (.-__parent_key__ form) "thread_id")
  form)
