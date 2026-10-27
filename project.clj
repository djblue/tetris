(defproject djblue/tetris "0.1-SNAPSHOT"
  :dependencies [[org.jank-lang.commons/raylib-sys "2026.09-3"]
                 [org.jank-lang.commons/raygui-sys "2026.09-5"]]
  :plugins [[org.jank-lang/lein-jank "2026.10-2"]]
  :middleware [leiningen.jank/middleware]
  :main tetris.main
  :profiles {:base {:jank {:target-dir "target/debug"
                           :optimization-level 0
                           :eager? true}}
             :release {:jank {:target-dir "target/release"
                              :optimization-level 3
                              :debug? false}}
             :dev {:source-paths ["dev"]
                   :dependencies [[djblue/portal "0.68.1"]]}})

