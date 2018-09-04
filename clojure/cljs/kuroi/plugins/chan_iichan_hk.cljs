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
                              (set! (.-href %) (str "?front&url="
                                    (if (s-in? l "..")
                                      (str (:orig-scheme target) "://" (:domain target)
                                           "/" (second (re-find #"\.\./(.*)/" l)))
                                      (str/replace l "http://" (:orig-scheme target)))))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(intern 'kuroi.base '*iichan-hk-wf*
  {:wf-enabled true ; sneaky wordfilter for iichan.hk
   :wf-title-parsed
               #{"rpg"
                 "toho"
                 "touho"
                 "genso"
                 "rozen"
                 "poney"
                 "pony"
                 "re:start thread"
                 "stranger story"
                 "#\\.beyond\\d"
                 "#\\b\u0440\u043f\u0433\\b"
                 "\u0442\u043e\u0445\u043e"
                 "\u0433\u0435\u043d\u0441\u043e\u043a"
                 "\u043d\u0438\u043f\u0430"
                 "#\u043d\u0438\u043f(?:[\u0430\u0435\u0443\u044b]|(?:\u043e\u0439))"
                 "#\\b\u043f\u043e\u043d\u0438\\b"
                 "\u043f\u043e\u043d\u0438\u0442\u0440\u0435\u0434"
                 "\u043b\u0435\u0441\u043b\u0438"
                 "stephanie"
                 "\u043a\u0443\u043a\u043b\u043e\u0447\u0430\u043d"
                 "#\u043f\u043e\u043f\u0438\u0441[\u0430\u044f]\u043b\u0430"
                 "#[^!]!!(?:[^!]|$)"
                 "\u96a0\u3059"
                 "\u0430\u0441\u0442\u043e\u043f\u043e\u043c \u043f\u043e \u0433\u0430\u043b\u0430\u043a\u0442\u0438\u043a\u0435"
                 "\u0430\u0432\u0430\u043b\u043e\u043d:"
                 "#\u0443\u0440\u043e\u0432\u043d\u044f /?[\u0431b]\\b"}
   :wf-post-parsed
               #{"#\\brpg\\b"
                 "toho"
                 "touho"
                 "rozen"
                 "#\\b\u0440\u043f\u0433\\b"
                 "\u0442\u043e\u0445\u043e"
                 "#\\b\u0441\u0443[\u0438\u0439]\u043a"
                 "#\\b\u0440\u0435[\u0438\u0439]\u043c\u0443"
                 "#\u043c\u0430\u0440\u0438\u0441[\u0430\u0443\u0435\u044b]"
                 "#\\b\u043d\u0438\u043f(?:[\u0430\u0435\u0443\u044b]|(?:\u043e\u0439))\\b"
                 "#\\b\u043f\u043e\u043d\u0438\\b"
                 "\u043f\u043e\u043d\u0435\u0439"
                 "mizore"
                 "\u043d\u0435\u0439\u043c\u0444\u0430\u0433"
                 "\u0445\u0430\u0432\u043a\u043e"
                 "\u0445\u0430\u0432\u043e\u043a"
                 "#\\b\u043a\u043e\u0430\\b"
                 "#\\b\u043a\u043e\u0430(?:\u043a\u0443|[^\u043b\u043a][^\u0438\u0441])"
                 "#[k\u043a][a\u0430]\u043d[a\u0430]\u043a"
                 "\u043a\u0438\u0440\u044e\u0448"
                 "#\\b\u0441\u043e\u0440\u043a[\u0430\u0443]?"
                 "#[^?]\\?\\?(?:[^?]|$)"
                 "#[^!]!!(?:[^!]|$)"
                 "#\u0443\u0440\u043e\u0432\u043d\u044f /?[\u0431b]\\b"}})