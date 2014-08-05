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

(ns lyceum.service.rules-loader.directory
  (:require [clojure.tools.logging :refer [info error]])
  (:require [lyceum.service.rules-loader :refer :all]
            [clojure.string :as string]))

(defn- node-type
  [node]
  (let [file-name (.getName node)]
    (if (and (.isFile node)
             (.endsWith file-name ".clj")
             (not (.startsWith file-name ".")))
      :file
      (if (and (.isDirectory node)
               (not (.startsWith file-name ".")))
        :directory
        nil))))

(defn- real-list-rules
  [directory id-prefix]
  (let [make-nodes (fn [nodes path] (map (fn [c] [c path]) nodes))
        dir (clojure.java.io/file directory)]
    (loop [nodes (make-nodes (.listFiles dir) [])
           result []]
      (if (empty? nodes)
        result
        (let [[node path] (first nodes)
              node-name (.getName node)]
          (case (node-type node)
            :file
            (let [id (path-to-id "" (string/join "/" (conj path node-name)))
                  next-nodes (rest nodes)]
              (if (or (nil? id-prefix)
                      (.startsWith id id-prefix))
                (recur next-nodes (conj result id))
                (recur next-nodes result)))
            :directory
            (let [next-nodes
                  (make-nodes (.listFiles node) (conj path node-name))]
              (recur (concat (rest nodes) next-nodes) result))
            nil
            (recur (rest nodes) result)))))))

(defn- real-get-rule
  [directory id]
  (let [path (id-to-path directory id)]
    (let [f (clojure.java.io/file path)]
      (if (.isFile f)
        (slurp path)
        nil))))

(defrecord DirectoryRulesLoader [path id-prefix]
  RulesLoader
  (list-rules [this]
    (real-list-rules path id-prefix))
  (get-rule [this id]
    (real-get-rule path id)))

(defn setup
  "Will iterate through `path` assuming that that is the root namespace of the
  rules and load any *.clj files found, transforming their names appropriately.

  Returns a `rules-loader` based of this."
  [{:keys [path id-prefix]}]
  (DirectoryRulesLoader. path id-prefix))
