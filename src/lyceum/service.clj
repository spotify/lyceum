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

;; # Experimental http server for testing rules.
;;
;; * Start: __lein run__
;; * Surf to http://localhost:8080
(ns lyceum.service
  (:import
    java.io.InputStreamReader
    java.io.ByteArrayInputStream
    java.io.PushbackReader
    lyceum.HttpException)
  (:require
    [lyceum.service.rules-loader :as loader]
    [lyceum.service.config-file :as config-file])
  (:require
    riemann.logging
    riemann.common
    riemann.time)
  (:require
    [lyceum.test :refer [with-mocked-riemann-time make-send-events-fn]]
    [lyceum.external :refer [*external-reports*]]
    [clojure.tools.logging :refer [info error]]
    [cheshire.core :as json]
    [compojure.route :refer [files not-found]]
    [compojure.handler :refer [site]]
    [compojure.core :refer [routes GET POST DELETE ANY context]]
    [org.httpkit.server :refer [run-server]])
  (:gen-class :name lyceum.service))

(defn handle-json
  [handler & args]
  (let [headers {"Content-Type" "application/json"}
        serialize json/generate-string
        respond (fn [status body]
                  {:status status
                   :headers headers
                   :body (serialize body)})]
    (fn [req]
      (try
        (respond 200 (apply handler (concat [req] args)))
        (catch HttpException e
          (respond (.getStatus e) {:message (.getMessage e)}))
        (catch Exception e
          (error e "An error happened in the request handler")
          (respond 500 {:message (str "An error occured: " e)}))))))

(defn- http-list-rules [req rules-loader]
  {:rules (loader/list-rules rules-loader)})

(defn- http-get-rule [req rules-loader]
  (let [p-ns (-> req :params :ns)
        rule (loader/get-rule rules-loader p-ns)]
    (if (nil? rule)
      (throw (HttpException. 404 (str "No such rule: " p-ns)))
      {:data rule})))

(defn read-expressions
  [reader]
  (binding [*read-eval* false]
    (loop [l []]
      (let [result (read reader false nil)]
        (if (nil? result)
          l
          (recur (conj l result)))))))

(defn- index-add
  [index e]
  (conj index {:time (riemann.time/unix-time) :event e}))

(defn eval-expressions
  [expressions events current-time]
  (let [temp-ns-name (gensym "lyceum-eval-ns___")
        current-ns (create-ns temp-ns-name)
        evaled-tasks (atom [])]
    (try
      (with-mocked-riemann-time evaled-tasks current-time
        (binding [*ns* current-ns]
          (doseq [expr expressions]
            (if (= (first expr) 'ns)
              (eval (cons 'ns (cons temp-ns-name (rest (rest expr)))))
              (eval expr)))))

      (let [reports (atom [])
            make-rules-fn (ns-resolve current-ns 'rules)
            index (atom [])
            index-fn (fn [e] (swap! index index-add e))
            rules-fn (make-rules-fn {:index index-fn})
            send-events (make-send-events-fn rules-fn)]
        (binding [*external-reports* reports]
          (send-events events))
        {:reports @reports :index @index})

      (finally
        (remove-ns temp-ns-name)))))

(defn- make-pushback-reader
  [data]
  (let [input-stream (ByteArrayInputStream. (.getBytes data))
        input-stream-reader (InputStreamReader. input-stream)]
    (PushbackReader. input-stream-reader) ))

(defn- http-eval-rule [req]
  (let [json-body-string (slurp (:body req))
        json-body (json/parse-string json-body-string)
        data (json-body "data" "")
        events (json-body "events" [])
        current-time (int (json-body "current-time" 0))]
    (when (or (nil? data) (empty? data))
      (throw (HttpException. 400 "'data' must not be empty")))
    (when (or (nil? events) (empty? events))
      (throw (HttpException. 400 "'events' must not be empty")))
    (let [pushback-reader (make-pushback-reader data)]
      (try
        (let [expressions (read-expressions pushback-reader)]
          (eval-expressions expressions events current-time))
        (catch RuntimeException e
          (error e "Failed to handle request")
          (throw (HttpException. 400 (str "Invalid body: " e))))))))


(defn make-routes
  [c]
  (let [rules-loader (:rules-loader c)]

    (when (nil? (:rules-loader c))
      (throw (Exception. "No rules-loader specified in lyceum configuration")))

    (routes
      (GET "/rules" [] (handle-json http-list-rules rules-loader))
      (GET "/rules/:ns" [] (handle-json http-get-rule rules-loader))
      (POST "/eval" [] (handle-json http-eval-rule))
      (not-found "not found"))))

(defn http-server
  [listen-port site-routes]
  (info (str "Listening on port " listen-port))
  (run-server (site site-routes) {:port listen-port}))

(defn -main
  [& argv]
  (riemann.logging/init)
  (let [config-path (or (first argv) "lyceum.conf")
        c (config-file/setup config-path)
        listen-port (:listen-port c)
        site-routes (make-routes c)]
    (http-server listen-port site-routes)))
