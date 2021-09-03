(ns clj-clapps.single-cmd-test
  (:require [clj-clapps.core :as cl :refer [defcmd defopt]]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import java.time.LocalDate))

(defopt user "the service username" :short "-u" :env "USER")
(defopt pass "the service password" :short "-p" :env "PASS")

(defcmd main-cmd
  "the only command"
  [^{:parse-fn read-string :validate [int? "must be a number"]} arg1
   & [^{:doc "Date option" :short "-d" :default-fn (constantly (LocalDate/now))} date]]
  (pr arg1 date))

(def this-ns *ns*)

(deftest test-single-cmd
  (testing "usage output"
    (let [parse-main-args #'clj-clapps.core/parse-main-args
          parse-cmd-args #'clj-clapps.core/parse-cmd-args
          cmd #'main-cmd]
      (is (like? {:command {:name 'main-cmd}
                  :options {:help nil?}
                  :main? true}
                 (parse-main-args this-ns [])))
      (is (like? {:exit-message #(str/includes? % "must be a number")}
                 (parse-cmd-args (meta cmd) {:arguments ["abc"] :main? true})))
      (is (like? {:exit-message #(str/includes? % "single-cmd-test [OPTIONS] ARG1")}
                 (parse-cmd-args (meta cmd) {:arguments ["-h"] :main? true})))
      (is (like? {:exit-message #(re-find #".+-v\s+Verbosity.*" %)}
                 (parse-cmd-args (meta cmd) {:arguments ["-h"] :main? true}))))))

