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

(ns lyceum.service.config-file
  (:require [clojure.tools.logging :refer [info error]])
  (:require [lyceum.service.rules-loader.composite :as composite]
            [lyceum.service.rules-loader.github :as github]
            [lyceum.service.rules-loader.directory :as directory]))

(def ^:dynamic *config*)

(defn assoc-function
  [assoc-key]
  (fn [value]
    (swap! *config* assoc assoc-key value)))

(def listen-port (assoc-function :listen-port))
(def rules-loader (assoc-function :rules-loader))

(defn rule-loaders
  [& loaders]
  (rules-loader (composite/setup loaders)))

(defn github-rules
  [& opts]
  (github/setup (apply hash-map opts)))

(defn directory-rules
  [& opts]
  (directory/setup (apply hash-map opts)))

(defn setup
  [path]
  (binding [*config* (atom {:listen-port 8080})
            *ns* (find-ns 'lyceum.service.config-file)]
    (load-file path)
    @*config*))
