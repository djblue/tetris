(ns tetris.core)

(def tetrominos
  {:I {:color "#1197dd"
       :dim [4 1]
       :zero [1 0]
       0 [[-1  0] [0  0] [1  0] [2  0]]
       1 [[1 -1] [1  0] [1  1] [1  2]]
       2 [[-1  1] [0  1] [1  1] [2  1]]
       3 [[0  1] [0  0] [0 -1] [0  2]]}
   :O {:color "#fabc26"
       :dim [2 2]
       :zero [0 0]
       0 [[0  0] [0  1] [1  0] [1  1]]
       1 [[0  0] [0  1] [1  0] [1  1]]
       2 [[0  0] [0  1] [1  0] [1  1]]
       3 [[0  0] [0  1] [1  0] [1  1]]}
   :T {:color "#c32ba6"
       :dim [3 2]
       :zero [1 1]
       0 [[0  0] [-1  0] [1  0] [0 -1]]
       1 [[0  0] [0  1] [1  0] [0 -1]]
       2 [[0  0] [-1  0] [1  0] [0  1]]
       3 [[0  0] [-1  0] [0 -1] [0  1]]}
   :J {:color "#2063cf"
       :dim [3 2]
       :zero [1 1]
       0 [[0  0] [-1  0] [-1 -1] [1  0]]
       1 [[0  0] [0  1] [0 -1] [1 -1]]
       2 [[0  0] [-1  0] [1  0] [1  1]]
       3 [[0  0] [-1  1] [0  1] [0 -1]]}
   :L {:color "#f47b14"
       :dim [3 2]
       :zero [1 1]
       0 [[0  0] [-1  0] [1 -1] [1  0]]
       1 [[0  0] [0  1] [0 -1] [1  1]]
       2 [[0  0] [-1  0] [1  0] [-1  1]]
       3 [[0  0] [-1 -1] [0  1] [0 -1]]}
   :S {:color "#73c214"
       :dim [3 2]
       :zero [1 1]
       0 [[0  0] [-1  0] [1 -1] [0 -1]]
       1 [[0  0] [1  0] [0 -1] [1  1]]
       2 [[0  0] [0  1] [1  0] [-1  1]]
       3 [[0  0] [-1 -1] [0  1] [-1  0]]}
   :Z {:color "#d5203b"
       :dim [3 2]
       :zero [1 1]
       0 [[0  0] [-1 -1] [0 -1] [1  0]]
       1 [[0  0] [1 -1] [0  1] [1  0]]
       2 [[0  0] [1  1] [0  1] [-1  0]]
       3 [[0  0] [-1  1] [0 -1] [-1  0]]}})

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