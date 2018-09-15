(ns kuroi.plugins.bb-joy-org
  (:require
    [kuroi.page-parser :as pp]
    [kuroi.plugins.bb-common-ru :as bb]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [log]]))


;░░░░▄▄▄▄▀▀▀▀▀▀▀▀▄▄▄▄▄▄
;░░░░█░░░░▒▒▒▒▒▒▒▒▒▒▒▒░░▀▀▄
;░░░█░░░▒▒▒▒▒▒░░░░░░░░▒▒▒░░█
;░░█░░░░░░▄██▀▄▄░░░░░▄▄▄░░░█
;░▀▒▄▄▄▒░█▀▀▀▀▄▄█░░░██▄▄█░░░█
;█▒█▒▄░▀▄▄▄▀░░░░░░░░█░░░▒▒▒▒▒█
;█▒█░█▀▄▄░░░░░█▀░░░░▀▄░░▄▀▀▀▄▒█
;░█▀▄░█▄░█▀▄▄░▀░▀▀░▄▄▀░░░░█░░█
;░░█░░▀▄▀█▄▄░█▀▀▀▄▄▄▄▀▀█▀██░█
;░░░█░░██░░▀█▄▄▄█▄▄█▄████░█
;░░░░█░░░▀▀▄░█░░░█░███████░█
;░░░░░▀▄░░░▀▀▄▄▄█▄█▄█▄█▄▀░░█
;░░░░░░░▀▄▄░▒▒▒▒░░░░░░░░░░█
;░░░░░░░░░░▀▀▄▄░▒▒▒▒▒▒▒▒▒▒░█
;░░░░░░░░░░░░░░▀▄▄▄▄▄░░░░░█


(def ^:const +trademark+ "joy.org")

(defmethod pp/paginate +trademark+ [n target]
           (bb/paginate n target))

(defmethod pp/thread-url +trademark+ [thread-id target]
           (bb/thread-url thread-id target))

(defmethod pp/meta-page-url +trademark+ [_ _])

(defmethod pp/get-thread-id +trademark+ [root-node target]
           (when root-node
                 (get (.split (.getAttribute root-node "id") "-") 1)))

(defmethod pp/get-oppost +trademark+ [thread target]
           thread)

(defmethod pp/parse-post +trademark+ [root-node data target]
           (bb/parse-post root-node data target ".torTopic, a.topictitle"))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
           (bb/parse-threads doc-tree target ".maintitle a" "tr[id^='tr-']"))
