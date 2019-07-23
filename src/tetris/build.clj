(ns tetris.build
  (:require [figwheel.main.api :as figwheel]
            [cljs.build.api :as cljs]
            [hiccup.core :refer [html]]))

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
    (figwheel/start
     {:id "dev"
      :options {:main 'tetris.core}
      :config {:ring-handler 'tetris.build/http}})
    (println (index-html))))

