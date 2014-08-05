(ns __NS__-test
  (:require [clojure.test :refer :all]
            [lyceum.mock :refer :all]
            [lyceum.test :refer :all]))

(use-fixtures :each (rule-fixture __NS__))

(def test-tags ["role::example"])

(deftest test-default-streams
  (testing "Monitoring hooks should send a single e-mail when toggling back and forth between 'critical' and 'ok'"
    (send-events
      ; stop the simulation at 100 seconds.
      ; use an event base to add the specified tags to all events.
      {:end-time 100 :event-base {:tags test-tags}}
      ; send a critical event at second 0.
      [{:time 0 :state "ok"}
       {:time 10 :state "critical"}
       {:time 20 :state "critical"}
       {:time 30 :state "ok"}])
    ; verify externals.
    (check-externals
      :email {:event {:state "critical" :time 10}}
      :email {:event {:state "ok" :time 30}})))
