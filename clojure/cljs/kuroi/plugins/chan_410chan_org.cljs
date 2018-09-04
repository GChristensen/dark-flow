(ns kuroi.plugins.chan-410chan-org
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "410chan.org")

(defmethod pp/parse-post +trademark+ [root-node data target]
  (let [data (pp/parse-post-generic root-node data target)
       flag-elt (pp/select root-node "span.country > img")
       flag (when flag-elt
                  [(pp/fix-url (.-src flag-elt) target)
                   (.-alt flag-elt)])]
      (assoc data :flag flag)))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
  (pp/parse-structured (pp/select* doc-tree ".thrdcntnr") ".reply" target))

(defmethod pp/get-captcha +trademark+ [url parent target callback]
  (let [link (let [[_ board] (re-find #"410chan\.org/([^/]*)/.*" url)]
                 (str "http://410chan.org/faptcha.php?board=" board))]
      (callback {:link (str link "#i" (.random js/Math))})))

(defmethod pp/get-post-error +trademark+ [response target]
  (pp/find-post-error "h2" response target))

(defmethod pp/adjust-form +trademark+ [form target]
  (set! (.-__parent_key__ form) "replythread")
  (doseq [sm (pp/select* form "small")]
        (.removeChild (.-parentNode sm) sm))
  form)