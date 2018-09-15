(ns kuroi.plugins.bb-rutracker-org
  (:require
    [kuroi.page-parser :as pp]
    [kuroi.plugins.bb-common-ru :as bb]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "rutracker.org")

(defmethod pp/paginate +trademark+ [n target]
           (bb/paginate n target))

(defmethod pp/thread-url +trademark+ [thread-id target]
           (bb/thread-url thread-id target))

(defmethod pp/meta-page-url +trademark+ [_ _])

(defmethod pp/get-thread-id +trademark+ [root-node target]
           (when root-node
                 (.getAttribute root-node "data-topic_id")))

(defmethod pp/get-oppost +trademark+ [thread target]
           thread)

(defmethod pp/parse-post +trademark+ [root-node data target]
           (bb/parse-post root-node data target "a.torTopic"))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
           (bb/parse-threads doc-tree target "#topic-title" ".hl-tr"))
