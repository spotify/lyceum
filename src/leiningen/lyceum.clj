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

(ns leiningen.lyceum
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]))

(defn- path-replace
  [part]
  (string/replace part "-" "_"))

(defmulti lyceum-action
  (fn [project action args]
    action))

(defmethod lyceum-action :init
  [project action args]

  (when (empty? args)
    (println "Usage: lyceum :init <name>")
    (System/exit 1))

  (when-not (:lyceum-namespace project)
    (println "Missing :lyceum-namespace from project.clj")
    (System/exit 1))

  (let [root (:root project)
        lyceum-namespace (:lyceum-namespace project)
        first-arg (first args)
        init-ns (symbol (str lyceum-namespace "." first-arg))
        base-path (str (string/join "/" (map path-replace (string/split (str init-ns) #"\."))))
        test-template (slurp (str root "/templates/test.clj"))
        rule-template (slurp (str root "/templates/rule.clj"))]

    (when-let [rule-path (first (:source-paths project))]
      (let [rule-path (str rule-path "/" base-path ".clj")]
        (io/make-parents rule-path)
        (with-open [out (io/writer rule-path)]
          (println (str "Writing rule: " rule-path))
          (.write out (string/replace rule-template "__NS__" (str init-ns))))))

    (when-let [test-path (first (:test-paths project))]
      (let [test-path (str test-path "/" base-path "_test.clj")]
        (io/make-parents test-path)
        (with-open [out (io/writer test-path)]
          (println (str "Writing test: " test-path))
          (.write out (string/replace test-template "__NS__" (str init-ns))))))))

(defmethod lyceum-action :default
  [project action args]
  (throw (RuntimeException. (str "No such action: " action))))

(defn lyceum
  [project & args]

  (when (empty? args)
    (println "Usage: lyceum [action] [options]")
    (println "Available actions:")
    (println "  init <name> - Initialize a rules namespace, ex. :init my-team")
    (System/exit 1))

  (lyceum-action project (keyword (first args)) (rest args)))
