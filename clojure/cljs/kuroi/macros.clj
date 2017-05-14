(ns kuroi.macros)

;; 'callback' continuation let
(defmacro cb-let [[& args] call & body]
  (concat call [`(fn [~@args] ~@body)]))

;; performs the call only when pred is true
(defmacro cb-let? [pred [& args] call & body]
  (let [gcallback (gensym)
        n-args (count args)]
    `(let [~gcallback (fn [~@args] ~@body)]
       (if ~pred
         ~(concat call [gcallback])
         (~gcallback ~@(repeat n-args nil))))))

(defmacro with-page [page [& args] & body]
  `(cb-let [html#] (~(symbol (str "bk/get-" page "-html")) ~@args)
     (~'setup-themed-vars (:theme ~'settings))
     (cookies/remove "theme")
     (cookies/set "theme" (:theme ~'settings) 31557600 "/")
     (.appendChild (.-body js/document) (.querySelector html# "#content"))
     (~'load-styles (:theme ~'settings) ~@(if (= page 'settings) [:settings true] []))
     ~@body))

(defmacro log [o]
  `(.log js/console ~o))

(defmacro a-> [attr obj value]
  `(set! (~(symbol (str ".-" attr)) ~obj) ~value))

(defmacro a- [attr obj]
  `(~(symbol (str ".-" attr)) ~obj))

(defmacro s-in? [s e]
  `(>= (.indexOf ~s ~e) 0))

(defmacro if-content [content]
  `(fn [node#]
     (if-let [c# ~content]
       ((ef/html-content c#) node#)
       node#)))

(defmacro remove-when [cond]
  `(fn [node#]
     (if ~cond 
       ((ef/remove-node) node#)
       node#)))

(defmacro do-when [cond f]
  `(fn [node#]
     (if ~cond 
       (~f node#)
       ((ef/remove-node) node#))))

;; clone-for modified to partition its work with setTimeout
(defmacro clone-for [[sym lst] & forms]
  `(fn [pnod#]
      (let [div# (ef/create-hidden-dom
                    (. js/document (~(symbol "createDocumentFragment"))))]
        ((fn breaker# [lst#]
           (when-let [~sym (first lst#)]
             (ef/at div# (ef/append (. pnod# (~(symbol "cloneNode") true))))
             (ef/at (goog.dom/getLastElementChild div#) ~@forms)
             (if (next lst#)
               (js/setTimeout #(breaker# (rest lst#)) 1)
               (do
                 (ef/log-debug div#)
                 (ef/at
                  pnod#
                  (ef/do-> (ef/after (ef/remove-node-return-child div#))
                                       (ef/remove-node)))))))
         ~lst))))

;; if not fallbac, acts on live node, mimics clone-for otherwise
(defmacro clone-for-live-node [live-node window [sym lst] & forms]
  `(fn [pnod#]
     (let [div# ~live-node
           fallback# (not div#)
           do-clone#
           (fn []
             (let [wnd# ~window
                   hidden# (ef/create-hidden-dom
                            (. js/document (~(symbol "createDocumentFragment"))))
                   div# (if fallback#
                          hidden#
                          div#)
                   show-nodes# (fn [nodes# where#]
                                 (doseq [n# nodes#]
                                   (.removeChild (.-parentNode n#) n#)
                                   (.appendChild where# n#)))]
               (when (not fallback#) (ef/at pnod# (ef/remove-node)))
               ((fn breaker# [lst# que#]
                  (when-let [~sym (first lst#)]
                    (let [clone# (. pnod# (~(symbol "cloneNode") true))]
                      (ef/at hidden# (ef/append clone#))
                      (ef/at (goog.dom/getLastElementChild hidden#) ~@forms)
                      (let [que# (if (and (not fallback#) (> (count que#) wnd#))
                                   (do (show-nodes# (seq que#) div#)
                                       (conj cljs.core.PersistentQueue/EMPTY clone#))
                                   (when (not fallback#) (conj que# clone#)))]
                        (if (next lst#)
                          (js/setTimeout #(breaker# (rest lst#) que#)
                                         1)
                          (if fallback#
                            (do (ef/log-debug div#)
                                (ef/at
                                 pnod#
                                 (ef/do-> (ef/after (ef/remove-node-return-child div#))
                                          (ef/remove-node))))
                            (do (show-nodes# (seq que#) div#)
                                (ef/at
                                 hidden#
                                 (ef/remove-node)))
                            ))))))
                ~lst cljs.core.PersistentQueue/EMPTY)))]
       (if (not fallback#)
         (js/setTimeout do-clone# 10)
         (do-clone#)))))
