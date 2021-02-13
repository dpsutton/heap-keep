(ns dpsutton.heap-keep
  (:import java.util.PriorityQueue))

n(defn heap-keep
  [threshold j-u-compare-f]
  (fn heap-keep-reducer
    ([] (PriorityQueue. 30 j-u-compare-f))
    ([^PriorityQueue q]
     (loop [acc []]
       (if-let [x (.poll q)]
         (recur (conj acc x))
         (reverse acc))))
    ([^PriorityQueue q item]
     (if (>= (.size q) threshold)
       (let [smallest (.peek q)]
         (if (pos? (j-u-compare-f item smallest))
           (doto q
             (.poll)
             (.offer item))
           q))
       (doto q
         (.offer item))))))

(comment
  (def inputs (shuffle (map (fn [n] {:data n}) (range 1e6))))
  (def sorted-low->high (sort-by :data inputs))
  (def sorted-high->low (sort-by :data #(compare %2 %1) inputs))

  (def result-count 1000)

  (prof/profile
    (doseq [inputs [inputs sorted-low->high sorted-high->low]]
      (time
n        (->> inputs
             (map (fn [{:keys [data]}] {:data data :score data}))
             (filter (fn [x] (> (:score x) 500000)))
             (sort-by :score #(compare %2 %1))
             (take result-count)))))

  (prof/profile
    {:event :alloc}
    (dotimes [_ 50]
      (let [xf (comp (map (fn [{:keys [data]}] {:data data :score data}))
                     (filter (fn [x] (> (:score x) 500000))))]
        (->> (transduce xf conj inputs)
             (sort-by :score #(compare %2 %1))
             (take result-count)))))

  (prof/profile
    {:event :alloc}
    (dotimes [_ 50]
        (let [xf (comp (map (fn [{:keys [data]}] {:data data :score data}))
                       (filter (fn [x] (> (:score x) 500000))))
              rf (heap-keep result-count (fn [x y] (compare (:score x) (:score y))))]
          (transduce xf rf inputs)))))

(comment
  (require '[clj-async-profiler.core :as prof])

  (prof/profile (dotimes [i 10000] (reduce + (range i))))

  (prof/serve-files 8080)
  (prof/stop)
  (prof/start {:event :alloc})
  (prof/list-event-types)
)
