(ns ^:figwheel-hooks tetris.core
  (:require [tetris.github  :refer [view-source]]
            [tetris.gamepad :as gp]
            [clojure.string :as s]
            [reagent.core :as r]))

(def tetrominos
  {:I {:color "#1197dd"
       :dim [4 1]
       :zero [1 0]
       0 [[-1 +0] [+0 +0] [+1 +0] [+2 +0]]
       1 [[+1 -1] [+1 +0] [+1 +1] [+1 +2]]
       2 [[-1 +1] [+0 +1] [+1 +1] [+2 +1]]
       3 [[+0 +1] [+0 +0] [+0 -1] [+0 +2]]}
   :O {:color "#fabc26"
       :dim [2 2]
       :zero [0 0]
       0 [[+0 +0] [+0 +1] [+1 +0] [+1 +1]]
       1 [[+0 +0] [+0 +1] [+1 +0] [+1 +1]]
       2 [[+0 +0] [+0 +1] [+1 +0] [+1 +1]]
       3 [[+0 +0] [+0 +1] [+1 +0] [+1 +1]]}
   :T {:color "#c32ba6"
       :dim [3 2]
       :zero [1 1]
       0 [[+0 +0] [-1 +0] [+1 +0] [+0 -1]]
       1 [[+0 +0] [+0 +1] [+1 +0] [+0 -1]]
       2 [[+0 +0] [-1 +0] [+1 +0] [+0 +1]]
       3 [[+0 +0] [-1 +0] [+0 -1] [+0 +1]]}
   :J {:color "#2063cf"
       :dim [3 2]
       :zero [1 1]
       0 [[+0 +0] [-1 +0] [-1 -1] [+1 +0]]
       1 [[+0 +0] [+0 +1] [+0 -1] [+1 -1]]
       2 [[+0 +0] [-1 +0] [+1 +0] [+1 +1]]
       3 [[+0 +0] [-1 +1] [+0 +1] [+0 -1]]}
   :L {:color "#f47b14"
       :dim [3 2]
       :zero [1 1]
       0 [[+0 +0] [-1 +0] [+1 -1] [+1 +0]]
       1 [[+0 +0] [+0 +1] [+0 -1] [+1 +1]]
       2 [[+0 +0] [-1 +0] [+1 +0] [-1 +1]]
       3 [[+0 +0] [-1 -1] [+0 +1] [+0 -1]]}
   :S {:color "#73c214"
       :dim [3 2]
       :zero [1 1]
       0 [[+0 +0] [-1 +0] [+1 -1] [+0 -1]]
       1 [[+0 +0] [+1 +0] [+0 -1] [+1 +1]]
       2 [[+0 +0] [+0 +1] [+1 +0] [-1 +1]]
       3 [[+0 +0] [-1 -1] [+0 +1] [-1 +0]]}
   :Z {:color "#d5203b"
       :dim [3 2]
       :zero [1 1]
       0 [[+0 +0] [-1 -1] [+0 -1] [+1 +0]]
       1 [[+0 +0] [+1 -1] [+0 +1] [+1 +0]]
       2 [[+0 +0] [+1 +1] [+0 +1] [-1 +0]]
       3 [[+0 +0] [-1 +1] [+0 -1] [-1 +0]]}})

(defn rotate [r] (-> r inc (mod 4)))

(defn get-positions [player]
  (let [{:keys [type x y r]} player
        tetromino (type tetrominos)
        {:keys [color]} tetromino]
    (->> (get tetromino r)
         (map (fn [[i j]]
                [[(+ i x) (+ j y)] color]))
         (into {}))))

(defn full-rows [positions]
  (->> positions
       (reduce (fn [acc [pair]]
                 (merge-with + acc {(second pair) 1})) {})
       (filter (fn [[_ v]] (= v 10)))
       (map first)
       sort))

(defn shift-row [positions row]
  (->> positions
       (filter (fn [[pair]] (not (= (second pair) row))))
       (map (fn [[[i j] v]]
              [[i (if (< j row) (inc j) j)] v]))
       (into {})))

(defn next-level [level]
  (* 5 (/ (* level (inc level)) 2)))

(defn update-score [world]
  (let [{:keys [positions lines score level]} world
        rows (full-rows positions)
        new-positions (reduce shift-row positions rows)]
    (merge world
           {:positions new-positions
            :lines (+ lines (count rows))
            :level (if (>= lines (next-level (inc level)))
                     (inc level)
                     level)
            :score (+ score
                      (* (inc level)
                         (case (count rows) 0 0 1 100 2 300 3 500 4 800)))})))

