(ns lyceum.core-test
  (:require [clojure.test :refer :all]
            [lyceum.core :refer :all]
            [lyceum.mock :refer :all]))

(deftest test-rules-for-all
  (with-mocks
    (binding-mocks
      [(rules-for-ns (list :rules-for-ns-return))]
      (rules-for-all :opts [:foo :bar])
      (is-called rules-for-ns (list :opts :foo) (list :opts :bar)))))

(deftest test-rules-for-one
  (with-mocks
    (let-mocks
      [(ns-resolve-ret :ns-resolve-ret)]
      (binding-mocks
        [(find-ns :find-ns) (ns-resolve ns-resolve-ret)]
        (is (= :ns-resolve-ret (rules-for-ns :opts :foo)))
        (is-called find-ns (list :foo))
        (is-called ns-resolve (list :find-ns 'rules))
        (is-called ns-resolve-ret (list :opts))))))
