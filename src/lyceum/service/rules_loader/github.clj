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

(ns lyceum.service.rules-loader.github
  (:require [clojure.tools.logging :refer [info error]])
  (:require [lyceum.service.rules-loader :refer :all]
            [org.httpkit.client :as http-kit]
            [cheshire.core :as json]
            [clojure.string :as string]
            [base64-clj.core :as b64]))

(defn- http-request-get
  [url http-options]
  (info (str "GET: " url))
  (let [request (http-kit/get url http-options)
        result @request]
    (when (= (result :status) 200)
      (json/parse-string (result :body)))))

(defprotocol GithubClientProtocol
  (get-contents [this path ref])
  (get-git-refs [this ref])
  (get-git-trees-rec [this sha]))

(defrecord GithubClient [http-options base-url]
  GithubClientProtocol
  (get-contents [this path ref]
    (http-request-get
      (str base-url "/contents/" path "?ref=" ref)
      http-options))
  (get-git-refs [this ref]
    (http-request-get
      (str base-url "/git/refs/" ref)
      http-options))
  (get-git-trees-rec [this sha]
    (http-request-get
      (str base-url "/git/trees/" sha "?recursive=1")
      http-options)))

(defn- github-list-rules
  [client branch prefix]
  (let []
    (when-let [body (get-git-refs client (str "heads/" branch))]
      (let [object-sha ((body "object") "sha")
            result (get-git-trees-rec client object-sha)]
        (for [entry (result "tree")
              :let [path (entry "path")]
              :when (and (.startsWith path prefix)
                         (.endsWith path ".clj"))]
          (path-to-id prefix path))))))

(defn- real-list-rules
  [client branch prefix id-prefix]
  (for [id (github-list-rules client branch prefix)
        :when (or (nil? id-prefix)
                  (.startsWith id id-prefix))]
    id))

(defn- real-get-rule
  [client branch prefix id]
  (let [path (id-to-path prefix id)
        body (get-contents client path branch)
        split-content (body "content")
        content (string/replace split-content #"\n" "")]
    (b64/decode content "UTF-8")))

(defrecord GithubRulesLoader [client branch prefix id-prefix]
  RulesLoader
  (list-rules [this]
    (real-list-rules client branch prefix id-prefix))
  (get-rule [this id]
    (real-get-rule client branch prefix id)))

(defn setup
  [{:keys [url branch prefix repo id-prefix http-options]
    :or {url "https://api.github.com"
         branch "master"
         prefix "test/"
         http-options {}}}]
  {:pre [(not (nil? repo))]}
  (let [client (GithubClient. http-options (str url "/repos/" repo))]
    (GithubRulesLoader. client branch prefix id-prefix)))
