#!/usr/bin/env bb

(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {org.clojars.clj-clapps/clj-clapps {:mvn/version "LATEST"}
                        borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                               :sha "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}})

(require 'spartan.spec) ;; defines clojure.spec.alpha

(ns my-cool-cli
  (:gen-class)
  (:require [clj-clapps.core :as cl :refer[defcmd defopt]]))

;; define your command function
(defcmd main-cmd
  "My cool command help description"
  [^{:doc "Argument 1 of cool command" } arg1
   ;; optional arguments vector become command options
   & [^{:doc "Option 1 of cool command" :short "-o" } opt1]]
  ;; do something with arg1 and opt1 
  (prn arg1 opt1))

;; execute your command
(defn -main [& args]
  (cl/exec! 'my-cool-cli args))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