(defn collision? [world positions]
  (some #(contains? positions (first %)) (:positions world)))

(defn bottom-out? [world positions]
  (or (collision? world positions)
      (not (< (apply max (map second (map first positions)))
              (:height world)))))

(defn projection [world]
  (let [new-player (update (:player world) :y inc)
        positions (get-positions new-player)]
    (if (bottom-out? world positions)
      (get-positions (:player world))
      (recur (assoc world :player new-player)))))

(defn out-of-bounds [world dim positions]
  (let [width (dec ((case dim :x :width :y :height) world))
        values (map (case dim :x first :y second) (map first positions))
        min-value (apply min values)
        max-value (apply max values)]
    (cond
      (< min-value 0)
      (- min-value)
      (> max-value width)
      (- width max-value)
      :else 0)))

(defn check-pause [f]
  (fn [world action]
    (cond
      (= (:type action) :player-toggle-pause) (update world :pause? not)
      (:pause? world) world
      :else (f world action))))

(defn player-shift [world action]
  (let [{:keys [player]} world
        shift (case (:direction action) :right inc :left dec)
        new-player (update player :x shift)
        positions (get-positions new-player)]
    (if (and (not (collision? world positions))
             (= 0 (out-of-bounds world :x positions)))
      (assoc world :player new-player)
      world)))

(defn game-over? [world]
  (collision? world (get-positions (:player world))))

(defn next-piece
  ([world]
   (let [[type & next-type] (:next-type world)]
     (next-piece world type next-type)))
  ([world type]
   (next-piece world type (:next-type world)))
  ([world type next-type]
   (merge world
          {:next-type next-type
           :player {:x 5 :y 1
                    :type type
                    :r (first (shuffle (range 4)))}})))

(defn start []
  (let [types (keys tetrominos)]
    (next-piece
     {:width 10
      :height 24
      :pause? false
      :lines 0
      :level 0
      :positions {}
      :hold nil
      :can-hold? true
      :next-type (apply concat (repeatedly #(-> tetrominos keys shuffle)))})))

(defn check-game-over [f]
  (fn [world action]
    (cond
      (= (:type action) :player-restart) (start)
      (game-over? world) world
      :else (f world action))))

(defn soft-drop-score [world action]
  (if (= (:source action) :user)
    (update world :score inc) world))

(defn player-shift-down [world action]
  (let [{:keys [player next-type]} world
        new-player (update player :y inc)
        positions (get-positions new-player)]
    (-> (if-not (bottom-out? world positions)
          (assoc world :player new-player)
          (-> world
              (next-piece)
              (update :positions merge (get-positions player))
              (assoc :can-hold? true)))
        (soft-drop-score action)
        update-score)))

(defn force-in-bounds [world player]
  (let [positions (get-positions player)
        bounds (out-of-bounds world :x positions)]
    (if (and (not (collision? world positions))
             (= 0 bounds)
             (= 0 (out-of-bounds world :y positions)))
      (assoc world :player player)
      (let [new-player (update player :x #(+ % bounds))
            positions (get-positions new-player)]
        (if (and (not (collision? world positions))
                 (= 0 (out-of-bounds world :y positions)))
          (assoc world :player new-player)
          world)))))

(defn player-rotate [world action]
  (let [{:keys [player]} world
        rotate (case (:direction action) :right inc :left dec)]
    (force-in-bounds world (update player :r #(-> % rotate (mod 4))))))

(defn player-drop
  ([world action] (player-drop world action 0))
  ([world action lines]
   (if (game-over? world)
     world
     (let [w (player-shift-down world action)]
       (if (< (:y (:player w))
              (:y (:player world)))
         (update w :score + (* 2 lines))
         (recur w action (inc lines)))))))

(defn player-hold [world _]
  (if (:can-hold? world)
    (merge
     (if-let [hold (:hold world)]
       (next-piece world hold)
       (next-piece world))
     {:can-hold? false
      :hold (get-in world [:player :type])})
    world))

(defn player-reset [world action]
  (force-in-bounds
   world
   (merge (:player world) (select-keys action [:x]))))

(defn action->fn [action]
  (case (:type action)
    :player-shift player-shift
    :player-shift-down player-shift-down
    :player-rotate player-rotate
    :player-drop player-drop
    :player-hold player-hold
    :player-reset player-reset
    nil))

(def update-player
  (-> (fn [world action]
        (if-let [f (action->fn action)] (f world action) world))
      check-pause
      check-game-over))

(def key-map
  [{:doc "Move Right"
    :codes #{"ArrowRight" "KeyL"}
    :keys ["L" "→"]
    :dispatch {:type :player-shift :direction :right}}
   {:doc "Move Left"
    :codes #{"ArrowLeft" "KeyH"}
    :keys ["H" "←"]
    :dispatch {:type :player-shift :direction :left}}
   {:doc "Soft Drop"
    :codes #{"ArrowDown" "KeyJ"}
    :keys ["J" "↓"]
    :dispatch {:type :player-shift-down :source :user}}
   {:doc "Rotate Right"
    :codes #{"ArrowUp" "KeyK"}
    :keys ["K" "↑"]
    :dispatch {:type :player-rotate :direction :right}}
   {:doc "Rotate Left"
    :codes #{"KeyZ"}
    :keys ["Z"]
    :dispatch {:type :player-rotate :direction :left}}
   {:doc "HOLD"
    :codes #{"KeyC"}
    :keys ["C"]
    :dispatch {:type :player-hold}}
   {:doc "HARD DROP"
    :codes #{"Space"}
    :keys ["SPACE"]
    :dispatch {:type :player-drop}}
   {:doc "Pause"
    :codes #{"Escape"}
    :keys ["Esc"]
    :dispatch {:type :player-toggle-pause}}])

(defn key-map->dispatch-table [key-map]
  (reduce
   (fn [table {:keys [codes dispatch]}]
     (into table (map #(-> [% dispatch]) codes)))
   {}
   key-map))

(def keydown->action (key-map->dispatch-table key-map))

(defn css [style & children]
  (into [:div {:style style}] children))

(defn block [style]
  [css (merge {:height "100%"
               :width "100%"
               :box-sizing :border-box
               :padding "25%"} style)
   [css {:height "100%"
         :border-radius 1
         :mix-blend-mode :darken
         :background "rgba(0,0,0,0.15)"}]])

(defn help [props]
  (let [{:keys [key-map]} props]
    [:table
     {:style
      {:font-size "1.1rem"
       :margin "0 auto"
       :text-align :left}}
     (into [:tbody]
           (map #(let [{:keys [doc keys]} %]
                   [:tr
                    [:th (s/join ", " keys)]
                    [:td [css {:width 20}]]
                    [:td doc]])
                key-map))]))

(defn board [props]
  (let [{:keys [scale width height positions projection border? on-update]} props
        size (str scale "vh")]
    [:table
     {:cell-spacing "0"
      :cell-padding "0"
      :style {:display :inline-block
              :border-collapse :collapse}}
     [:tbody
      (for [i (range height)]
        [:tr {:key i}
         (for [j (range width)]
           [:td {:key j
                 :style
                 {:width size
                  :height size
                  :border (when (or border? (get positions [j i])) "1px solid #202020")
                  :background (when border? (if (even? (+ i j)) "#2e2e2e" "#2b2b2b"))}
                 :on-mouse-enter
                 (fn []
                   (when (fn? on-update)
                     (on-update {:type :player-reset :x j})))}
            (if-let [color (get positions [j i])]
              [block {:background color}]
              (when-let [color (get projection [j i])]
                [block {:background color :opacity 0.25}]))])])]]))

(defn panel [& children]
  (into [css
         {:border "2px solid rgba(255,255,255, 0.1)"
          :padding 10
          :box-sizing :border-box}] children))

(defn info [label & children]
  (into [css
         {:border "3px solid #2e2e2e"
          :background "#2b2b2b"
          :color :white
          :box-sizing :border-box
          :text-align :center
          :width 120
          :padding 10}
         [css {:font-weight :bold
               :margin-bottom 5
               :text-transform :uppercase} label]] children))

(defn button [props & children]
  [css
   {:line-height "1.1rem"}
   (into [:button
          (merge {:style
                  {:outline :none
                   :min-width 200
                   :border :none
                   :cursor :pointer
                   :padding 10
                   :font-size "1.1rem"
                   :background :white}} props)] children)])

(defn overlay [& children]
  [css {:position :absolute
        :display :flex
        :align-items :center
        :justify-content :center
        :flex-direction :column
        :top 0
        :left 0
        :right 0
        :bottom 0
        :background "rgba(0,0,0,0.5)"
        :color :white
        :font-size "3em"
        :z-index "1"
        :font-weight :bold}
   (into [css
          {:width "100vw"
           :position :relative
           :text-align :center
           :padding 60
           :box-sizing :border-box
           :background "rgba(0,0,0,0.75)"
           :border-top "3px solid #2e2e2e"
           :border-bottom "3px solid #2e2e2e"}] children)])

(defn preview [t]
  (let [[width height] (get-in tetrominos [t :dim])
        [x y] (get-in tetrominos [t :zero])
        positions (get-positions {:type t :x x :y y :r 0})]
    [board {:scale 1.8
            :width width
            :height height
            :positions positions}]))

(defn hold-info [world on-hold]
  (let [{:keys [hold]} world]
    [panel
     [:div
      {:on-click on-hold
       :style {:cursor :pointer}}
      [info
       "Hold"
       [css {:height 10}]
       (when-not (nil? hold) (preview hold))]]]))

(defn score-info [world]
  (let [{:keys [score level lines]} world]
    [panel
     [info "score" (or score 0)]
     [css {:padding-top 10}]
     [info "level" (inc level)]
     [css {:padding-top 10}]
     [info "lines" lines]]))

(defn next-info [world]
  [panel
   (into
    [info "Next"
     [css {:height 10}]]
    (->> (:next-type world)
         (take 5)
         (map #(preview %))
         (interpose [css {:height 10}])))])

(def gutter (constantly [css {:width "1vw"}]))

(defn with-timer [interval f]
  (let [state (atom nil)]
    (r/create-class
     {:component-will-mount
      #(reset! state
               [(js/window.setInterval f interval) interval])
      :component-will-receive-props
      (fn [_ args]
        (let [[handle interval] @state
              [_ new-interval f] args]
          (when-not (= interval new-interval)
            (.clearInterval js/window handle)
            (reset! state
                    [(.setInterval js/window f new-interval) new-interval]))))
      :component-will-unmount
      #(let [[handle] @state]
         (js/window.clearInterval handle))
      :display-name "timer"
      :reagent-render (constantly nil)})))

(defn with-listener [k h]
  (let [handler #(h %)]
    (r/create-class
     {:component-will-mount
      #(.addEventListener js/document (name k) handler)
      :component-will-unmount
      #(.removeEventListener js/document (name k) handler)
      :display-name "with-listener"
      :reagent-render (constantly nil)})))

(def
  frames
  (mapv
   #(* 1000 (/ % 60.0988))
   [48 43 38 33 28 23 18 13 8 6 5 5 5 4 4 4 3 3 3 2 2 2 2 2 2 2 2 2 2 1]))

(def controller-state (atom {}))

(defn game [props]
  (let [{:keys [world on-update]} props
        {:keys [width height player positions]} world]
    [:div
     [with-listener
      :keydown
      #(when-let [action (keydown->action (.-code %))] (on-update action))]
     [with-timer
      16
      #(when-let [gamepad (gp/get-gamepad)]
         (let [[state actions]
               (gp/controller->action @controller-state gamepad)]
           (dorun (map on-update actions))
           (reset! controller-state state)))]
     [with-timer
      (nth frames (:level world))
      #(on-update {:type :player-shift-down})]
     (if (:pause? world)
       [overlay
        [:div "PAUSED"]
        [css {:height 20}]
        [button {:on-click #(on-update {:type :player-toggle-pause})} "RESUME"]
        [css {:height 20}]
        [button {:on-click #(on-update {:type :player-restart})} "RESTART"]
        [css {:height 20}]
        [help {:key-map key-map}]])
     (if (game-over? world)
       [overlay
        [:div "GAME OVER"]
        [button {:on-click #(on-update {:type :player-restart})} "RESTART"]])
     [css
      {:justify-content :center
       :display :flex
       :filter (when (:pause? world) "blur(50px)")}
      [css
       {:display :flex
        :justify-content :space-between
        :flex-direction :column}
       [hold-info world #(on-update {:type :player-hold})]
       [score-info world]]
      [gutter]
      [:div
       {:on-click #(on-update {:type :player-drop})
        :on-context-menu #(do
                            (.preventDefault %)
                            (on-update {:type :player-hold}))}
       [board
        {:width width
         :height height
         :border? true
         :scale (/ 90 height)
         :projection (projection world)
         :positions (merge positions (get-positions player))
         :on-update on-update}]]
      [gutter]
      [css
       {:display :flex
        :justify-content :space-between
        :flex-direction :column}
       [next-info world]]]]))

(defn start-screen [on-update]
  [overlay
   [:div "WELCOME"]
   [css {:height 20}]
   [button {:on-click #(on-update {:type :player-restart})} "START"]
   [css {:height 20}]
   [help {:key-map key-map}]])

(defonce world (r/atom nil))

(defn app []
  (let [on-update #(swap! world update-player %)]
    [css
     {:position :relative
      :user-select :none
      :background "#202020"
      :display :flex
      :flex-direction :column
      :justify-content :center
      :height "100vh"}
     (if-let [world @world]
       [game {:world world :on-update on-update}]
       [start-screen on-update])]))

(defn ^:after-load render []
  (r/render
   [:div
    [view-source
     {:color "#202020"
      :background (:color (rand-nth (vals tetrominos)))}]
    [app]]
   (. js/document (getElementById "app"))))

(defn ^:export main [] (render))

