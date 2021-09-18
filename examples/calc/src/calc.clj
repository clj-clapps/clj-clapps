(ns calc
  (:gen-class)
  (:require [clj-clapps.core :as cl :refer [defcmd defopt]]))

(defcmd main-cmd
  "A simple calculator"
  [^{:doc "First operand" :parse-fn read-string :validate [number? "must be a number"]} n1
   ^{:doc "Operator" :enum ["+" "-" "*" "/"]} op
   ^{:doc "Second operand" :parse-fn read-string :validate [number? "must be a number"]} n2]
  (let [result (case op
                 "+" (+ n1 n2)
                 "-" (- n1 n2)
                 "*" (* n1 n2)
                 "/" (/ n1 n2))]
    (println result)))

(def this-ns *ns*)

;; execute your command
(defn -main [& args]
  (cl/exec! this-ns args))
