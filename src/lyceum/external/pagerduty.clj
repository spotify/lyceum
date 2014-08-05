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

(ns lyceum.external.pagerduty
  (:require [lyceum.external :refer [report]])
  (:require [riemann.common :refer [event-to-json]]
            [riemann.pagerduty :as pagerduty]))

(def ^:private map-keys [:trigger :resolve :acknowledge])

(defn- pagerduty-make-map
  "Helper function to build a pagerduty map using a filter function for each
  action.
  The supplied function will be applied to all available keys.

  The map will be of the form:
  {:action (make-fn :action) ...}

  Note that the map keys are defines statically as the global var 'map-keys'."
  [make-fn]
  (apply hash-map (flatten
                    (interleave map-keys
                                (map make-fn map-keys)))))

(defn pagerduty
  [api-key]
  (pagerduty-make-map
    (fn [action]
      (report
        :pagerduty "Send Event to PagerDuty" {:api_key api-key :action action}
        (let [pd (riemann.pagerduty/pagerduty api-key)]
          (pd action))))))
