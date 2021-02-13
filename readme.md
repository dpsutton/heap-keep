## Heap keep

A common pattern in the wild is to see some kind of transducing context that precedes a `sort` and then a `(take N)`. The sorting usually must go after the transducing context and requires the realization of the entire collection, sorting, and then taking the subset that you care about. This reducing function uses a min heap as the accumulator and evicts the smallest element with newer higher ranked elements when full.

| Input | naive seq | transduce then sort | heap-keep transduction|
|-------|-----------|---------------------|-----------------------|
| shuffled | 3027 ms  | 3191 ms | 273 ms |
| low->high | 710 ms | 488 ms | 1226 ms |
| high->low | 1079 ms | 615 ms | 262 ms |


Considering the shuffled array as the average case and the ordered arrays as best case and worst case, you can see that the heap based accumulator is better in time than all cases of the naive collection based version. Comparing to the transducer approach with the sort after, we appear to be far better in the average case but worse in the sorted low to high case, the heap-keep's worst case.

The space usage of heap-keep should be far better, linear with regards to the desired `(take N)` rather than linear to the size of the input collection, for those that must take after sorting. Thus it may be a worthwhile tradeoff even if you have ordered collections (1226 ms vs 488 ms) if the space requirements are weighed heavily.

```clojure
;; our inputs. {:name 1234} etc.
(def inputs (shuffle (map (fn [n] {:data n}) (range 1e6))))
(def sorted-low->high (sort-by :data inputs))
(def sorted-high->low (sort-by :data #(compare %2 %1) inputs))

;; how many results from the million we care about
(def result-count 1000)

;; a typical collection based pipeline:

(doseq [inputs [inputs sorted-low->high sorted-high->low]]
  (time
    (->> inputs
         (map (fn [{:keys [data]}] {:data data :score data}))
         (filter (fn [x] (> (:score x) 4)))
         (sort-by :score #(compare %2 %1))
         (take result-count))))
"Elapsed time: 3027.527706 msecs"
"Elapsed time: 710.806354 msecs"
"Elapsed time: 1078.503962 msecs"

;; put the transformations in a transducer but the sort goes after
(doseq [inputs [inputs sorted-low->high sorted-high->low]]
  (time
    (let [xf (comp (map (fn [{:keys [data]}] {:data data :score data}))
                   (filter (fn [x] (> (:score x) 4))))]
      (->> (transduce xf conj inputs)
           (sort-by :score #(compare %2 %1))
           (take result-count)))))
"Elapsed time: 3191.244937 msecs"
"Elapsed time: 488.754128 msecs"
"Elapsed time: 615.899472 msecs"

(declare heap-keep)

(doseq [inputs [inputs sorted-low->high sorted-high->low]]
  (time
    (let [xf (comp (map (fn [{:keys [data]}] {:data data :score data}))
                   (filter (fn [x] (> (:score x) 4))))
          rf (heap-keep result-count (fn [x y] (compare (:score x) (:score y))))]
      (transduce xf rf inputs))))
"Elapsed time: 273.779469 msecs"
"Elapsed time: 1226.198758 msecs"
"Elapsed time: 262.179039 msecs"

;; heap-keep
(defn heap-keep
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
```
