(ns __NS__
  (:require [lyceum.core :refer :all]))

(require-depends)

(def alert
  (email {:from "riemann@example.com"} ["me@example.com"]))

;; STEP 4: Add rules here!
(def-rules
  (where (tagged-any ["role::example"])
    (changed-state {:init "ok"}
      alert)))
