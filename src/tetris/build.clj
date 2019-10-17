(ns tetris.build
  (:require [figwheel.main.api :as figwheel]
            [cljs.build.api :as cljs]
            [cljs.repl.browser :as browser]
            [cljs.repl :as repl]
            [hiccup.core :refer [html]]
            [org.httpkit.server :as kit]
            [clojure.core.server :as server]))

(defn app [& body]
  (str
   "<!DOCTYPE html>"
   (html
    [:html
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:link {:rel "stylesheet"
              :href "https://fonts.googleapis.com/css?family=Lato|Roboto:300,400"}]
      [:style "html, body { margin: 0; padding: 0; font-family: Roboto; }"]]
     [:body [:div {:id "app"}]
      body
      [:script {} "tetris.core.main()"]]])))

(defn http [req]
  {:headers {"Content-Type" "text/html"}
   :body (app [:script {:src "cljs-out/dev-main.js" :type "text/javascript"}])})

(defn ws [req]
  (case (:uri req)
    "/ws"
    (kit/with-channel req channel
      (kit/on-close channel (fn [status] (println "channel closed: " status)))
      (kit/on-receive channel (fn [data] ;; echo it back
                                (kit/send! channel data))))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "hello!"}))

(defn index-html []
  (cljs/build "src"
              {:output-to "target/dist/tetris.js"
               :output-dir "target/dist"
               :pretty-print false
               :optimizations :advanced})
  (app [:script (slurp "target/dist/tetris.js")]))

(defn -main [& args]
  (case (first args)
    "server"
    (do
      (server/start-server
       {:accept 'clojure.core.server/io-prepl
        :address "localhost"
        :port 5555
        :name "cljoure"})
      (kit/run-server #'ws {:port 8080})
      (figwheel/start
       {:id "dev"
        :options {:main 'tetris.core}
        :config {:mode :serve
                 :ring-handler 'tetris.build/http}})
      (let [env (browser/repl-env :launch-browser false)]
        (server/start-server
         {:accept 'cljs.core.server/io-prepl
          :address "localhost"
          :port 5556
          :name "clojurescript"
          ; TODO: fix this
          ; figwheel stack traces cause issues
          ;:accept 'cljs.server.browser/prepl
          ;:args [:repl-env (figwheel/repl-env "dev")]
          :args [:repl-env env]})
        (repl/repl env)))

    (println (index-html))))

