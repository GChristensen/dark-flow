;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.base
  (:require [kuroi.io :as io]))
   
(def *posting-not-impl* #{"2ch.hk"})

(def *iichan-hk-wf*
  {:wf-enabled true ; sneaky wordfilter for iichan.hk
   :wf-title-parsed
               #{}
   :wf-post-parsed
               #{}})