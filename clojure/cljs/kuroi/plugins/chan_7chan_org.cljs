(ns kuroi.plugins.chan-7chan-org
  (:require
    [kuroi.page-parser :as pp]
    [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

(def ^:const +trademark+ "7chan.org")

(defmethod pp/parse-post +trademark+ [root-node data target]
           (let [thread-id (pp/get-thread-id root-node target)
                 date-elt nil ;(pp/select root-node [:.post :> text-node])
                 title-elt (pp/select root-node ".post > .subject")
                 thumb-img (pp/select root-node ".post_thumb > a > img,
                                     .post > .post_thumb > a > img,
                                     .post > div[style='float:left'] > a img")
                 image-link (pp/select root-node ".post_thumb > a,
                                     .post > .post_thumb > a,
                                     .post > div[style='float:left'] > a")
                 filesize (pp/select root-node ".file_size,
                                    .post > .file_size,
                                    .post .multithumbfirst > a")
                 post-text (pp/select root-node ".post > .message")]
                (pp/build-post-data data thread-id date-elt title-elt thumb-img
                                 image-link filesize post-text target)))

(defmethod pp/parse-threads +trademark+ [doc-tree target]
           (pp/parse-structured (pp/select* doc-tree "form > div[id^='thread']")
                             ".reply"
                             target))