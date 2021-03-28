(ns clj-clapps.simple
  (:require [clj-clapps.core :as cl]))

(cl/defcmd main [arg1 arg2 & [opt1]]
  (println "this command uses " arg1 " and " arg2 " and option " opt1))

(defn -main [& args]
  (cl/exec! args))
