#!/usr/bin/env bb

(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {org.clojars.clj-clapps/clj-clapps {:mvn/version "LATEST"}
                        babashka/babashka.curl {:mvn/version "0.0.3"}
                        borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                               :sha "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}})

(require 'spartan.spec) ;; defines clojure.spec.alpha

(ns dates
  (:require [clj-clapps.core :as cl :refer[defcmd defopt]]
            [babashka.curl :as curl]
            [cheshire.core :as json])
  (:import [java.util Date]
           [java.time LocalDate]))

(defcmd today "Prints today's date" [] (println (Date.)))

(defcmd on-this-day "Prints top events that occurred on a day like today, but some years ago"
  [^{:doc "Numbers of years ago" :parse-fn read-string
     :validate [int? "Must be number"]} years
   & [^{:short "-l" :doc "Language code, e.g. en (English), es(Spanihs), etc." :default "en"} lang]]
  (let [date (-> (LocalDate/now) (.minusYears years))
        year (.getYear date)
        items (-> (curl/get (format
                             "https://api.wikimedia.org/feed/v1/wikipedia/%s/onthisday/%s/%3$tm/%3$td"
                             lang "all" date))
                  :body
                  (json/parse-string true))]
    (printf "The date was %1$tA, %1$td/%1$tb/%1$tY\n" date)
    (doseq [[type items] items
            :let [items (filter #(= year (:year %)) items)]]
      (when (seq items)
        (printf "\n====%s====\n" type)
        (doseq [event items]
          (println (:text event)))))))

(def this-ns *ns*)

;; execute your command
(when (= *file* (System/getProperty "babashka.file"))
  (cl/exec! this-ns *command-line-args*))
