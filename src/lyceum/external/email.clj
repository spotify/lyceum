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

;; # External e-mail integration.
;; Wraps riemann's [email](http://riemann.io/api/riemann.email.html)
;; implementation for sending e-mails.
(ns lyceum.external.email
  (:require [lyceum.external :refer [report]])
  (:require [riemann.email :as email]))

;; ## Send an e-mail on events.
;; Send e-mails using `mailer-opts` to the specified `recipients`.

;; Options (`mailer-opts`) is of the same form as provided to
;; [`riemann.email/mailer`](http://riemann.io/api/riemann.email.html#var-mailer).
(defn email
  "Returns a stream function that emails events it receives"

  [opts recipients]
  (report
    :email "Send E-Mail" (merge opts {:recipients recipients})
    (apply (email/mailer opts) recipients)))
