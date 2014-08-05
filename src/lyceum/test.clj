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

;; <h1>Lyceum Test Framework</h1>
;;
;; This namespace contains function designed to put lyceum rules under test.
;;
;; It contains [utility functions](#utility-functions) to simulate riemann
;; operations as fast as your CPU can handle it without spinning up a core.
;;
;; You are probably looking for [`with-rules`](#with-rules), which is the
;; helper macro used when writing test-cases.
;;
;; It is typically used like the following.
;;
;; <pre>
;; (ns my-awesome.namespace-test
;;   (require [lyceum.test :refer :all]))
;;
;; (deftest my-awesome-test
;;   (with-rules 'my-awesome.namespace
;;     (send-events {:time 1000 :host :foo :state "critical"})
;;     (check-externals
;;       :email {:state "critical"})))
;; </pre>
(ns lyceum.test
  (:require clojure.test)
  (:require
    [lyceum.core :refer [rules-for-ns]]
    [lyceum.mock :refer [with-mocks let-mocks make-mock-fn]]
    [lyceum.external :refer [*external-reports* report-external]])
  (:require
    lyceum.external)
  (:require
    riemann.common
    riemann.logging))

;; <em id='*test-events*'>Maintains a list of all events sent into the rules
;; function under test.</em>
(def ^:dynamic *test-events*)

(defn check-bound-with-rules
  "<em id='check-bound-with-rules'>Builds a function that fails with an
  assertion error unless [`*test-events*`](#*test-events*) has been bound</em>"
  [fn-name]
  (fn [& args]
    (assert (bound? #'*test-events*)
            (str "Function must be inside a with-rules block: " fn-name))))

;; <h1 id="unbound-functions">Unbound Functions</h1>
;;
;; These functions are bound to a placeholder through
;; [`check-bound-with-rules`](#check-bound-with-rules) until a real
;; implementation is provided by the [`with-rules`](#with-rules) macro.

;; <em id="send-event">The unbound implementation of
;; `send-event-fn` which is built by
;; [`make-send-event-fn`](#make-send-event-fn)</em>
(def ^:dynamic send-event (check-bound-with-rules 'send-event))

;; <em id="send-events">The unbound implementation of
;; `send-events-fn` which is built by
;; [`make-send-events-fn`](#make-send-events-fn)</em>
(def ^:dynamic send-events (check-bound-with-rules 'send-events))

;; <em id="nth-event">The unbound implementation of
;; [`nth-event-fn`](#nth-event-fn)</em>
(def ^:dynamic nth-event (check-bound-with-rules 'nth-event))

;; <em id="check-externals">The unbound implementation of
;; [`check-externals-fn`](#check-externals-fn)</em>
(def ^:dynamic check-externals (check-bound-with-rules 'check-externals))

;; <em id="index">A helper variable that is bound by
;; [`rule-fixture`](#rule-fixture) for simplifying common test fixtures.</em>
(def ^:dynamic index)

;; <h1 id='utility'>Utility Functions</h1>

(defn- make-event-maps-filter
  "<em id='make-event-maps-filter'>Makes the function that converts a map
  (`event-map`) into a riemann event.</em>

  Takes a `config` parameter which is what was passed to
  [`send-events`](#send-events) and family functions.

  All keys will be converted to keywords"
  [{:keys [event-base]}]
  (fn [event-map]
    (riemann.common/event
      (into
        {:time 0}
        (for  [[k v] (merge event-base event-map)]
          [(keyword k) v])))))

(defmacro with-mocked-riemann-time
  "<em id='with-mocked-riemann-time'>Mock all important riemann time functions
  for predictive operations.</em>"
  [tasks now & body]
  `(with-redefs [riemann.time/next-tick (fn [a# dt#] (+ ~now dt#))
                 riemann.time/unix-time (fn [] ~now)
                 riemann.time/schedule!
                 (fn [task#]
                   (swap! ~tasks conj task#)
                   task#)]
     ~@body))

(defn- sort-tasks
  [tasks]
  (sort (fn [a b] (compare (:t a) (:t b))) tasks))

(defn- run-task
  [task task-time current-tasks]
  (let [new-tasks (atom [])
        i-task-time (int task-time)]
    (with-mocked-riemann-time new-tasks i-task-time
      (riemann.time/run task))
    (let [next-task (riemann.time/succ task)
          rest-tasks (rest current-tasks)
          tail-tasks (if (nil? next-task)
                       rest-tasks
                       (conj rest-tasks next-task))]
      (sort-tasks (concat @new-tasks tail-tasks)))))

(defn run-tasks
  "Recursively execute all viable tasks."
  [now tasks]
  (loop [current-tasks (sort-tasks tasks)]
    (if (empty? current-tasks)
      current-tasks
      (let [task (first current-tasks)
            task-time (:t task)]
        (if (> task-time now)
          current-tasks
          (recur (run-task task task-time current-tasks)))))))

(defn- compare-events
  "Compare events by time first, then by index to make sure sorting is
  consistent."
  [[i-a a] [i-b b]]
  (compare [(:time a) i-a] [(:time b) i-b]))

(defn send-events-fn
  "<em id='send-events-fn'>Function to simulate riemann operation.</em>
  Will submit events to the specified rules function and run any tasks
  which pop up at the appropriate time."
  ([config rules-fn event-maps initial-tasks]
   (let [set-defaults (partial merge {:host :default-host :service :default-service})
         event-filter (make-event-maps-filter config)
         flat-event-maps (flatten event-maps)
         events (into [] (map event-filter flat-event-maps))
         indexed-events (map-indexed list events)
         events (map second (sort compare-events indexed-events))
         tasks (atom initial-tasks)]

     ; Send in the sequence of events, and run tasks required accordingly.
     ; We have to run tasks both before and after the rule under test.
     ; * Tasks can have been pre-emptively scheduled before the rule and be
     ;   viable for run at `now`.
     ; * The rule function can schedule tasks.
     (doseq [e (map set-defaults events) :let [now (:time e)]]
       (reset! tasks (run-tasks now @tasks))
       (with-mocked-riemann-time tasks now
         (rules-fn e))
       (reset! tasks (run-tasks now @tasks)))

     ; If :end-time specified, run the current tasks up until specified time.
     (when-let [end-time (:end-time config)]
       (run-tasks end-time @tasks))

    (when (bound? #'*test-events*)
      (reset! *test-events* events))))
  ([config rules-fn event-maps]
   (send-events-fn config rules-fn event-maps [])))


(defn make-send-events-fn
  "<em id='make-send-event-fn'>Build the send-events-fn used by the
  [unbound function](#unbound-functions) [`send-events`](#send-events)</em>

  This is essentially a wrapper for [`send-events-fn`](#send-events-fn)
  "
  [rules-fn]
  (fn [& args]
    {:pre [(> (count args) 0)]}
    (let [two-args? (> (count args) 1)
          config (if two-args? (first args) {})
          event-maps (if two-args? (rest args) (first args))]
      (send-events-fn config rules-fn event-maps))))


(defn make-send-event-fn
  "<em id='make-send-event-fn'>Build the send-event-fn used by the
  [unbound function](#unbound-functions) [`send-event`](#send-event)</em>

  This is essentially a wrapper for [`send-events-fn`](#send-events-fn)."
  [rules-fn]
  (fn [& event-maps]
    (send-events-fn {} rules-fn event-maps)))


(defn external-reporter
  "<em id='external-reporter'>Function used to generate one external report
  (`report`) through `lyceum.external/report-external`</em>"
  [report]
  (let [external-name (:external report)
        message (:message report)]
    (clojure.test/report
    {:type :fail
     :message (str "This should not happen: " external-name ": "  message
                   " - did you forget to mock streams or check the external "
                   "calls (check-externals) in your test case?")})))


(defn nth-event-fn
  "<em id='nth-event-fn'>Return event number `n` that has been processed from
  [`*test-events*`](#*test-events*)</em>"
  [n]
  (get @*test-events* n))


(defn- match-map
  "<em id='match-map'>Recursively check that all items in `ref-val` equals to
  the ones in `actual-val`.</em>

  Keys existing in `actual-val` but missing in `ref-val` will be ignored.

  Ex: `(match-map {:foo :bar} {:foo :bar :baz :biz})` would be `true`, even
  though `:baz` only exists in `actual-val`."
  [ref-val actual-val]
  (if (map? ref-val)
    (every?
      true?
      (for [[k v] (seq ref-val)]
        (match-map v (k actual-val))))
    (= ref-val actual-val)))


(defn- match-actual
  "<em id='match-actual'>Generate a readable reference of a map that did
  not match.</em>

  Create a readable representation of what the actual match looked like
  compared to the reference object."
  [ref-val actual-val]
  (if (and actual-val (map? ref-val))
    (apply
      hash-map
      (flatten
        (for [[k v] (seq ref-val)]
          [k (match-actual v (k actual-val))])))
    actual-val))


(defn check-externals-fn
  "<em id='check-externals-fn'>The real implementation of
  [`check-externals`](#check-externals)</em>

  Check that all externals that is specified in `call-defs` has been called
  in the order specified.

  This is a flexible assertion method that uses the specified calls
  (`call-defs`) as template to the actual calls being made, and will report
  when-ever there is a mis-match through `clojure.test/report`

  The structure of the external call is matched using
  [`match-map`](#match-map).

  Resets `*external-reports*` after each call."
  [& call-defs]
  {:pre [(even? (count call-defs))]}
  (let [reports @*external-reports*
        calls (partition 2 call-defs)
        indexed-reports (map-indexed vector reports)
        count-matches? (= (count calls) (count reports))]

    (clojure.test/report
      {:type (if count-matches? :pass :fail)
        :message (str "Amount of external calls should match")
        :expected (count calls)
        :actual (count reports)})

    (doseq [[i [external-actual report]] indexed-reports]
      (let [[external-ref value-ref] (nth calls i [nil, nil])]
        (let [external-matches? (= external-ref external-actual)
              value-matches? (match-map value-ref report)
              all-matches? (and external-matches? value-matches?)
              report-message (if (nil? external-ref)
                               (str "Did not expect external call #" (inc i))
                               (str "Expected external call #" (inc i)))
              report-type (if all-matches?
                            :pass
                            :fail)
              reduced-actual (match-actual value-ref report)
              print-reduced? (and (not (nil? value-ref))
                                  (not (empty? reduced-actual)))
              value-actual (if print-reduced? reduced-actual report)]

          (clojure.test/report
            {:type report-type
             :message report-message
             :expected (list external-ref value-ref)
             :actual (list external-actual value-actual)}))))

    (reset! *external-reports* [])))

;; <h1 id='public'>Public Functions</h1>

(defmacro with-test-bindings
  [rules-fn & body]
  `(binding [*test-events* (atom [])
            *external-reports* (atom [])
            send-event (make-send-event-fn ~rules-fn)
            send-events (make-send-events-fn ~rules-fn)
            nth-event nth-event-fn
            check-externals check-externals-fn]
     ~@body
     (report-external external-reporter)))

(defmacro with-rules
  "<em id='with-rules'>Helper macro to setup test framework</em>

  __This is probably what you are looking for.__

  Will bind all [unbound functions](#unbound-functions) with options (`opts`)
  and load the namespace `under-test` and evalute `body` in this scope."
  [opts under-test & body]
  {:pre [(not (nil? opts))
         (symbol? under-test)]}
  `(do
     (require '~under-test :reload)

     (let [rules-fn# (rules-for-ns ~opts '~under-test)]
       (when (nil? rules-fn#)
         (throw (Exception. (str "Could not load rules for: " '~under-test))))
       (riemann.logging/init)
       (with-test-bindings rules-fn#
         ~@body)
       (remove-ns '~under-test))))

(defmacro rule-fixture
  "<em id='rule-fixture'>A helper function to setup a clojure.test fixture for
  a namespace `under-test`.</em>

  Initializes a rule namespace with a bound and mocked [index](#index)."
  [under-test]
  `(fn [f#]
    (with-mocks
      (let-mocks [(index#)]
        (with-rules {:index index#} ~under-test
          (binding [index index#]
            (f#)))))))

(defmacro with-rule-fn
  "<em id='with-rule-fn'>A helper function to setup lyceum testing for a
  immediate `rule-fn` rules function.</em>"
  [rule-fn & body]
  `(with-test-bindings ~rule-fn
     ~@body))
