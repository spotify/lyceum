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

(ns lyceum.service.rules-loader
  (:require [clojure.string :as string]))

(defn path-to-id
  [prefix path]
  (let [r1 (.substring path (.length prefix) (- (.length path) 4))
        r2 (string/split r1 #"/")
        r3 (string/join "." r2)]
    (string/replace r3 #"_" "-")))

(defn id-to-path
  [prefix id]
  (let [r1 (string/replace id #"-" "_")
        r2 (string/split r1 #"\.")
        r3 (string/join "/" r2)]
    (str prefix r3 ".clj")))

(defprotocol RulesLoader
  (list-rules [this] "List all available rules, should return a list of string.")
  (get-rule [this id] "Get the specified set of rules."))
