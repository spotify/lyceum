(ns lyceum.mock-test
  (:require [clojure.test :refer :all]) 
  (:require [lyceum.mock :refer :all]))

(deftest test-with-mocks
  (testing "Should rebind *mocks-db* to a map"
    (binding [*mocks-db* (atom :incorrect)]
      (with-mocks
        (is (= {} @*mocks-db*))))))

(deftest test-make-mock-fn
  (testing "Should support multiple calls"
    (binding [*mocks-db* (atom {})]
      (let [mock-fn (make-mock-fn :callee-fn-name :make-mock-fn-return)]
        (is (= :make-mock-fn-return (mock-fn :arg-a1 :arg-a2))
            "Mock function should return specified value")
        (is (= :make-mock-fn-return (mock-fn :arg-b1 :arg-b2))
            "Mock function should return specified value")
        (is (= {mock-fn '((:arg-a1 :arg-a2) (:arg-b1 :arg-b2))} @*mocks-db*)
            "Mock function call should have been recorded")))))

(deftest test-make-mocks
  (is (= '(:func1 (make-mock-fn :callee-fn-name :ret1)
           :func1 (make-mock-fn :callee-fn-name :ret2))
         (make-mocks :callee-fn-name ['(:func1 :ret1) '(:func1 :ret2)]))))

(deftest test-binding-mocks
  (with-redefs [make-mocks (fn [& _] '((foo :foo-fn)))]
   (let [f (macroexpand-1 `(binding-mocks [(fn-name :ret-value)]))]
     (is (= `(with-redefs  [(foo :foo-fn)])) f))))

(deftest test-let-mocks
  (with-redefs [make-mocks (fn [& _] '((foo :foo-fn)))]
   (let [f (macroexpand-1 `(let-mocks [(fn-name :ret-value)]))]
     (is (= `(let  [(foo :foo-fn)])) f))))
