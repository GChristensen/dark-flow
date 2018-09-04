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
  (str (:scheme target) (:forum target) "/res/" thread-id ".json"))

(defmethod pp/parse-post +trademark+ [root-node data target]
  (let [post-id (root-node "num")
       post-text (pp/fix-links (pp/sanitize (root-node "comment")) target)
       image (first (root-node "files"))
       flag (root-node "icon")]
      (merge data
             {:id (str post-id)
              :internal-id (str (:prefix target) post-id)
              :date (root-node "date")
              :link (str (:scheme target) (:forum target) "/res/" (or (:parent-id data) post-id) ".html")
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
  (pp/extract-navbar-menu dom "nav.rmenu > span" target))

;(defmethod get-captcha +trademark+ [url parent target callback]
;           (let [url (str "https://2ch.hk/makaba/captcha.fcgi?type=2chaptcha"
;                          "&board=" (:board target)
;                          (when parent
;                                (str "&action=thread")))]
;                (cb-let [response] (io/get-page url)
;                        (if (= (:state response) "ok")
;                          (let [page (:page response)
;                                text (:text page)]
;                               (callback (if (.startsWith text "CHECK")
;                                           (let [key (.substr text (inc (.lastIndexOf text "\n")))
;                                                 link (str "https://2ch.hk/makaba/captcha.fcgi?type=2chaptcha"
;                                                           "&action=image&id=" key)]
;                                                {:link link :challenge key})
;                                           {})))))))

;(defmethod get-post-error +trademark+ [response target]
;           (let [json (.parse js/JSON (:text response))]
;                (when-let [err (aget json "Error")]
;                          (when (not (nil? err))
;                                (aget json "Reason")))))

;(defmethod adjust-form +trademark+ [form target]
;           (set! (.-__parent_key__ form) "thread")
;           (set! (.-__captcha_row__ form) ".captcha-row")
;           (set! (.-__captcha_challenge__ form) "2chaptcha_id")
;           (set! (.-action form) (str (fix-url (.-action form) target) "?json=1"))
;           (set! (.-action form) (str/replace (.-action form) "http:" "https:"))
;           (let [captcha-line (select form "div.captcha-box")
;                 text-area (select form "#shampoo")
;                 submit (select form "#submit")
;                 message-byte-len (select form ".message-byte-len")
;                 send-mob (select form ".send-mob")
;                 qr-image2 (select form "#image2")
;                 qr-image3 (select form "#image3")
;                 qr-image4 (select form "#image4")
;                 hr (select form "hr")
;                 buy-pass (select form ".kupi-passcode-suka")
;                 captcha-type (select form "input[name=\"captcha_type\"]")
;                 usercode (select form "input[name=\"usercode\"]")
;                 oekaki (select form ".oekaki-images-area")
;                 rules (select form ".rules-area")]
;                (when submit (set! (.-cssFloat (.-style submit)) "right"))
;                (when buy-pass (set! (.-display (.-style buy-pass)) "none"))
;                ;(when usercode (.removeChild (.-parentNode usercode) usercode))
;                ;(when captcha-type (.removeChild (.-parentNode captcha-type) captcha-type))
;                ;(when captcha-type (set! (.-value captcha-type) "yandex"))
;                (when message-byte-len (set! (.-display (.-style message-byte-len)) "none"))
;                (when hr (set! (.-display (.-style hr)) "none"))
;                (when send-mob (set! (.-display (.-style send-mob)) "none"))
;                (when buy-pass (set! (.-display (.-style buy-pass)) "none"))
;                (when oekaki (set! (.-display (.-style oekaki)) "none"))
;                (when rules (set! (.-display (.-style rules)) "none"))
;                (when qr-image2 (set! (.-display (.-style qr-image2)) "none"))
;                (when qr-image3 (set! (.-display (.-style qr-image3)) "none"))
;                (when qr-image4 (set! (.-display (.-style qr-image4)) "none"))
;                (when text-area (set! (.-width (.-style text-area)) "500px"))
;                (when captcha-line (set! (.-innerHTML captcha-line) "<img/><br/>
;                                                         <input type=\"text\" name=\"2chaptcha_value\"
;                                                         autocomplete=\"off\">")))
;           form)