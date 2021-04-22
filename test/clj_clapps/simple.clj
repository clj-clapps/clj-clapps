(ns clj-clapps.simple
  (:gen-class)
  (:require [clj-clapps.core :as cl]))

(cl/defcmd main
  "My cool command help description"
  [^{:doc "Argument 1 to cool command"} arg1
   ^{:doc "Argument 2 to cool command"} arg2
   &
   [^{:doc "Option 1" :short "-o"} opt1]]
  (println "this command uses " arg1 " and " arg2 " and option " opt1))

(defn -main [& args]
  (cl/exec! 'clj-clapps.simple args))
