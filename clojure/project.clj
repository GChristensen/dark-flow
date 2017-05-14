(defproject dark-flow "0.1.3-SNAPSHOT"
  :description "An advanced imageboard aggregator"
  :license {:name "BSD"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [enfocus "2.1.1"]
                 [org.clojure/clojurescript "1.7.228"]]
  :plugins [[lein-swank "1.4.4"]
            [lein-shell "0.1.0"]
            [lein-cljsbuild "1.1.3"]]
  :prep-tasks [["shell" "echo" "mugichka"]]
  :cljsbuild {
    :builds [#_{:source-paths ["cljs"]
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
               {;:main 'kuroi.frontend
                :output-to "frontend.js"
                :optimizations :advanced
                :externs ["externs.js"]
                :pretty-print false
                }}
              ]}
    }
   :debug
   {
    :cljsbuild {
     :builds [{:source-paths ["cljs"]
               :compiler 
               {:output-to #_"../gae/war/frontend.js"  "D:/std/home/firefox/australis/extensions/@dark-flow-imageboard-browser/data/frontend.js"
                :optimizations :whitespace ;:advanced
                :externs ["externs.js"]
                :pretty-print true;false
                }}
              ]}
    }

   })