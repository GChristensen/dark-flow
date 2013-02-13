(ns kuroi.macros)

;; 'callback' continuation let
(defmacro cb-let [[& args] call & body]
  (concat call [`(fn [~@args] ~@body)]))

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
       ((em/html-content c#) node#)
       node#)))

(defmacro remove-when [cond]
  `(fn [node#]
     (if ~cond 
       ((em/remove-node) node#)
       node#)))

(defmacro do-when [cond f]
  `(fn [node#]
     (if ~cond 
       (~f node#)
       ((em/remove-node) node#))))

;; clone-for modified to partition its work with setTimeout
(defmacro clone-for [[sym lst] & forms]
  `(enfocus.core/chainable-standard
    (fn [pnod#]
      (let [div# (enfocus.core/create-hidden-dom
                    (. js/document (~(symbol "createDocumentFragment"))))]
        ((fn breaker# [lst#]
           (when-let [~sym (first lst#)]
             (enfocus.macros/at div# (enfocus.macros/append (. pnod# (~(symbol "cloneNode") true))))
             (enfocus.macros/at (goog.dom/getLastElementChild div#) ~@forms)
             (if (next lst#)
               (js/setTimeout #(breaker# (rest lst#)) 1)
               (do
                 (enfocus.core/log-debug div#)
                 (enfocus.macros/at
                  pnod#
                  (enfocus.macros/do-> (enfocus.macros/after (enfocus.core/remove-node-return-child div#))
                                       (enfocus.macros/remove-node)))))))
         ~lst)))))