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
(ns ^{:doc "Forward events to the alert-dispatcher service for monitoring-alerts"}
  lyceum.external.monitoring-alert
  (:require [lyceum.external :refer [report]])
  (:require [riemann.common :refer [event-to-json]])
  (:require [clj-http.client :as client]))

(defn- post-alert
  [url data]
  (client/post url
               {:body           data
                :socket-timeout 1000
                :conn-timeout   1000
                :content-type   :json
                :accept         :json
                :throw-entire-message? true}))

(defn monitoring-alert
  [url]
  (report :monitoring-alert "Send Monitoring Alert" {:url url}
    (fn [e]
      (post-alert url (event-to-json e)))))
