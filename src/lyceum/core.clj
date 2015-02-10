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

;; # Core components essential to lyceum
(ns lyceum.core
  (:require [riemann.streams :refer [call-rescue]])
  (:require [clojure.tools.logging :refer [info error]]))

;; A function that reads the current lyceum mode from system properties.
;;
;; The following modes are valid.
;;
;; * `:fake` - Prevents external interactions from being executed, just
;;   appended to `lyceum.external/*external-reports*`.
;; * `:test` - Prevents external interactions from being executed, just logged
;;   to standard logging facilities.
;; * `:real` - All external interactions will both be executed and logged.
(defn get-lyceum-mode
  []
  (keyword (System/getProperty "lyceum.mode" "fake")))

(defmacro def-rules
  "Define a rules function for the current namespace.

  This will define the entry point for lyceum.
  The `index` symbol will be defined within the block in def-rules and
  corresponds to the value that was passed into the `:index` key in
  `load-rules`."
  [& body]
  `(defn ~'rules
     [{:keys [~'index]}]
     (let [bodies# [~@body]]
       (fn [e#] (call-rescue e# bodies#)))))

(defmacro require-depends
  "Requires all necessary dependencies and binds them to the current
  namespace.

  This should contain most dependencies that are available in a regular
  riemann.config file."
  []
  (require '[riemann.streams :refer :all])
  (require '[riemann.time :refer [unix-time linear-time once! every!]])
  (require '[lyceum.external.email :refer :all])
  (require '[lyceum.external.hipchat :refer :all])
  (require '[lyceum.external.pagerduty :refer :all])
  (require '[lyceum.external.logging :refer :all])
  (require '[lyceum.external.tcp-forward :refer :all]))

(def default-index
  (fn [e] (error "No indexing function set in lyceum/load-rules!")))

(defn rules-for-ns
  "Load rules for the `rule-ns` namespace passing in options `opts`.

  Returns `nil` if the specified rule cannot be found."
  [{:keys [index] :or {index default-index}} rule-ns]
  (when-let [rule-ns (find-ns rule-ns)]
    (when-let [rule-fn (ns-resolve rule-ns 'rules)]
      (rule-fn {:index index}))))

(defn rules-for-nss
  "Load all specified rules in `rule-nss` with the option `opts`.

  Essentially a wrapped around `rules-for-ns` but `nil` results are ignored."
  [opts rule-nss]
  (let [map-fn (partial rules-for-ns opts)]
    (for [rule-ns rule-nss :let [f (map-fn rule-ns)] :when f]
      f)))

(defn rules-for-all
  "Create a stream function that corresponds to the rules specified in
  `rule-nss` with the options `opts`.

  Essentially a wrapper for `rules-for-nss` but defines a stream function out
  of the results."
  [opts rule-nss]
  (let [functions (doall (rules-for-nss opts rule-nss))]
    (fn [e] (call-rescue e functions))))
