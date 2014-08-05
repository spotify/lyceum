(ns lyceum.service.rules-loader.composite-test
  (:require [lyceum.service.rules-loader :refer :all])
  (:require [lyceum.service.rules-loader.composite :refer :all])
  (:require [clojure.test :refer :all]))

(defn fake
  [ret nss]
  (reify
    RulesLoader
    (list-rules [this] nss)
    (get-rule [this id] ret)))

(deftest test-recursive-loader
  (is (= :second (real-get-rule [(fake nil []) (fake :second [])] :x)))
  (is (= :only (real-get-rule [(fake :only [])] :x)))
  (is (= nil (real-get-rule [] :x))))
