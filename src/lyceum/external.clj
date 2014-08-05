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

;; # External integrations
;; `externals` are lyceum's way of interacting with the outside world.
;;
;; In essence they are wrappers around other types of external integrations to
;; provide a convenience layer to enable integration testing.
(ns lyceum.external
  (:require [riemann.time]
            [riemann.pubsub :as pubsub]
            [riemann.core :as core])
  (:require [lyceum.core :refer [get-lyceum-mode]])
  (:require [riemann.common :as common]
            [riemann.config :as config]
            [riemann.streams :as streams])
  (:require [clojure.tools.logging :refer [info error]]))

;; If bound, will contain a list of _all_ external interactions that has
;; occured as formatted by `format-report`.
(def ^:dynamic *external-reports*)

(defn- format-report
  "Format a single external the external named by `external` containing an
  `event`, a `message` and `extra` parameters."
  [external message extra event]
  (let [now (riemann.time/unix-time)]
    (list external {:message message :event event
                    :extra extra :time now})))

(defn fake-report
  "Build a stream function that causes an a message as formatted by
  `format-report` to be appended to `*external-reports*` when it is
  called."
  [external message extra]
  (fn [e]
    (if (bound? #'*external-reports*)
      (swap! *external-reports* conj (format-report external message extra e))
      (error "*external-reports* is not bound, you probably want to run riemann with -Dlyceum.mode=real or -Dlyceum.mode=test"))))

(defn real-report
  [external message opts & children]
  (let [external-s (name external)]
    (fn real-report-fn [e]
      (info (str external " " message ": " opts " - " (common/event-to-json e)))
      (when-let [core @config/core]
        (pubsub/publish!
          (:pubsub core)
          "lyceum.external"
          (common/event (merge opts
                               {:external_name external :external_message message}
                               e))))
      (streams/call-rescue e children))))

(defn test-report
  [external message opts]
  (fn test-report-fn [e]
    (info (str external " (TEST) " message ": " opts " - " (common/event-to-json e)))))

(defmacro report
  [external message opts & children]
  (let [m (get-lyceum-mode)]
    (case m
      :real `(real-report ~external ~message ~opts ~@children)
      :test `(test-report ~external ~message ~opts)
      :fake `(fake-report ~external ~message ~opts)
      (throw (RuntimeException. (str "Unknown lyceum mode: " m))))))

(defn report-external
  "Dispatch all accumulated external reports to the specified `reporter`
  function."
  [reporter]
  (let [reports @*external-reports*]
    (doseq [report reports]
      (reporter report))))
