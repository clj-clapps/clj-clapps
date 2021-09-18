(ns clj-clapps.core-test
  (:require [clj-clapps.core :as cl]
            [clj-clapps.util :as util]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import clojure.lang.ExceptionInfo
           [java.time LocalDate LocalDateTime]))

(cl/defopt use-proxy "my global option" :short "-p" :default false)

(cl/defopt global-opt1 "a global option for ..." :short "-b" :parse-fn read-string
  :validate [int? "must be a integer"])

(defn- valid-day? [d]
  (#{"Mon" "Fri"} d))

(cl/defcmd print-date
  [date-name &
   [with-time?
    ^{:short "-z" :default "UTC"} tz
    ^{:validate [valid-day? "Must be valid day"]} day]]
  (println "date " date-name "is" (if with-time? (LocalDateTime/now) (LocalDate/now))))

(def this-ns *ns*)

(deftest cmd-macros
  (let [global-options #'clj-clapps.core/global-options
        parse-main-args #'clj-clapps.core/parse-main-args
        parse-cmd-args #'clj-clapps.core/parse-cmd-args]
    (testing "discover global options "
      (let [opts (global-options this-ns)]
        (is (some #(= "use-proxy" (name (:name %))) opts))))
    (testing "discover commands:"
      (let [cmds (#'clj-clapps.core/sub-commands this-ns)]
        (is (some #(= "print-date" (name (:name %))) cmds))))
    (testing "generate cli options"
      (is (like? [["-p" "--use-proxy" "my global option"]
                  ["-b" "--global-opt1 GLOBAL-OPT1" "a global option for ..."]
                  ["-v" nil "Verbosity level"]
                  ["-h" "--help" "Print help"]]
                 (map (partial take 3) (#'clj-clapps.core/opts-meta->cli-options
                                        (global-options this-ns))))))
    (testing "parse args:"
      (let [r (parse-main-args this-ns ["-h"])]
        (is (:exit-message r))
        (is (:ok? r)))
      (let [r (parse-main-args this-ns ["print-date" "--help"])]
        (is (like? {:command some?} r))
        (is (like? {:exit-message some? :ok? nil?}
                   (parse-cmd-args (meta #'print-date) {:arguments []})))))))

(deftest options-test
  (testing "validate enums"
    (is (thrown? Exception (eval '(clj-clapps.core/defopt enum-opt "Choice option" :short "-c" :enum "a,b,c"))))
    (is (some? (eval '(clj-clapps.core/defopt enum-opt "Choice option" :short "-c" :enum ["a" "b" "c"]))))))

(cl/defcmd dummy-cmd "dummy command"
  [arg1 arg2 & [^{:short "-o" :parse-fn read-string
                  :default-fn (constantly (LocalDate/now))
                  :validate [int? "must be an int"]} opt1
                ^{:short "-p" :enum ["A" "B"]} opt2
                ^{:validate [#(= "w" %) "must be w"]} opt3]]
  (println "arg1:" arg1 "arg2:" arg2))

(cl/defcmd expr
  "evaluates arithmetic expression"
  [^{:parse-fn read-string :validate [number? "Must be a number"]} n1
   ^{:enum ["+" "-" "*" "/"]} op
   ^{:parse-fn read-string :validate [number? "Must be a number"]} n2]
  (let [result (case op
                 "+" (+ n1 n2)
                 "-" (- n1 n2)
                 "*" (* n1 n2)
                 "/" (/ n1 n2))]
    (println result)))

(deftest subcommands
  (let [parse-cmd-args #'clj-clapps.core/parse-cmd-args]
    (testing "parsing sub commands"
      (let [cmd #'dummy-cmd]
        (is (like? {:exit-message #(str/includes? % "[GLOBAL-OPTIONS] dummy-cmd [OPTIONS] ARG1 ARG2")
                    :ok? true}
                   (parse-cmd-args (meta cmd) {:arguments ["-h"] :summary "\t-v\t\tVerbosity Level"})))
        (is (like? {:exit-message #(str/includes? % "Wrong number of arguments. Expected 2")
                    :ok? nil}
                   (parse-cmd-args (meta cmd) {:arguments []})))
        (is (like? {:cmd-fn some?}
                   (parse-cmd-args (meta cmd) {:arguments ["a" "b"] :main? false})))
        (is (like? {:exit-message #(str/includes? % "must be an int")}
                   (parse-cmd-args (meta cmd) {:arguments ["-o" "xyz" "a" "b"] :main? false})))
        (is (like? {:cmd-fn some?}
                   (parse-cmd-args (meta cmd) {:arguments ["-o" "5" "a" "b"] :main? false})))
        (is (like? {:exit-message #(and (str/includes? % "[GLOBAL-OPTIONS]")
                                        (str/includes? % "Global-Options:\n")
                                        (str/includes? % "...global-options"))}
                   (parse-cmd-args (meta cmd) {:arguments ["-h"] :summary "...global-options..."})))))
    (testing "validating sub command args"
      (let [cmd #'expr]
        (is (like? {:exit-message #(str/includes? % "Invalid argument n1. Must be a number")}
                   (parse-cmd-args (meta cmd) {:arguments ["x" "+" "1"]})))
        (is (like? {:exit-message #(str/includes? % "Invalid argument op. It should be one of: +,-,*,/")}
                   (parse-cmd-args (meta cmd) {:arguments ["3.12" "x" "1"]})))))))

(deftest cmd-execs
  (testing "parsing main commands"
    (let [parse-main-args #'clj-clapps.core/parse-main-args]
      (is (like? {:exit-message #(str/includes? % "core-test [GLOBAL-OPTIONS] COMMAND")}
                 (parse-main-args this-ns ["-h"])))
      (is (like? {:exit-message #(str/includes? % "must be a integer")}
                 (parse-main-args this-ns ["-b" "x"])))
      (is (like? {:command some?}
                 (parse-main-args this-ns ["-b" "4" "dummy-cmd" "a" "b"])))
      (is (like? {:command some?}
                 (parse-main-args this-ns ["dummy-cmd" "-h"])))))

  (testing "executing commands"
    (with-redefs [clj-clapps.core/exit #(throw (ex-info "Cmd Exit" {:status %1 :msg %2}))]
      (is (thrown? ExceptionInfo (cl/exec! this-ns ["-h"])))
      (is (nil? (cl/exec! this-ns ["dummy-cmd" "1" "2"]))))))

(deftest error-handling
  (testing "unhandled errors"
    (with-redefs [clj-clapps.core/exit (fn [status _] (is (= 1 status)))
                  clj-clapps.core/print-error (fn [msg] (is (str/includes? msg "Unhandled exception")))]
      (cl/exec! this-ns ["-v" "expr" "3.1415" "/" "0"]))))

