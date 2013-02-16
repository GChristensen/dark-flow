;; Dark Flow
;;
;; (C) 2013 g/christensen (gchristnsn@gmail.com)

(ns kuroi.page-parser
  (:require
   [kuroi.io :as io]
   [kuroi.base :as base]
   [kuroi.filters :as filt]
   [clojure.string :as str])
  (:use-macros [kuroi.macros :only [cb-let a- a-> s-in? log]]))

;; all the imageboard specific code resides here

;; correct parsing is known for:

;; 4chan.org
;; 7chan.org
;; gurochan.net
;; wakachan.org
;; ichan.org (limited support)
;; iichan.hk
;; 0chan.hk
;; 2ch.hk
;; 2--ch.ru
;; 410chan.org
;; 5channel.net
;; dobrochan.ru (limited support)
;; nowere.net
;; krautchan.net

;; utilities ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const alt-exts {"dobrochan.ru" "xhtml", "2--ch.ru" "memhtml"})

(defn trade-dispatch [_ target] (:trade target))

(defn get-ext [target]
  (or (alt-exts (:trade target)) "html"))

(defn sanitize [content]
  (when content
    (str/replace content #"<([^ ]+)[^>]*onload[^>]*>"
                 (fn [_ & match] (str "<" (first match) ">")))))

(def *host* (.-host (.-location js/document)))
(def *host-pattern* (re-pattern (str "https?://" *host* ".*")))

