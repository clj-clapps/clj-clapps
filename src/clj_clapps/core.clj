(ns clj-clapps.core
  (:require [clj-clapps.spec :as spec]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.walk :as walk])
  (:import [java.io File PrintStream]))

(defn- arg-meta [arg]
  (assoc (meta arg) :name (name arg)))

(defn- cmd-params-opts [args]
  (let [[req-args [_ & [opt-args]]] (split-with #(not= (symbol "&") %) args)]
    [(mapv arg-meta req-args)
     (mapv arg-meta opt-args)]))

(defmacro defcmd
  "Like defn, but the resulting function can be invoked from the command line
  The docstring? and the arguments metadata are used to generate the CLI options.

  Example:
  (defcmd say-hello \"Say Hello command\"
      [name & [^{:short \"-l\" :enum [\"en\" \"es\"] :default \"en\"} lang]]
      ;; do something with name and lang
   )

  The required arguments are converted to positional command arguments.
  A vector of optional arguments can be specified, and will be converted to optional command arguments.

  The following metadata can be used with required and optional arguments:
  :doc         Argument documentation
  :validate    A tuple like [validate-fn validate-msg] to validate the passed argument value
  :enum        A vector of allowed values
  :parse-fn    A function to convert the option value string to the desired type

  The optional arguments additionally support the following metadata:
  :short       The short option prefix, e.g \"-o\"
  :long?       Allow long name option. Defaults to true. Long name is generated from the argument name.
  :default     The default value.
  :default-fn  A function invoke to get the default value
  "
  {:arglists '([name docstring? [params*] body])}
  [cmd args & body]
  (let [doc (when (string? args) args)
        [args body] (if (string? args) [(first body) (rest body)] [args body])
        args (into [] (walk/prewalk #(vary-meta % eval) args))
        [req-args-meta opt-args-meta] (cmd-params-opts args)]
    (doseq [arg req-args-meta]
      (when-let [probs (s/explain-data ::spec/param-meta (dissoc arg :name))]
        (throw (Exception. (format "Invalid param %s metadata:\n%s" (:name arg)
                                   (with-out-str (s/explain-out probs)))))))
    (doseq [opt opt-args-meta]
      (when-let [probs (s/explain-data ::spec/opt-args (->> (dissoc opt :name) (apply concat)))]
        (throw (Exception. (format "Invalid param %s metadata:\n%s" (:name opt)
                                   (with-out-str (s/explain-out probs)))))))
    `(defn ~(vary-meta cmd assoc :command? true :doc doc) ~args
       ~@body)))

(defmacro defopt
  "Declares a global variable that's exposed as a command line option.
  The docstring? and the options are used to generate the CLI options.
  Example:
  (defopt verbose \"Verbosity Level\" :long? false :short \"-v\" :update-fn inc)

  The following options are allowed, and have the semantics as the corresponding options in Clojure tools.cli
  :short       The short option prefix, e.g \"-o\"
  :long?       Allow long name option. Defaults to true. Long name is generated from the argument name.
  :default     The default value.
  :default-fn  A function invoke to get the default value
  :validate    A tuple like [validate-fn validate-msg] to validate the passed option value
  :enum        A vector of allowed values
  :parse-fn    A function to convert the option value string to the desired type"
  {:arglists '([name docstring? [options*]])}
  [opt-name & [doc?  & opts]]
  (let [[doc? opts] (if (string? doc?) [doc? opts] [nil (cons doc? opts)])
        _ (when-let [probs (s/explain-data ::spec/opt-args (eval (into [] opts)))]
            (throw (Exception. (format "Invalid option arguments:\n%s"
                                       (with-out-str (s/explain-out probs))))))
        opts (apply hash-map :doc doc? opts)]
    `(def ~(vary-meta opt-name merge (assoc opts :option? true)) (:default ~opts))))

(defopt verbose "Verbosity level" :short "-v" :long? false :default 0 :update-fn inc)

(defopt help "Print help" :short "-h" :default false)

(defn print-error [msg]
  (.println System/err msg))

(defn exit
  "Prints `msg` and exist with the given `status`"
  [status msg]
  (if (zero? status)
    (println msg)
    (print-error msg))
  (System/exit status))

(defn prompt
  "Prompts for user input.
  The following options are supported:
  :password?  If true, it will hide the user input
  "
  {:arglists '([message [options*]])}
  [msg & {:keys [password?]}]
  (if-let [console (System/console)]
    (if password?
      (str/join (.readPassword console msg (into-array [])))
      (.readLine console msg (into-array [])))
    (print-error "Terminal console not available!")))

(defmacro exit-on-error
  "Tries to execute the action, if successful returns action result, otherwise prints the given message to std error"
  {:arglists '([action message]
               [action message-fn])}
  [action msg]
  (let [ex (gensym)]
    `(try
       ~action
       (catch Exception ~ex
         (exit 1 ~(if (fn? msg) `(~msg ~ex) msg))))))

(defn- opt-long [v]
  (let [opt-name (:name v)]
    (cond
      (false? (:long? v)) nil
      (or (boolean? (:default v)) (str/ends-with? opt-name "?")) (str "--" opt-name)
      :else (str "--" opt-name " " (str/upper-case opt-name)))))

(defn- enum-check-fn [enum]
  (fn [val]
    (some #(= val %) enum)))

(defn- env-fn [env-var default]
  (fn [_]
    (or
     (System/getenv (str/upper-case env-var))
     default)))

(defn- meta->option [{:keys [enum doc short env] :as v}]
  (let [v (cond-> v
            enum (update :validate concat
                         [(enum-check-fn enum) (format "Must be one of %s" (str/join "," enum))])
            env (assoc :default-fn (env-fn env (:default v))))]
    (->> (apply concat
                [(:short v) (opt-long v) (:doc v)]
                [:id (keyword (name (:name v)))]
                (select-keys v [:default :parse-fn :validate :update-fn :default-fn]))
         (into []))))

(defn- opts-meta->cli-options [opts]
  (->> (map meta->option opts)
       (into [])))

(defn- global-options [ns]
  (concat
   (->> (ns-interns ns)
        (map (comp meta second))
        (filter :option?))
   [(meta #'verbose) (meta #'help)]))

(defn- sub-commands [ns]
  (->> (ns-interns ns)
       (map (comp meta second))
       (filter :command?)))

(defn- usage [ns desc options & {:keys [sub-cmds global-options cmd-name args]}]
  (->> [desc
        ""
        (->> ["Usage:" (str ns)
              (when global-options "[GLOBAL-OPTIONS]")
              cmd-name (when options "[OPTIONS]") (when (seq sub-cmds) "COMMAND")
              (map (comp str/upper-case :name) args)]
             flatten
             (filter some?)
             (str/join " "))
        (when (seq args)
          ["" "Arguments:"
           (for [arg args]
             (format "\t%s\t%s" (str/upper-case (:name arg)) (or (:doc arg) "")))])
        (when options
          ["" "Options:" options ""])
        (when (seq sub-cmds)
          (cons "Commands:"
                (map #(str "\t" (name (:name %)) "\t" (:doc %)) sub-cmds)))
        (when global-options
          ["" "Global-Options:" global-options ""])]
       (flatten)
       (str/join \newline)))

(defn- error-msg [errors]
  (str "Invalid command options and/or arguments:" \newline
       (str/join \newline errors)))

(defn- set-options! [opts values]
  (doseq [[k v] values]
    (alter-var-root (some #(when (= (name (:name %)) (name k))
                             (ns-resolve (:ns %) (:name %))) opts) (constantly v))))

(defn- parse-arg [arg arg-meta]
  (let [validations (concat
                     (some-> (:enum arg-meta)
                             (enum-check-fn)
                             (vector (format "Invalid argument %s. It should be one of: %s"
                                             (:name arg-meta) (str/join "," (:enum arg-meta)))))
                     (:validate arg-meta))
        result (try
                 {:value ((or (:parse-fn arg-meta) identity) arg)}
                 (catch Exception e
                   {:error (format "Error parsing argument %s :%s"
                                   (:name arg-meta) (.getMessage e))}))]
    (or (not-empty (select-keys result [:error]))
        (some #(when-not ((first %) (:value result))
                 {:error (format "Invalid argument %s. %s" (:name arg-meta) (second %))})
              (partition 2 validations))
        result)))

(defn- validate-args [args arg-values]
  (let [[values errors] (if (not= (count args) (count arg-values))
                          [arg-values
                           [(format "Wrong number of arguments. Expected %d"  (count args))]]
                          (->> (mapv parse-arg arg-values args)
                               ((juxt (partial mapv :value)
                                      (partial mapv :error)))))]
    {:arguments values
     :errors (filter some? errors)}))

(defn- parse-cmd-args [cmd {:keys [main?] :as main-args-opts}]
  (let [[req-args opt-args] (cmd-params-opts (-> cmd :arglists first))
        {:keys [options arguments errors summary]
         :as cmd-opts-args} (cli/parse-opts (:arguments main-args-opts)
                                            (opts-meta->cli-options
                                             (concat opt-args [(meta #'help)])))]
    (cond
      (or (:help (:options main-args-opts))
          (:help options)) {:exit-message
                            (if main?
                              (usage (:ns cmd) (:doc cmd)
                                     (str summary \newline (:summary main-args-opts))
                                     :args req-args)
                              (usage (:ns cmd) (:doc cmd) summary
                                     :global-options (:summary main-args-opts)
                                     :args req-args
                                     :cmd-name (:name cmd)))
                            :ok? true}
      errors {:exit-message (error-msg errors)}
      :else (let [{:keys [arguments errors]} (validate-args req-args arguments)]
              (if (seq errors)
                {:exit-message (error-msg errors)}
                {:cmd-fn (ns-resolve (:ns cmd) (:name cmd))
                 :cmd-args (concat arguments (map #(get options (keyword (:name %))) opt-args))})))))

(defn- parse-main-args [ns args]
  (let [sub-cmds (sub-commands ns)
        multi? (> (count sub-cmds) 1)
        global-opts (global-options ns)
        {:keys [options arguments errors summary]
         :as main-args-opts} (cli/parse-opts args (opts-meta->cli-options global-opts) :in-order true)]
    (cond
      (empty? sub-cmds) {:exit-message
                         (format "No commands defined in ns:%s, at least one (defcmd ...) should be defined" (str ns))}
      (not multi?) (assoc main-args-opts
                          :command (first sub-cmds)
                          :global-opts global-opts
                          :arguments arguments
                          :main? true)
      (:help options) {:exit-message (usage ns (-> ns the-ns meta :doc) nil
                                            :sub-cmds sub-cmds :global-options summary)
                       :ok? true}
      errors {:exit-message (error-msg errors)}
      (empty? (first arguments)) {:exit-message
                                  (usage ns "Missing command" nil
                                         :sub-cmds sub-cmds :global-options summary)}
      :else (if-let [cmd (first (filter #(= (first arguments) (-> % :name name)) sub-cmds))]
              (assoc main-args-opts
                     :command cmd
                     :arguments (rest arguments)
                     :global-opts global-opts)
              {:exit-message (str "Unknown command: " (first arguments))}))))

(defn- generate-error-trace
  [prefix e]
  (let [tmp (File/createTempFile prefix "-error.trace")]
    (spit tmp (ex-data e))
    (.printStackTrace e (PrintStream. tmp))
    (print-error (format " Error dump created in:%s" tmp))))

(defn exec!
  "Parses the arguments and executes the specified command"
  {:arglists '([main-class-ns arguments])}
  [ns args]
  (let [{:keys [exit-message options command ok? global-opts] :as parsed}
        (parse-main-args ns args)
        _ (when exit-message
            (exit (if ok? 0 1) exit-message))
        {:keys [exit-message ok? cmd-fn cmd-args]} (parse-cmd-args command parsed)]
    (when exit-message
      (exit (if ok? 0 1) exit-message))
    (set-options! global-opts options)
    (try
      (apply cmd-fn cmd-args)
      (catch Exception e
        (print-error (format "Unhandled exception when executing your command: %s" (.getMessage e)))
        (if (pos? verbose)
          (.printStackTrace e)
          (generate-error-trace (str ns) e))
        (exit 1 "")))))
