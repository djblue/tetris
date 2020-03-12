(ns tetris.gamepad
  (:require [clojure.set :as set]))

(def xbox-buttons
  [:button-a
   :button-b
   :button-x
   :button-y
   :bumper-left
   :bumper-right
   :trigger-left
   :trigger-right
   :select
   :start
   :stick-left
   :stick-right
   :dpad-up
   :dpad-down
   :dpad-left
   :dpad-right])

(def xbox-buttons->action
  {:dpad-right {:type :player-shift :direction :right}
   :dpad-left {:type :player-shift :direction :left}
   :dpad-down {:type :player-shift-down :source :user}
   :dpad-up {:type :player-rotate :direction :right}

   :start {:type :player-toggle-pause}

   :bumper-left {:type :player-rotate :direction :left}
   :bumper-right {:type :player-rotate :direction :right}

   :button-a {:type :player-drop}
   :stick-right {:type :player-drop}
   :button-b {:type :player-shift-down :source :user}

   :trigger-left {:type :player-hold}})

(defn apply-deadzone [value]
  (if (< (js/Math.abs value) 0.75) 0 value))

(def xbox-sticks->action
  {:left-stick-horizonal
   #(cond
      (< % 0) :dpad-left
      (> % 0) :dpad-right)
   :left-stick-vertical
   #(cond
      (< % 0) :dpad-up
      (> % 0) :dpad-down)
   :right-stick-horizonal #()
   :right-stick-vertical #()})

(defn axes->buttons [controller]
  (let [axes (.-axes controller)]
    (reduce-kv
     (fn [actions index k]
       (let [value (apply-deadzone (aget axes index))
             f (k xbox-sticks->action)
             action (f value)]
         (if-not action
           actions
           (conj actions action))))
     #{}
     [:left-stick-horizonal
      :left-stick-vertical
      :right-stick-horizonal
      :right-stick-vertical])))

(defn conntroller->buttons [controller]
  (let [buttons (.-buttons controller)]
    (reduce-kv
     (fn [actions index button]
       (if-not (.-pressed (aget buttons index))
         actions
         (conj actions button)))
     #{}
     xbox-buttons)))

(defn controller->action [state gamepad]
  (let [buttons (set/union
                 (axes->buttons gamepad)
                 (conntroller->buttons gamepad))
        done-pressing (set/difference (set (keys state)) buttons)
        state (apply dissoc state done-pressing)
        holding (keep (fn [[k c]] (when (> c 17) k)) state)
        state (merge state (zipmap holding (repeat 13)))]
    [(merge-with + state (zipmap buttons (repeat 1)))
     (map xbox-buttons->action (concat done-pressing holding))]))

(defn get-gamepad []
  (aget (js/navigator.getGamepads) 0))
