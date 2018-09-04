(ns kuroi.plugins.chan-iichan-hk
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "iichan.hk")

(defmethod pp/paginate +trademark+ [n target]
  (str (:target target) "/"
      (if (zero? n)
        (str "index." (pp/get-ext target))
        (str n "." (pp/get-ext target)))))

(defmethod pp/get-oppost +trademark+ [thread target]
           thread)

(defmethod pp/extract-navbar +trademark+ [dom target]
  (let [navbar-elt (pp/select dom ".adminbar")]
      (pp/transform-navbar dom target (seq (.-childNodes navbar-elt))
                        #(re-matches #"(.*\.org/..*)|(.*/\.\./..*)" (.getAttribute % "href"))
                        #(let [l (.getAttribute % "href")]
                              (set! (.-href %)
                                    (if (s-in? l "..")
                                      (str (:orig-scheme target) (:domain target)
                                           "/" (second (re-find #"\.\./(.*)/" l)))
                                      (str/replace l "http://" (:orig-scheme target))))
                              %))))

(defmethod pp/get-captcha +trademark+ [url parent target callback]
  (let [link (let [[_ board thread] (re-find #".*/([^/]*)/[^/]*/(\d+)\..*$" url)
                  script (if (#{"a" "b"} board) "captcha1.pl" "captcha.pl")]
                 (if thread
                   (str "http://iichan.hk/cgi-bin/" script "/" board "/?key=res" thread)
                   (let [[_ board] (re-find #"\.hk/([^/]*)" url)
                         script (if (#{"a" "b"} board) "captcha1.pl" "captcha.pl")]
                        (str "http://iichan.hk/cgi-bin/" script "/" board "/?key=mainpage"))))]
      (callback {:link (str link "#i" (.random js/Math))})))