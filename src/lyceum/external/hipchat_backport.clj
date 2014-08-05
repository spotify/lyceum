;; # A backport and extension of the official hipchat plugin
;; This came to be because the version we were running at the time (0.2.2) did
;; not support hipchat.
;;
;; Original: https://github.com/aphyr/riemann/blob/master/src/riemann/hipchat.clj
;;
;; $LICENSE
;; The original source did not have a copyright statement, so we assume it was
;; released under the same license as the rest of Riemann which is the
;; Eclipse Public License - v 1.0, see
;; https://github.com/aphyr/riemann/blob/master/LICENSE for the copyright
;; statement.
;;
;; This code has been extended to add slightly more sane HTML bodies.
(ns ^{:doc    "Forwards events to HipChat"
      :author "Hubert Iwaniuk"}
  lyceum.external.hipchat-backport
  (:require [clj-http.client :as client]
            [clojure.string :refer [join]]
            [cheshire.core :as json]))

(def ^:private chat-url
  "https://api.hipchat.com/v1/rooms/message?format=json")

(defn- escape-html
  "Change special characters into HTML character entities."
  [text]
  (.. ^String (str text)
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")))

(defn- format-message
  [{:keys [host service state metric description tags]}]
  (let [description (if (nil? description)
                      "(no description)"
                      (if (< (count description) 150)
                        description
                        (subs description 0 150)))
        service (if-not (nil? service)
                  service
                  "(no service)") 
        host (if-not (nil? host)
               host
               "(no host)")
        tags (join ", " (map escape-html tags))
        parts [(str "[" (escape-html state) "]") " "
               (escape-html host) " "
               (str "<b>" (escape-html service) "</b>") "<br/>"
               (escape-html description) "<br/>"
               (str "Tags: " tags)
               (if-not (nil? metric)
                 (str "<br/>" "Metric: " (escape-html metric))
                 "")]]
    (join parts)))

(defn- format-event
  [params event]
  (merge {:color (condp = (:state event)
                   "ok"       "green"
                   "critical" "red"
                   "error"    "red"
                   "yellow")}
         params
         (when-not (:message params)
           (prn (format-message event))
           {:message (format-message event)})))

(defn- post
  "POST to the HipChat API."
  [token params event]
  (let [form-params (format-event (assoc params :message_format "html") event)]
    (client/post (str chat-url "&auth_token=" token)
                 {:form-params           form-params
                  :socket-timeout        5000
                  :conn-timeout          5000
                  :accept                :json
                  :throw-entire-message? true})))

(defn hipchat
  "Creates a HipChat adapter. Takes your HipChat authentication token,
   and returns a function which posts a message to a HipChat.

  (let [hc (hipchat {:token \"...\"
                     :room 12345
                     :from \"Riemann reporting\"
                     :notify 0})]
    (changed-state hc))"
  [{:keys [token room from notify color]}]
  (fn [e] (post token
                {:room_id room
                 :from    from
                 :notify  notify}
               e)))
