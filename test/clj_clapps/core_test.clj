(ns clj-clapps.core-test
  (:require [clj-clapps.core :as cl]
            [clojure.java.shell :refer [sh]]
            [clojure.test :refer :all]
            [clojure.string :as str])
  (:import [java.time LocalDate LocalDateTime]))

(defn alike? [x y]
  (cond
    (map? x) (and (map? y)
                  (every? (fn [[k v]] (if (fn? v) (v (get y k)) (alike? v (get y k)))) x))
    (coll? x) (and (coll? y) (or (and (empty? x) (empty? y))
                                 (and (alike? (first x) (first y)) (alike? (rest x) (rest y)))))
    (fn? x) (if (fn? y) (identical? x y) (x y))
    :else (= x y)))

(defmethod assert-expr 'like? [msg form]
  (let [expected (nth form 1)
        expr (nth form 2)]
    `(let [expected# ~expected
           actual# ~expr
           res# (alike? expected# actual#)]
       (if (true? res#)
         (do-report {:type :pass :message ~msg :expected expected# :actual actual#})
         (do-report {:type :fail :message ~msg :expected expected# :actual actual#})))))

(cl/defopt use-proxy "my global option" :short "-p" :default false)

(cl/defopt global-opt1 "a global option for ..." :short "-b" :validate [int? "must be a integer"])

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
                   (parse-cmd-args (meta #'print-date) [])))))))

(deftest options-test
  (testing "validate enums"
    (is (thrown? Exception (eval '(clj-clapps.core/defopt enum-opt "Choice option" :short "-c" :enum "a,b,c"))))
    (is (some? (eval '(clj-clapps.core/defopt enum-opt "Choice option" :short "-c" :enum ["a" "b" "c"]))))))


(cl/defcmd dummy-cmd "dummy command"
  [arg1 arg2 & [^{:short "-o" :parse-fn read-string
                  :validate [int? "must be an int"]} opt1
                ^{:short "-p" :enum ["A" "B"]} opt2
                ^{:validate [#(= "w" %) "must be w"]} opt3]]
  (println "arg1:" arg1 "arg2:" arg2))

(deftest subcommands
  (testing "parsing sub commands"
    (let [cmd #'dummy-cmd
          parse-cmd-args #'clj-clapps.core/parse-cmd-args]
      (is (like? {:exit-message #(str/includes? % "dummy-cmd [options] arg1 arg2")
                  :ok? true}
                 (parse-cmd-args (meta cmd) ["-h"])))
      (is (like? {:exit-message #(str/includes? % "Wrong number of arguments. Expected 2")
                  :ok? nil}
                 (parse-cmd-args (meta cmd) [])))
      (is (like? {:cmd-fn some?}
                 (parse-cmd-args (meta cmd) ["a" "b"])))
      (is (like? {:exit-message #(str/includes? % "must be an int")}
                 (parse-cmd-args (meta cmd) ["-o" "xyz" "a" "b"])))
      (is (like? {:cmd-fn some?}
                 (parse-cmd-args (meta cmd) ["-o" "5" "a" "b"]))))))

(deftest cmd-execs
  (testing "parsing main commands"
    ))
