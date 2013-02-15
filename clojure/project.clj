(defproject dark-flow "0.1.0-SNAPSHOT"
  :description "An advanced imageboard aggregator"
  :license {:name "BSD"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [enfocus "1.0.0-SNAPSHOT"]]
  :plugins [[lein-swank "1.4.4"]
            [lein-cljsbuild "0.3.0"]]
  :cljsbuild {
    :builds [{:source-paths ["cljs"]
              :compiler 
              {:output-to "d:/std/home/firefox/current/extensions/dark-flow-ag@jetpack/resources/dark-flow-aggregator/data/frontend.js"
               :optimizations :whitespace
               :externs ["externs.js"]
               :pretty-print true
               }}
             ]}
  :profiles
  {
   :firefox
   {
    :cljsbuild {
     :builds [{:source-paths ["cljs"]
               :compiler 
               {:output-to "../firefox/data/frontend.js"
                :optimizations :advanced
                :externs ["externs.js"]
                :pretty-print false
                }}
              ]}
    }
   })