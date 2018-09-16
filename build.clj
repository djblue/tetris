(ns tetris
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [figwheel-sidecar.repl-api :as figwheel]
            [cljs.build.api :as cljs]
            [hiccup.core :refer [html]]
            [clojure.tools.nrepl :as repl]))

(defn app [& body]
  [:html
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:meta {:name "theme-color" :content "#8fbcbb"}]
    [:link {:rel "stylesheet"
            :href "https://fonts.googleapis.com/css?family=Lato|Roboto:300,400"}]
    [:style "html, body { margin: 0; padding: 0; font-family: Roboto; }"]]
   [:body [:div {:id "app"}]
    body
    [:script {} "tetris.core.render()"]]])

(defn http [req]
  {:body (str "<!DOCTYPE html>"
              (html (app [:script {:src "tetris.js" :type "text/javascript"}])))})

(def figwheel-config
  {:figwheel-options
   {:ring-handler 'tetris/http
    :nrepl-port 7890
    :nrepl-middleware ['cemerick.piggieback/wrap-cljs-repl]}
   :build-ids ["dev"]
   :all-builds
   [{:id "dev"
     :figwheel {:on-jsload 'tetris.core/render
                :open-urls ["http://localhost:3449/index.html"]
                :websocket-host :js-client-host}
     :source-paths ["src"]
     :compiler {:main 'tetris.core
                :asset-path ""
                :output-to "resources/public/tetris.js"
                :output-dir "resources/public"}}]})

(defn index-html []
  (cljs/build "src"
              {:output-to "target/public/tetris.js"
               :output-dir "target/public"
               :pretty-print false
               :optimizations :advanced})
  (str
   "<!DOCTYPE html>"
   (html (app [:script (slurp "target/public/tetris.js")]))))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(def cli-options
  [["-a" "--app" "only output index.html"]
   ["-h" "--help" "output usage information"]])

(defn help [options]
  (->> ["Build app."
        ""
        "Usage: build [options]"
        ""
        "Options:"
        ""
        options
        ""]
       (str/join \newline)))

(defn main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:help options)     (exit 0 (help summary))
      errors              (exit 1 (str (first errors) "\nSee \"dots --help\""))
      (:app options)      (println (index-html))
      :else               (figwheel/start-figwheel! figwheel-config))))

(apply main *command-line-args*)
