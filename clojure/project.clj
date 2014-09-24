(defproject dark-flow "0.1.3-SNAPSHOT"
  :description "An advanced imageboard aggregator"
  :license {:name "BSD"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [enfocus "1.0.0-SNAPSHOT"]]
  :plugins [[lein-swank "1.4.4"]
            [lein-shell "0.1.0"]
            [lein-cljsbuild "0.3.0"]]
  :prep-tasks [["shell" "echo" "mugichka"]]
  :cljsbuild {
    :builds [{:source-paths ["cljs"]
              :compiler 
              {:output-to #_"../gae/war/frontend.js"  "d:/std/home/firefox/australis/extensions/dark-flow-ag@jetpack/resources/dark-flow-aggregator/data/frontend.js"
               :optimizations :whitespace
               :externs ["externs.js"]
               :pretty-print true
               }}
             ]}
  :profiles
  {
   :production
   {
    :cljsbuild {
     :builds [{:source-paths ["cljs"]
               :compiler 
               {:output-to "frontend.js"
                :optimizations :advanced
                :externs ["externs.js"]
                :pretty-print false
                }}
              ]}
    }
   })