(defn fix-url [url target]
  (when url
    (let [url (if io/*addon*
                url
                (if (re-matches *host-pattern* url)
                  (.substring url (+ (.indexOf url *host*) (.-length *host*)))
                  url))
          url (cond
               (= (get url 0) \/) (if (= (get url 1) \/)
                                    (str "http:" url)
                                    (str (:scheme target) (:domain target) url))
               (re-matches #"^http.*" url) url
               :default (str (:target target) "/" url))]
      (if (:prox target)
        (str "/get/" url)
        url))))

(def ^:dynamic *crossref-map* nil)

(defn check-reflinks [post-id content target]
  (str/replace content #"<a[^>]+(href=[\"'][^'\"]+[\"'])[^>]*>&gt;&gt;(\d+)</a>"
               (fn [_ url reply-no]
                 (let [refs (when *crossref-map* (@*crossref-map* reply-no))]
                   (when *crossref-map*
                     (swap! *crossref-map* assoc reply-no (conj refs post-id)))
                   (str "<a target=\"_blank\" "
                        (cond
                         (:fourchan target)
                         (str/replace url #"href=\"(\d+)"
                                      (fn [_ rest]
                                        (str "href=\"http://" (:forum target) "/res/" rest)))
                         (:kraut target)
                         (str/replace url #"href=\""
                                      (str "href=\"http://" (:trade target)))
                         :else url)
                        " " 
                        "onclick=\"return frontend.inline_view_reflink(this)\" "
                        "onmouseover=\"frontend.show_popup(event, '" reply-no  "', true)\" "
                        "onmouseout=\"frontend.show_popup(event, '" reply-no "', false)\""
                        ">&gt;&gt;" reply-no "</a>")))))

(defn fix-links [content target]
  (str/replace content (re-pattern (str "href=[\"'](/?(?:" (:board target) ")?/?res/[^\"']+)[\"']"))
               (fn [_ match]
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

(defmethod paginate "4chan.org" [n target]
  (str (:scheme target) "boards.4chan.org/" (:board target) "/"
       (when (not (zero? n)) n)))         

(defmethod paginate "iichan.hk" [n target]
  (str (:target target) "/"
       (if (zero? n)
         (str "index." (get-ext target))
         (str n "." (get-ext target)))))

(defmethod paginate "dobrochan.ru" [n target]
  (str (:target target) "/"
       (if (zero? n)
         (str "index." (get-ext target))
         (str n "." (get-ext target)))))

(defmethod paginate "2--ch.ru" [n target]
  (str (:target target) "/"
       (str n "." (get-ext target))))

(defmethod paginate :default [n target]
  (str (:target target) "/"
       (when (not (zero? n))
         (str n "." (get-ext target)))))

(defn target-pages [target pages]
  (map #(paginate % target) (range (target pages))))

(defmulti thread-url trade-dispatch)

(defmethod thread-url "krautchan.net" [thread-id target]
  (str (:scheme target) (:forum target) "/thread-" thread-id "." (get-ext target)))

(defmethod thread-url "4chan.org" [thread-id target]
  (str "http://boards.4chan.org/" (:board target) "/res/" thread-id))

(defmethod thread-url "iichan.net" [thread-id target]
  (str "http://kei.iichan.net/" (:board target) "/res/" thread-id ".html"))

(defmethod thread-url "2--ch.ru" [thread-id target]
  (str (:scheme target) (:forum target) "/res/" thread-id ".html"))

(defmethod thread-url :default [thread-id target]
  (str (:scheme target) (:forum target) "/res/" thread-id "." (get-ext target)))

;; post data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti get-thread-id trade-dispatch)

(defmethod get-thread-id "4chan.org" [root-node target]
  (let [elt (select root-node "input[type='checkbox']")]
    (.-name elt)))

(defmethod get-thread-id "dobrochan.ru" [root-node target]
  (let [elt (select root-node "input[type='checkbox']")]
    (.-name elt)))

(defmethod get-thread-id "krautchan.net" [root-node target]
  (let [elt (select root-node ".postheader > input")]
    (re-find #"\d+" (.-name elt))))

(defmethod get-thread-id :default [root-node target]
  (when root-node 
    (if (= (str/lower-case (.-tagName root-node)) "input")
      (.-value root-node)
      (when-let [elt (select root-node "input[type='checkbox']")]
        (.-value elt)))))

(defmulti get-summary-node trade-dispatch)

(defmethod get-summary-node "dobrochan.ru" [thread target]
  (if-let [omitted (select thread "div.abbrev > span:last-of-type")]
    (if (re-find #"\d+" (.-textContent omitted))
      omitted
      0)
    0))

(defmethod get-summary-node :default [thread target]
  (select thread 
          ".omittedposts, .omittedinfo, div.abbrev > span, span.summary"))

(defn get-omitted-posts [thread target]
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
        (.setAttribute abbrev "onclick" 
                       (str "return frontend.iv_expand_post(this, '" xpnd-url "')")))))
  (let [width-height (when image-size (re-find #"(\d+)[x\u00d7](\d+)" (.-innerHTML image-size)))
        post-text (fix-links (sanitize (.-innerHTML post-text)) target)]
    (merge data
           {:id post-id
            :internal-id (str (:prefix target) post-id)
            :date (when date-val (str/trim date-val))
            :title (when title-elt (sanitize (.-textContent title-elt)))
            :link (thread-url (or (:parent-id data) post-id) target)
            :thumb (when thumb-img (fix-url (.getAttribute thumb-img "src") target))
            :image (when image-link (fix-url (.getAttribute image-link "href") target))
            :image-size (if image-size [(get width-height 1) (get width-height 2)] [0 0])
            :text (check-reflinks post-id post-text target)
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

(defmethod get-oppost "iichan.hk" [thread target]
  thread)

(defmethod get-oppost "0chan.hk" [thread target]
  (select thread ".postnode"))

(defmethod get-oppost "2ch.hk" [thread target]
  (select thread ".oppost"))

(defmethod get-oppost "4chan.org" [thread target]
  (select thread ".op"))

(defmethod get-oppost "krautchan.net" [thread target]
  (select thread ".thread_body"))

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

;; (flat scrapper isn't implemented)

(defmulti parse-threads trade-dispatch)

(defmulti parse-post #(:trade %3))

(defn parse-post-generic [root-node data target]
  (let [thread-id (get-thread-id root-node target)
        poster-name-elt (select root-node "span[class*='postername']")
        poster-name (when poster-name-elt (str/trim (.-textContent poster-name-elt)))
        date-elt (select root-node ".posttime, label")
        date-elt (when date-elt (aget (.-childNodes date-elt) 
                                      (dec (.-length (.-childNodes date-elt)))))
        date-val (when date-elt (str/trim (.-nodeValue date-elt)))
        date-val (str poster-name (when (and poster-name date-val) ", ") date-val)
        title-elt (select root-node "span[class*='title']")
        thumb-img (select root-node "img[src*='thumb/'], 
                                    .threadimg img,
                                    .post_video_pic > img")
        image-link (select root-node "a[href*='src/'], .threadimg img")
        filesize (select root-node ".filesize, .fileinfo")
        post-text (select root-node "blockquote, div.postbody > div.message")]
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

;; site-specific parsers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod parse-post "4chan.org" [root-node data target]
  (let [thread-id (get-thread-id root-node target)
        title-elt (select root-node ".post > .subject")
        poster-name-elt (select root-node "span.nameBlock > span.name")
        poster-name (when poster-name-elt (str/trim (.-textContent poster-name-elt)))
        date-elt (select root-node ".dateTime")
        date-val (when date-elt (str/replace (.-textContent date-elt) #" No\..*" ""))
        date-val (str poster-name (when (and poster-name date-val) ", ") date-val)
        image-link (select root-node "a.fileThumb")
        thumb-img (when image-link (select image-link "img"))
        filesize (select root-node ".fileText")
        post-text (select root-node ".post > blockquote")
        flag-elt (select root-node ".countryFlag")
        flag (when flag-elt 
              [(fix-url (.-src flag-elt) target)
               (.-title flag-elt)])]
    (assoc (build-post-data data thread-id date-val title-elt thumb-img 
                            image-link filesize post-text target)
      :flag flag)))

(defmethod parse-threads "4chan.org" [doc-tree target]
  (parse-structured (select* doc-tree "div.board > div.thread")
                     ".replyContainer > .reply"
                     target))

(defmethod parse-post "krautchan.net" [root-node data target]
  (let [thread-id (get-thread-id root-node target)
        flag-elt (select root-node ".postheader > img[src*='ball']")
        flag (when flag-elt [(fix-url (.-src flag-elt) target)
                             (get (re-find #"'([^']+)'" (.-onmouseover flag-elt)) 1)])
        title-elt (select root-node ".postheader > span.postsubject")
        date-elt (select root-node ".postdate")
        date (str/replace (.-textContent date-elt) #".\d+$" "")
        image-link (select root-node ".file_thread > a[target='_blank'],
                                      .file_reply > a[target='_blank']")
        thumb-img (when image-link (select image-link "img"))
        filesize (select root-node ".file_thread > .fileinfo,
                                    .file_reply > .fileinfo")
        post-text (select root-node "blockquote")]
    (assoc (build-post-data data thread-id date title-elt thumb-img 
                            image-link filesize post-text target)
      :flag flag)))

(defmethod parse-threads "krautchan.net" [doc-tree target]
  (parse-structured (select* doc-tree "form > div.thread")
                     ".postreply"
                     target))

(defmethod parse-post "7chan.org" [root-node data target]
  (let [thread-id (get-thread-id root-node target)
        date-elt nil ;(select root-node [:.post :> text-node])
        title-elt (select root-node ".post > .subject")
        thumb-img (select root-node ".post_thumb > a > img,
                                     .post > .post_thumb > a > img,
                                     .post > div[style='float:left'] > a img")
        image-link (select root-node ".post_thumb > a,
                                     .post > .post_thumb > a,
                                     .post > div[style='float:left'] > a")
        filesize (select root-node ".file_size,
                                    .post > .file_size,
                                    .post .multithumbfirst > a")
        post-text (select root-node ".post > .message")]
    (build-post-data data thread-id date-elt title-elt thumb-img 
                     image-link filesize post-text target)))

(defmethod parse-threads "7chan.org" [doc-tree target]
  (parse-structured (select* doc-tree "form > div[id^='thread']")
                    ".reply"
                    target))

(defmethod parse-post "410chan.org" [root-node data target]
  (let [data (parse-post-generic root-node data target)
        flag-elt (select root-node "span.country > img")
        flag (when flag-elt 
               [(fix-url (.-src flag-elt) target)
                (.-alt flag-elt)])]
    (assoc data :flag flag)))
    

(defmethod parse-threads "2--ch.ru" [doc-tree target]
  (parse-structured (select* doc-tree "form > div.threadz")
                    "table .reply"
                    target))
                     
;; image scrappers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti parse-thread-images trade-dispatch)

;; (defmethod parse-thread-images "dobrochan.ru" [doc-tree target]
;;   (let [images (select doc-tree {[:.fileinfo] [:.file]})]
;;     (for [filesz images]
;;       (make-image-headline nil (first (select filesz [:img])) (first (select filesz [:a])) filesz target))))
  
(defmethod parse-thread-images :default [doc-tree target]
  (let [images (select* doc-tree ".filesize, .fileinfo, .fileInfo, img[src*='thumb/']")]
        (for [[filesz thumb] (partition 2 images)]
          (build-image-data nil thumb (.-parentNode thumb) filesz target))))

(defn parse-images-struct [doc-tree oppost target]
  (let [threads (select* doc-tree oppost)]
    (apply concat
           (for [root-node threads]
             (let [thread-id (get-thread-id root-node target)
                   images (select* root-node ".filesize, .fileinfo, .fileInfo, img[src*='thumb/']")]
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

(defmulti parse-images trade-dispatch)

(defmethod parse-images "krautchan.net" [doc-tree target]
  [])

(defmethod parse-images :default [doc-tree target]
  (if (:inline target)
    (parse-thread-images doc-tree target)
    (if (select doc-tree "form > blockquote")
      (parse-images-flat doc-tree target)
      (parse-images-struct doc-tree ".board > .thread,
                                  form#delform > div[id^='thread'],
                                  body > form > div[id^='thread']"
                           target))))

;; extraction of additional data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti transform-page-text trade-dispatch)

(defmethod transform-page-text :default [text target]
  text)

(defmulti get-post-form trade-dispatch)

(defmethod get-post-form :default [dom target]
  (select dom "form#postform, form[name='post'], #posting_form"))

;; navbar

(defmulti extract-navbar trade-dispatch)

(defn transform-navbar [dom nodes link-p link-f]
  (loop [nodes nodes out []]
    (if-let [node (first nodes)]
      (cond (and (= (.-tagName node) "A") (link-p node))
            (recur (next nodes) (conj out (link-f node)))

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

(defmethod extract-navbar "4chan.org" [dom target]
  (let [navbar-elt (select dom "#boardNavDesktop")]
    (transform-navbar dom (seq (.-childNodes navbar-elt))
                      #(re-matches #".*\.org/..*" (.getAttribute % "href"))
                      #(do
                         (a-> href % (str/replace (.getAttribute % "href") "//boards." io/*scheme*))
                         %))))

(defmethod extract-navbar "iichan.hk" [dom target]
  (let [navbar-elt (select dom ".adminbar")]
    (transform-navbar dom (seq (.-childNodes navbar-elt))
                      #(re-matches #"(.*\.org/..*)|(.*/\.\./..*)" (.getAttribute % "href"))
                      #(let [l (.getAttribute % "href")]
                         (set! (.-href %) 
                               (if (s-in? l "..")
                                 (str io/*scheme* (:domain target)
                                      "/" (second (re-find #"\.\./(.*)/" l)))
                                 (str/replace l "http://" io/*scheme*)))
                         %))))

(defn extract-navbar-menu [dom menu-sel target]
  (let [navbar-elts (select* dom menu-sel)
        nodes (reduce concat
                      (for [ne navbar-elts]
                        (concat 
                         [(.createTextNode dom "[")]
                         (select* ne "a")
                         [(.createTextNode dom "]")])))]
    (transform-navbar dom nodes
                      #(re-matches #"/[^/]+/" (.getAttribute % "href"))
                      #(let [l (.getAttribute % "href")]
                         (set! (.-textContent %) l)
                         (set! (.-href %) (str io/*scheme* (:domain target) 
                                               (.substring l 0 (.lastIndexOf l "/"))))
                         %))))

(defmethod extract-navbar "0chan.hk" [dom target]
  (extract-navbar-menu dom "#overlay_menu div[id^='menu']" target))

(defmethod extract-navbar "2ch.hk" [dom target]
  (extract-navbar-menu dom "#boardNav > .nowrap" target))

(defmethod extract-navbar "ichan.org" [dom target]
  (doseq [a (select* dom "#leftmenudiv a")]
    (set! (.-href a) (str/replace (.-href a) #"http://ichan.org" "")))
  (extract-navbar-menu dom "#leftmenudiv > div" target))

(defmethod extract-navbar "dobrochan.ru" [dom target]
  (when-let [navbar-elt (select dom ".adminbar")]
    (transform-navbar dom (seq (.-childNodes navbar-elt))
                      #(re-matches #"/[a-zA-Z0-9-]+/index.xhtml" (.getAttribute % "href"))
                      #(let [l (.getAttribute % "href")]
                         (set! (.-href %) (str io/*scheme* (:domain target) 
                                               (.substring l 0 (.lastIndexOf l "/"))))
                         %))))

(defmethod extract-navbar :default [dom target]
  (let [navbar-elt (select dom ".navbar, body > p[align='right']")
        navbar-elt (or navbar-elt (select dom ".adminbar"))]
    (when navbar-elt
      (transform-navbar dom (seq (.-childNodes navbar-elt))
                        #(re-matches #"/?[a-zA-Z0-9-]+/?(index\.x?html)?" (.-href %))
                        #(let [l (.getAttribute % "href")]
                           (set! (.-href %) (str io/*scheme* (:domain target) 
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

(defmulti get-captcha #(:trade %2))

(defn get-recaptcha [url target callback]
  (cb-let [dom] (get-thread-dom url target)
    (when dom 
      (when-let [form (get-post-form dom target)]
        (when-let [iframe (select form "noscript iframe")]
          (cb-let [response] (io/get-page (fix-url (.-src iframe) target))
             (if (= (:state response) "ok")
               (let [page (:page response)
                     dom (.parseFromString (new js/DOMParser) (:text page) "text/html")
                     img (select dom "img")
                     link (str "http://www.google.com/recaptcha/api/" (.-src img))
                     challenge (.substring link (inc (.indexOf link "=")))]
                 (callback {:link link :challenge challenge})))))))))

(defmethod get-captcha "4chan.org" [url target callback]
  (get-recaptcha url target callback))

(defmethod get-captcha "7chan.org" [url target callback]
  (get-recaptcha url target callback))

(defmethod get-captcha "2ch.hk" [url target callback]
  (cb-let [response] (io/get-page "http://2ch.hk/makaba/captcha.fcgi")
    (if (= (:state response) "ok")
      (let [page (:page response)
            text (:text page)
            key (.substr text (inc (.lastIndexOf text "\n")))
            link (str "http://i.captcha.yandex.net/image?key=" key)]
        (callback {:link link :challenge key})))))

(defmethod get-captcha "iichan.hk" [url target callback]
  (let [link (let [[_ board thread] (re-find #".*/([^/]*)/[^/]*/(\d+)\..*$" url)
                   script (if (#{"a" "b"} board) "captcha1.pl" "captcha.pl")]
               (if thread
                 (str "http://iichan.hk/cgi-bin/" script "/" board "/?key=res" thread)
                 (let [[_ board] (re-find #"\.hk/([^/]*)" url)
                       script (if (#{"a" "b"} board) "captcha1.pl" "captcha.pl")]
                   (str "http://iichan.hk/cgi-bin/" script "/" board "/?key=mainpage"))))]
    (callback {:link (str link "#i" (.random js/Math))})))

(defmethod get-captcha "410chan.org" [url target callback]
  (let [link (let [[_ board] (re-find #"410chan\.org/([^/]*)/.*" url)]
               (str "http://410chan.org/faptcha.php?board=" board))]
    (callback {:link (str link "#i" (.random js/Math))})))

(defmethod get-captcha "dobrochan.ru" [url target callback]
  (let [link (let [[_ board] (re-find #"dobrochan\.ru/([^/]*)/.*" url)]
               (str "http://dobrochan.ru/captcha/" board "/" (.getTime (new js/Date)) ".png"))]
  (callback {:link link})))

(defmethod get-captcha :default [url target callback]
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

(defmethod get-post-error "4chan.org" [response target] 
  (find-post-error "#errmsg" response target))

(defmethod get-post-error "krautchan.net" [response target])

(defmethod get-post-error "7chan.org" [response target])

(defmethod get-post-error "2ch.hk" [response target] 
  (find-post-error "center > strong" response target))

(defmethod get-post-error "dobrochan.ru" [response target] 
  (find-post-error ".post-error" response target))

(defmethod get-post-error "410chan.org" [response target] 
  (find-post-error "h2" response target))

(defmethod get-post-error :default [response target] 
  (find-post-error "h1" response target))

;; posting form

(defmulti adjust-form trade-dispatch)

(defmethod adjust-form "4chan.org" [form target]
  (set! (.-__parent_key__ form) "resto")
  (set! (.-__captcha_challenge__ form) "recaptcha_challenge_field")
  (let [captcha-line (select form "#captchaFormPart")]
    (set! (.-innerHTML captcha-line) "<td class=\"desktop\">Verification</td>
                                      <td>
                                        <img/><br/>
                                        <input type=\"text\" name=\"recaptcha_response_field\"
                                               autocomplete=\"off\">
                                      </td>"))
  form)

(defmethod adjust-form "7chan.org" [form target]
  (set! (.-__parent_key__ form) "replythread")
  (set! (.-__captcha_challenge__ form) "recaptcha_challenge_field")
  (let [captcha-line (select form "#captcha_label")]
    (set! (.-innerHTML (.-parentNode captcha-line)) "<label id=\"captcha_label\">Captcha</label>
                                                     <img/><br/>
                                                     <input type=\"text\" name=\"recaptcha_response_field\"
                                                           autocomplete=\"off\">"))
  form)

(defmethod adjust-form "2ch.hk" [form target]
  (set! (.-__captcha_challenge__ form) "captcha")
  (let [captcha-line (select form "#captcha_div")]
    (set! (.-innerHTML captcha-line) "<img/><br/>
                                      <input type=\"text\" name=\"captcha_value\"
                                             autocomplete=\"off\">"))
  form)

(defmethod adjust-form "dobrochan.ru" [form target]
  (set! (.-__parent_key__ form) "thread_id")
  form)

(defmethod adjust-form "410chan.org" [form target]
  (set! (.-__parent_key__ form) "replythread")
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

(defn parse-page [page target &{:keys [get-metadata]}]
  (let [error-msg (str "<span class=\"red\">Error loading page: " (:url page))]
    (if (= (:error page) "http_error")
      [(build-error-data (str error-msg " (" (:status page) ")") target)]
      (let [target (with-meta target {:page-index (:index page)})
            text (transform-page-text (:text page) target)
            dom (.parseFromString (new js/DOMParser) text "text/html")
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
