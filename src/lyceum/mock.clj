;; $LICENSE
;; Copyright 2013-2014 Spotify AB. All rights reserved.
;;
;; The contents of this file are licensed under the Apache License, Version 2.0
;; (the "License"); you may not use this file except in compliance with the
;; License. You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
;; WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
;; License for the specific language governing permissions and limitations under
;; the License.

;; # A simple mocking library
(ns lyceum.mock
  (:require clojure.test))

;; Record all mock calls.
(def ^:dynamic *mocks-db*)

(defmacro with-mocks [& body]
  "Sets up the mocking framework by binding `*mocks-db*` in the scope of the
  specified body."
  `(binding [*mocks-db* (atom {})]
     ~@body))

(defn make-mock-fn
  "Builds a mock function by `callee-fn-name` that will always return the value
  `return-value`.

  Will return a function that once called, logs the call to `*mocks-db*`
  (if bound) and return `return-value`.

  The `calee-fn-name` parameter exists to give a better error when
  pre-conditions fail since it contains the name of _who_ requested the mock
  function."
  [callee-fn-name return-value]
  (assert (bound? #'*mocks-db*)
          (str "Function must be inside a with-mocks block: " callee-fn-name))
  (let [mocked-fn
        (fn mocked-fn [& args]
          (swap! *mocks-db* update-in [mocked-fn] #(concat % [args]))
          return-value)]
    (swap! *mocks-db* assoc-in [mocked-fn] (list))
    mocked-fn))

(defn make-mocks
  "<em id='make-mocks'>Build mock functions</em>

  Create a list of forms containing definition of mock functions compatible
  with with-redefs, let and binding.

  `mock-defs` should be a sequence containing sequences of the form
  `(name[ return-value])`.

  * The `name` part for each element will be used as the local variable where
    the mock function is bound.
  * The `return-value` corresponds to what invocations of this function should
    return."
  [callee-fn-name mock-defs]
  {:pre [(vector? mock-defs)
         ; Make sure every mock definition is a sequence.
         (every? seq? mock-defs)
         ; make sure every mock definition has at at least the first
         ; value. Second (return-value) will default to nil.
         (every? first mock-defs)]}
  (let [make-mock-expr #(list 'make-mock-fn
                              callee-fn-name
                              (second %))
        mocks (map make-mock-expr mock-defs)
        fn-names (map #(first %) mock-defs)]
    (interleave fn-names mocks)))

(defn lookup-calls
  "<em id='lookup-calls'>Lookup all calls to a mocked function (`mocked-fn`)</em>

  If any calls could be found, filters the result using `filter-fn`.

  Returns `nil` if no calls could be found and reports the problem using
  clojure.test/report."
  [mocked-fn filter-fn]
  (let [calls (@*mocks-db* mocked-fn)]
      (if (nil? calls)
        (do
          (clojure.test/report
            {:type :fail
             :message (str "Expected '" mocked-fn "' to be a mocked function") })
          nil)
        (filter-fn calls))))

(defmacro binding-mocks
  "<em id='binding-mocks'>Setup a binding of mocked functions using `with-redefs`.</em>

  For the structure of `mock-defs`, see [`make-mocks`](#make-mocks).

  Evaluates `body` with the defined binding in effect."
  [mock-defs & body]
  (let [mocks (make-mocks ''binding-mocks mock-defs)]
    `(with-redefs [~@mocks]
       ~@body)))

(defmacro let-mocks
  "<em id='let-mocks'>Define new mock functions which did not exist before.</em>"
  [mock-defs & body]
  (let [mocks (make-mocks ''let-mocks mock-defs)]
    `(let [~@mocks] ~@body)))

(defn is-called-nth
  "<em id='is-called-nth'>Check that `mocked-fn` call number `n` matches `args`.</em>"
  [mocked-fn n args]
  (let [msg (str "Expected '" mocked-fn "' call number " n " to have parameters: " args)]
    (let [actual (lookup-calls mocked-fn #(get % n))]
       (when-not (nil? actual)
         (clojure.test/is (= args actual) msg)))))

(defn is-called-count
  "<em id='is-called-count'>Macro to check that `mocked-fn` has `n` calls.</em>"
  [mocked-fn n]
  (let [msg (str "Expected '" mocked-fn "' to be called " n " time(s)")]
    (let [actual (lookup-calls mocked-fn #(count %))]
      (when-not (nil? actual)
        (clojure.test/is (= n actual) msg)))))

(defn is-not-called [mocked-fn]
  "<em id='is-not-called'>Macro to check that `mocked-fn` has not been called.</em>"
  (let [msg (str "Expected '" mocked-fn "' to not be called")]
    (let [actual (lookup-calls mocked-fn #(count %))]
      (when-not (nil? actual)
        (clojure.test/is (= 0 actual) msg)))))

(defmacro is-called
  "Helper macro to check all call arguments.

  Uses [`is-called-count`](#is-called-count) and
  [`is-called-nth`](#is-called-nth) to verify that all specified arguments
  match."
  [mocked-fn & args]
  (let [c (count args)
        expr (for [i (range 0 c)]
               (list 'is-called-nth mocked-fn i (nth args i)))]
    `(do
       (is-called-count ~mocked-fn ~c)
       ~@expr)))
