(defproject adventure.txt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins  [[lein-ring "0.8.11"]]
  :ring  { :handler adventure.txt/handler }
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [liberator "0.13"]
                 [ring/ring-core "1.4.0"]
                 [clojurewerkz/neocons "3.1.0"]
                 [compojure "1.4.0"]
                 [ring-cors "0.1.7"]] )
