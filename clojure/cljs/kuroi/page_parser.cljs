;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.page-parser
  (:require
   [kuroi.io :as io]
   [kuroi.base :as base]
   [kuroi.filters :as filt]
   [goog.style :as style]
   [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

;; utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn trade-dispatch1 [target] (:trade target))
(defn trade-dispatch [_ target] (:trade target))

(defmulti thread-url trade-dispatch)
(defmulti html-thread-url trade-dispatch)

(defmulti json? trade-dispatch1)

(defmethod json? :default [target]
  false)

(defmulti get-scheme :trade)

(defmethod get-scheme :default [target]
           "http://")

(defmulti get-page-ext :trade)

(defmethod get-page-ext :default [target]
  "html")

(defn get-ext [target]
  (if (json? target)
    "json"
    (get-page-ext target)))

(defn sanitize [content]
  (when content
    (str/replace content #"<([^ ]+)[^>]*onload[^>]*>"
                 (fn [[_ & match]] (str "<" (first match) ">")))))

(def *host* (get (re-find #"chan://([^/]*)" (js/decodeURI (.-location js/document))) 1))
(def *host-pattern* (re-pattern (str "https?://" *host* ".*")))

(defn fix-url [url target]
  (when url
    (let [url (if (.startsWith url "moz-extension://")
                (get (re-find #"moz-extension://[^/]+(/.*)" url) 1)
                url)
          url (if io/*addon*
                url
                (if (re-matches *host-pattern* url)
                  (.substring url (+ (.indexOf url *host*) (.-length *host*)))
                  url))
          url (cond
               (= (get url 0) \/) (if (= (get url 1) \/)
                                    (str "http:" url)
                                    (str (get-scheme target) (:domain target) url))
               (re-matches #"^http.*" url) url
               :default (str (:target target) "/" url))]
      (if (:prox target)
        (str "/get/" url)
        url))))

(def ^:dynamic *crossref-map* nil)

(defn check-reflinks [post-id parent-id content target]
  (str/replace content #"<a[^>]+(href=[\"'][^'\"]+[\"'])[^>]*>((&gt;&gt;)|(>>))(\d+)([^<]*)</a>"
               (fn [[_ url _ _ _ reply-no other]]
                 (let [refs (when *crossref-map* (@*crossref-map* reply-no))]
                   (when *crossref-map*
                     (swap! *crossref-map* assoc reply-no (conj refs post-id)))
                   (str "<a target=\"_blank\" "
                        (cond
                         (:fourchan target)
                         (str/replace url #"href=\"([^\"]+)"
                                      (fn [[_ rest]]
                                        (if (and parent-id (= "#" (get rest 0)))
                                          (str "href=\"http://boards." (:trade target) "/"
                                               (:board target) "/thread/" parent-id rest)
                                          (str "href=\"http://boards." (:trade target) rest))))
                         (:kraut target)
                         (str/replace url #"href=\""
                                      (str "href=\"http://" (:trade target)))
                         :else url)
                        " data-onclick=\"frontend.inline_view_reflink(this)\" "
                        "data-onmouseover=\"frontend.show_popup(event, '" reply-no  "', true)\" "
                        "data-onmouseout=\"frontend.show_popup(event, '" reply-no "', false)\""
                        ">&gt;&gt;" reply-no other  "</a>")))))

(defn fix-links [content target]
  (str/replace content (re-pattern (str "href=[\"'](/?(?:" (:board target) ")?/?res/[^\"']+)[\"']"))
               (fn [[_ match]]
                 (str "href=\"" (fix-url match target) "\" target=\"_blank\""))))

(defn select [node sel]
  (if-let [id (.-id node)]
    (if (not= "" id)
      (.querySelector node (str/replace sel ":root" 
                                        (fn [_]
                                          (let [c (first id)]
                                            (if (re-matches #"[0-9]" c)
                                              (str "#\\" (.toString (.charCodeAt c) 16) " " 
                                                   (.substring id 1))
                                              (str "#" id))))))
      (.querySelector node sel))
    (.querySelector node sel)))

(defn select* [node sel]
  (let [nodes (if-let [id (.-id node)]
                (if (not= "" id)
                  (.querySelectorAll node (str/replace sel ":root" 
                                                       (fn [_]
                                                         (let [c (first id)]
                                                           (if (re-matches #"[0-9]" c)
                                                             (str "#\\" (.toString (.charCodeAt c) 16) " " 
                                                                  (.substring id 1))
                                                             (str "#" id))))))
                  (.querySelectorAll node sel))
                (.querySelectorAll node sel))]
    (when nodes
      (let [nodes (for [i (range (.-length nodes))]
                    (aget nodes i))]
        (doall nodes)
        nodes))))
            
  
;; pagination ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti paginate trade-dispatch)

(defmethod paginate :default [n target]
  (str (:target target) "/"
       (when (not (zero? n))
         (str n "." (get-ext target)))))


(defmulti meta-page-url #(:trade %))

(defmethod meta-page-url :default [target]
  nil)

(defn target-pages [target n-pages]
  (let [meta-url (meta-page-url target)
        page-urls (map #(paginate % target) (range (target n-pages)))]
    (if meta-url
      (conj page-urls meta-url)
      page-urls)))

(defmethod thread-url :default [thread-id target]
  (str (get-scheme target) (:forum target) "/res/" thread-id "." (get-ext target)))

(defmethod html-thread-url :default [thread-id target]
  (thread-url thread-id target))

;; post data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti get-thread-id trade-dispatch)

(defmethod get-thread-id :default [root-node target]
  (when root-node 
    (if (= (str/lower-case (.-tagName root-node)) "input")
      (.-value root-node)
      (when-let [elt (select root-node "input[type='checkbox']")]
        (.-value elt)))))

(defmulti get-summary-node trade-dispatch)

(defmethod get-summary-node :default [thread target]
  (select thread 
          ".omittedposts, .omittedinfo, div.abbrev > span, span.summary, .mess-post"))

(defmulti get-omitted-posts trade-dispatch)

(defmethod get-omitted-posts :default [thread target]
  (when-let [summary-node (get-summary-node thread target)]
    (let [summary-text (.-innerHTML summary-node)
          omitted-posts (when summary-text (re-find #"\d+" summary-text))]
      (when omitted-posts (inc (js/parseInt omitted-posts))))))

(defn cons-post-data [&{:keys [parent-id post-num] :as init :or {post-num 1}}]
  init)

(defn build-post-data [data post-id date-val title-elt thumb-img 
                       image-link image-size post-text target]
  (when post-text 
    (when-let [abbrev (select post-text "*[class*='abbr'] a")]
      (let [xpnd-url (if (:oppost data)
                       (thread-url post-id target)
                       (fix-url (.getAttribute abbrev "href") target))]
        (.setAttribute abbrev "data-onclick"
                       (str "frontend.iv_expand_post(this, '" xpnd-url "')")))))
  (let [width-height (when image-size (re-find #"(\d+)[x\u00d7](\d+)" (.-innerHTML image-size)))
        post-text (when post-text (fix-links (sanitize (.-innerHTML post-text)) target))]
    (merge data
           {:id post-id
            :internal-id (str (:prefix target) post-id)
            :date (when date-val (str/trim date-val))
            :title (when title-elt (sanitize (.-textContent title-elt)))
            :link (thread-url (or (:parent-id data) post-id) target)
            :thumb (when thumb-img (fix-url (.getAttribute thumb-img "src") target))
            :image (when image-link (fix-url (.getAttribute image-link "href") target))
            :image-size (if image-size [(get width-height 1) (get width-height 2)] [0 0])
            :text (when post-text (check-reflinks post-id (:parent-id data) post-text target))
            })))

(defn build-image-data [thread-id thumb-img image-link image-size target]
  (let [width-height (when image-size (re-find #"(\d+)[x\u00d7](\d+)" (.-innerHTML image-size)))
        image-link (when image-link (fix-url (.getAttribute image-link "href") target))]
    {:id thread-id
     :internal-id image-link
     :link (thread-url thread-id target)
     :thumb (when thumb-img (fix-url (.getAttribute thumb-img "src") target))
     :image image-link
     :image-size (if image-size [(get width-height 1) (get width-height 2)] [0 0])
     }))

(defmulti parse-images trade-dispatch)

(defmulti parse-thread-images trade-dispatch)

(defn build-error-data [message target]
  {:id "-"
   :internal-id (str (:prefix target) "-error")
   :post-count 1
   :error true
   :title "Error"
   :text message})


;; oppost selection ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- exists-in [root-node sel]
  (when (select root-node sel) true))

(defmulti get-oppost trade-dispatch)

;; "smart" heuristics for unknown sites
(defmethod get-oppost :default [thread target]
  (cond
   (exists-in thread ":root > div:first-of-type > div.postbody,
                      :root > div:first-of-type > blockquote,
                      :root > div:first-of-type > mobile")
   (select thread ":root > div:first-of-type")
   
   (exists-in thread ":root > .op > .post")
   (select thread ":root > .op")
   
   (exists-in thread ".maincontentdiv > form#delform > blockquote")
   (select thread ".maincontentdiv > form#delform")
   
   :default thread))

;; tree scrapper ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti parse-threads trade-dispatch)

(defmulti parse-post #(:trade %3))

(defn parse-post-generic [root-node data target]
  (let [thread-id (get-thread-id root-node target)
        poster-name-elt (select root-node "span[class*='postername']")
        post-name-text (when post-name-elt (.-textContent poster-name-elt))
        poster-name (when poster-name-text (str/trim post-name-text))
        date-elt (select root-node ".postdate, .posttime, .date, label")
        date-elt (when date-elt (aget (.-childNodes date-elt) 
                                      (dec (.-length (.-childNodes date-elt)))))
        date-text (when date-elt (.-nodeValue date-elt))
        date-val (when date-text (str/trim date-text))
        date-val (str poster-name (when (and poster-name date-val) ", ") date-val)
        title-elt (select root-node "span[class*='title']")
        thumb-img (select root-node "img[src*='thumb/'], 
                                    .threadimg img,
                                    .nothumb,
                                    .post_video_pic > img")
        image-link (select root-node "a[href*='src/'], .threadimg img")
        filesize (select root-node ".filesize, .fileinfo")
        post-text (select root-node "blockquote, div.postbody > div.message")]
;;;
;;(.error console thread-id)
    (build-post-data data thread-id date-val title-elt thumb-img image-link 
                     filesize post-text target)))

(defmethod parse-post :default [root-node data target]
  (parse-post-generic root-node data target))

(defn parse-replies [oppost all-replies target]
  (let [post-count (+ (count all-replies) (or (:omitted oppost) 1))
        last-id (if (seq all-replies)
                  (get-thread-id (last all-replies) target)
                  (:id oppost))
        take-replies (:replies target)]
    (if (= 0 take-replies)
      (assoc oppost :last-id last-id :post-count post-count)
      (binding [*crossref-map* (atom {})]
        (let [replies
              (when (seq all-replies)
                (let [first-taken (- (count all-replies) take-replies)
                      taken-replies (if (> take-replies 0)
                                      (seq (second (split-at first-taken all-replies)))
                                      all-replies)]
                  (loop [replies taken-replies num (- post-count (count taken-replies) -1)
                         out []]
                    (let [reply (first replies)]
                      (if reply
                        (let [data (cons-post-data :parent-id (:id oppost)
                                                   :post-num num)]
                          (recur (next replies)
                                 (inc num)
                                 (conj out (parse-post reply data target))))
                        out)))))
              replies (doall (map #(assoc % :refs (reverse (@*crossref-map* (:id %)))) replies))
              oppost (assoc oppost :children replies :last-id last-id :post-count post-count)]
          (assoc oppost :refs (reverse (@*crossref-map* (:id oppost)))))))))


(defn parse-structured
  ([threads reply-sel target]
     (let [pattern (filt/user-pattern target)
           data (for [thread threads]
                  (when-let [oppost-node (get-oppost thread target)]
                    (let [data (cons-post-data :omitted (get-omitted-posts thread target)
                                               :page-index (:page-index (meta target))
                                               :oppost true)
                          oppost (parse-post oppost-node data target)]
                      (when (filt/filter-shallow pattern oppost target)
                        (let [replies (select* thread reply-sel)]
                          (parse-replies oppost replies target))))))]
       (filt/filter-deep pattern data target)))
  ([threads target]
     (parse-structured threads "table .reply" target)))


;; json stuff ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti get-json-thread-id trade-dispatch)

(defmulti get-json-post-images trade-dispatch)

(defmulti json->image #(:trade %3))

(defn parse-json [json target]
  (let [pattern (filt/user-pattern target)
        data (for [thread (json "threads")]
               (when-let [oppost-node (get-oppost thread target)]
                 (let [data (cons-post-data :omitted (get-omitted-posts thread target)
                                            :page-index (:page-index (meta target))
                                            :oppost true)
                       oppost (parse-post oppost-node data target)]
                   (when (filt/filter-shallow pattern oppost target)
                     (let [replies (rest (thread "posts"))]
                       (parse-replies oppost replies target))))))]
    (filt/filter-deep pattern data target)))

(defn parse-images-json [json target]
  (apply concat 
         (for [thread (json "threads")]
           (let [thread-id (get-json-thread-id thread target)]
             (apply concat 
                    (for [post (thread "posts")]
                      (map #(json->image thread-id % target) 
                           (get-json-post-images post target))))))))

;; flat scrapper ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-flat [doc-tree target]
  (let [elts (select* doc-tree "form#delform > input[type='checkbox'],
                                form#delform > label > input[type='checkbox'],
                                form#delform > .omittedposts,
                                form#delform > .posttime,
                                form#delform > span.filetitle,
                                form#delform > label > span.filetitle,
                                form#delform > a[href*='src/'],
                                form#delform > .filesize,
                                form#delform > blockquote,
                                form#delform > table")
        ctr (atom 0)
        threads (partition-by #(do (when (and (= (str/lower-case (.-nodeName %)) "span")
                                              (= (str/lower-case (.-className %)) "filesize"))
                                     (swap! ctr inc))
                                   @ctr)
                              elts)]
    (parse-structured (for [t threads]
                        (let [node (.createElement doc-tree "div")]
                          (do
                            (doseq [child t]
                              (.appendChild node child))
                            node)))
                      target)))

(defmethod parse-threads :default [doc-tree target]
  (if (select doc-tree "form > blockquote")
    (parse-flat doc-tree target)
    (parse-structured (select* doc-tree "form > div[id^='thread']") target)))

;; image scrappers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod parse-thread-images :default [doc-tree target]
  (let [images (select* doc-tree ".filesize, .fileText, .fileinfo, a.fileThumb > img, .fileInfo, img[src*='thumb/']")]
        (for [[filesz thumb] (partition 2 images)]
          (build-image-data nil thumb (.-parentNode thumb) filesz target))))

(defn parse-images-struct [doc-tree oppost target]
  (let [threads (select* doc-tree oppost)]
    (apply concat
           (for [root-node threads]
             (let [thread-id (get-thread-id root-node target)
                   images (select* root-node ".filesize, .fileText, a.fileThumb > img, .fileinfo, .fileInfo, img[src*='thumb/']")]
               (for [[filesz thumb] (partition 2 images)]
                 (let [link (.-parentNode thumb)]
                   (build-image-data thread-id thumb link filesz target))))))))

(defn parse-images-flat [doc-tree target]
  (let [nodes (select* doc-tree "form > input[type='checkbox'],
                                form > label > input[type='checkbox'],
                                .filesize,
                                *:not(.filesize) > a[target][href*='src/']")
        groups (partition-by #(= (str/lower-case (.-nodeName %)) "input") nodes)
        images (loop [prev (first groups) sep (second groups) rest (nnext groups) out []]
                 (if rest
                   (let [following (first rest)
                         [h t] (split-at (- (count following) 2) following)]
                       (recur t (fnext rest) (nnext rest) (concat out sep prev h)))
                   (concat out sep prev)))
        images (partition-by  #(= (str/lower-case (.-nodeName %)) "input") images)]      
    (loop [th (first images) th-images (second images) rest (nnext images) out []]
      (if th-images
        (let [id-elt (first th)
              thread-id (get-thread-id id-elt target)]
          (recur (first rest) (second rest) (nnext rest)
                 (concat out (for [[filesz link] (partition 2 th-images)]
                               (build-image-data thread-id (select link "img") link filesz
                                                    target)))))
       out))))

(defmethod parse-images :default [doc-tree target]
  (if (:inline target)
    (parse-thread-images doc-tree target)
    (if (select doc-tree "form > blockquote")
      (parse-images-flat doc-tree target)
      (parse-images-struct doc-tree ".board > .thread,
                                  form > div[id^='thread'],
                                  body > form > div[id^='thread']"
                           target))))

;; extraction of additional data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti transform-page-text trade-dispatch)

(defmethod transform-page-text :default [text target]
  text)

(defmulti get-post-form trade-dispatch)

(defmethod get-post-form :default [dom target]
  (select dom "form#postform, .postform, form[name='post'], #posting_form"))

;; navbar

(defmulti extract-navbar trade-dispatch)

(defn transform-navbar [dom target nodes link-p link-f]
    (loop [nodes nodes out []]
      (if-let [node (first nodes)]
        (cond
         (and (= (.-tagName node) "A") (link-p node))
         (let [link (link-f node)]
           (a-> href link (str (.getAttribute link "href") (:params target)))
           (recur (next nodes) (conj out link)))

         (and (= (.-nodeType node) 3)
              (re-find #"[\[\]|/]" (.-textContent node)))
         (let [chars (filter #(and (not (str/blank? %)) (s-in? "[]|/" %))
                             (filter #(not (re-matches #"\s" %)) (.-textContent node)))]
           (recur (next nodes) (conj out (map #(.createTextNode dom %) chars))))

         :else (recur (next nodes) out))
        (let [ctr (atom 0)
              nodes (partition-by #(let [c @ctr]
                                     (when (= (.-textContent %) "]") (swap! ctr inc))
                                     c)
                                  (flatten out))
              nodes (flatten (filter #(and (> (count %) 2) 
                                           (some (fn [n] (= (.-tagName n) "A")) %))
                                     nodes))
              spaces (repeatedly (count nodes) #(.createTextNode dom "  "))
              div (.createElement dom "div")]
          (doseq [n (interleave spaces nodes)]
            (.appendChild div n))
          div))))

(defn extract-navbar-menu [dom menu-sel target]
  (let [navbar-elts (select* dom menu-sel)
        nodes (reduce concat
                      (for [ne navbar-elts]
                        (concat 
                         [(.createTextNode dom "[")]
                         (select* ne "a")
                         [(.createTextNode dom "]")])))]
    (transform-navbar dom target nodes
                      #(re-matches #"/[^/]+/" (.getAttribute % "href"))
                      #(let [l (.getAttribute % "href")]
                         (set! (.-textContent %) l)
                         (set! (.-href %) (str "?front&url=" (:scheme target) (:domain target)
                                               (.substring l 0 (.lastIndexOf l "/"))))
                         %))))

(defmethod extract-navbar :default [dom target]
  (let [navbar-elt (select dom ".navbar, body > p[align='right']")
        navbar-elt (or navbar-elt (select dom ".adminbar"))]
    (when navbar-elt
      (transform-navbar dom target (seq (.-childNodes navbar-elt))
                        #(re-matches #"/?[a-zA-Z0-9-]+/?(index\.x?html)?" (.-href %))
                        #(let [l (.getAttribute % "href")]
                           (set! (.-href %) (str "?front&url=" (:scheme target) (:domain target)
                                                 (if (= (first l) "/")
                                                   (if (s-in? l "index")
                                                     (.substring l 0 (.lastIndexOf l "/"))
                                                     l)
                                                   (str "/" l))))
                           %)))))

;; captcha

(defn get-thread-dom [thread-url target callback]
  (cb-let [response] (io/get-page thread-url)
    (if (= (:state response) "ok")
      (let [page (:page response)
            text (transform-page-text (:text page) target)
            dom (.parseFromString (new js/DOMParser) text "text/html")]
        (callback dom)))))

(defmulti get-captcha #(:trade %3))

(defn get-recaptcha [url parent target callback]
  (cb-let [dom] (get-thread-dom url target)
    (when dom 
      (when-let [form (get-post-form dom target)]
        (when-let [iframe (select form "noscript iframe")]
          (cb-let [response] (io/get-page (fix-url (.-src iframe) target))
             (if (= (:state response) "ok")
               (let [page (:page response)
                     dom (.parseFromString (new js/DOMParser) (:text page) "text/html")
                     img (select dom "img")
                     link (str "http://www.google.com/recaptcha/api2/" (.-src img))
                     challenge (.substring link (inc (.indexOf link "=")))]
                 (callback {:link link :challenge challenge})))))))))

(defmethod get-captcha :default [url parent target callback]
  (cb-let [dom] (get-thread-dom url target)
    (when-let [form (get-post-form dom target)]
      (when-let [img (select form "img")]
        (callback {:link (fix-url (.-src img) target)})))))

;; posting errors

(defn find-post-error [tag response target]
  (if (= (:state response) "ok")
    (let [dom (.parseFromString (new js/DOMParser) (:text response) "text/html")
          error  (select dom tag)]
      (when error
        (when-let [text (.-firstChild error)]
          (.-textContent text))))))

(defmulti get-post-error trade-dispatch)

(defmethod get-post-error "7chan.org" [response target])

(defmethod get-post-error :default [response target]
  (find-post-error "h1" response target))

;; posting form

(defmulti adjust-form trade-dispatch)

(defmethod adjust-form "7chan.org" [form target]
  (set! (.-__parent_key__ form) "replythread")
  (set! (.-__captcha_challenge__ form) "recaptcha_challenge_field")
  (let [captcha-line (select form "#captcha_label")]
    (set! (.-innerHTML (.-parentNode captcha-line)) "<label id=\"captcha_label\">Captcha</label>
                                                     <img/><br/>
                                                     <input type=\"text\" name=\"recaptcha_response_field\"
                                                           autocomplete=\"off\">"))
  form)

(defmethod adjust-form :default [form target]
  form)

(defn extract-form [dom target]
  (when-let [form-node (get-post-form dom target)]
    (let [form-node (.cloneNode form-node true)
          radio (select form-node "tr > td input[type='radio']")
          tr (when radio
               (loop [e radio]
                 (if (and (not (nil? e)) (= (str/lower-case (.-tagName e)) "tr"))
                   e
                   (recur (.-parentNode e)))))]
      (when tr
        (.removeChild (.-parentNode tr) tr))
      (set! (.-__parent_key__ form-node) "parent")
      (set! (.-__captcha_elt__ form-node) "img")
      (set! (.-__captcha_attr__ form-node) "src")
      (set! (.-id form-node) (str (:prefix target) "postform"))
      (set! (.-display (.-style form-node)) "none")
      (set! (.-action form-node) (fix-url (.-action form-node) target))
      (doseq [img (select* form-node "img")]
        (set! (.-src img) (fix-url (.-src img) target)))

      (let [form (adjust-form form-node target)]
        (when-let [captcha-elt (select form (.-__captcha_elt__ form))]
          (.setAttribute captcha-elt (.-__captcha_attr__ form-node) ""))
        form))))

;;;;;;;;;;;;;

(defn parse-page [page target &{:keys [get-metadata multiple]}]
  (let [error-msg (str "<span class=\"red\">Error loading page: " (:url page))]
    (if (= (:error page) "http_error")
      [(build-error-data (str error-msg " (" (:status page) ")") target)]
      (let [target (with-meta target {:page-index (:index page)
                                      :multiple multiple})
            text (transform-page-text (:text page) target)
            dom (if (json? target)
                  (js->clj (.parse js/JSON text))
                  (.parseFromString (new js/DOMParser) text "text/html"))
            threads (if (:img target)
                      (parse-images dom target)
                      (parse-threads dom target))
            meta {:form (when (and io/*addon* get-metadata) (extract-form dom target))
                  :navbar (when get-metadata (extract-navbar dom target))}]
        (if (seq threads)
          (with-meta threads meta)
          (if (:filter target)
            (with-meta [] meta)
            (let [threads [(build-error-data error-msg target)]]
              (with-meta threads meta))))))))

(defn get-page-meta [page target]
  (when (not= (:error page) "http_error")
    (let [text (transform-page-text (:text page) target)
          dom (.parseFromString (new js/DOMParser) text "text/html")]
      {:form (extract-form dom target)
       :navbar (extract-navbar dom target)})))
