(ns clj-clapps.util
  (:require  [clojure.test :as t]))


(defn alike? [x y]
  (cond
    (map? x) (and (map? y)
                  (every? (fn [[k v]] (if (fn? v) (v (get y k)) (alike? v (get y k)))) x))
    (coll? x) (and (coll? y) (or (and (empty? x) (empty? y))
                                 (and (alike? (first x) (first y)) (alike? (rest x) (rest y)))))
    (fn? x) (if (fn? y) (identical? x y) (x y))
    :else (= x y)))

(defmethod t/assert-expr 'like? [msg form]
  (let [expected (nth form 1)
        expr (nth form 2)]
    `(let [expected# ~expected
           actual# ~expr
           res# (alike? expected# actual#)]
       (if (true? res#)
         (t/do-report {:type :pass :message ~msg :expected expected# :actual actual#})
         (t/do-report {:type :fail :message ~msg :expected expected# :actual actual#})))))
