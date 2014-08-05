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

(ns lyceum.plugin
  (:require [clojure.java.classpath :as cp])
  (:require [clojure.tools.logging :refer [info warn error]])
  (:require [lyceum.core :as core]))

(defn- fn-to-ns
  [path]
  (let [n (.substring path 0 (- (.length path) 4))]
    (symbol (.replace n "/" "."))))

(defn- blacklist-matcher
  [blacklist]
  (fn [s]
    (let [n (name s)]
      (not
        (every?
          nil?
          (map #(re-matches % n) blacklist))))))

(defn- load-namespaces
  "Loads all namespaces not matching blacklist.
  
  Namespaces that should not be loaded"
  [namespaces matches-blacklist?]
  (for [n namespaces
        :when
        (do
          (if (matches-blacklist? n)
            (do
              (warn (str "Not loading blacklisted namespace: " n))
              false)
            (do
              (info (str "Loading namespace: " n))
              true)))]
    (try
      (do
        (require n)
        n)
      (catch Exception e
        (error e (str "Failed to require namespace: " n))
        nil))))

(defn load-rules
  [base-ns & {:keys [opts blacklist]
              :or {opts {} blacklist []}}]
  (let [files (mapcat cp/filenames-in-jar (cp/classpath-jarfiles))
        path-prefix (str (.replace (name base-ns) "." "/") "/")
        matches-blacklist? (blacklist-matcher blacklist)
        namespaces (for [file files
                         :when (and (.startsWith file path-prefix)
                                    (.endsWith file ".clj"))
                         :let [file-ns (fn-to-ns file)]]
                     file-ns)]
    (let [ns-names (load-namespaces namespaces matches-blacklist?)]
      (core/rules-for-all opts (filter (comp not nil?) ns-names)))))
