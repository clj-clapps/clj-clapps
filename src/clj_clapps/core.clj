(ns clj-clapps.core
  (:require [clj-clapps.spec :as spec]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.walk :as walk]))

(defn- eval-symbols [x]
  (cond
    (or (symbol? x) (list? x)) (eval x)
    (instance? clojure.lang.Cons x) (eval x)
    :else x))

(defn- eval-meta [x]
  (vary-meta x (partial walk/prewalk eval-symbols)))

(defn- arg-meta [arg]
  (assoc (meta arg) :name (name arg)))

(defn- cmd-params-opts [args]
  (let [[req-args [_ & [opt-args]]] (split-with #(not= (symbol "&") %) args)]
    [(map arg-meta req-args)
     (map arg-meta opt-args)]))

(defmacro defcmd
  "Like defn, but the resulting function can be invoked from the command line
  The docstring? and the arguments metadata are used to generate the cli options.

  Example:
  (defcmd say-hello \"Say Hello command\"
      [name & [^{:short \"-l\" :enum [\"en\" \"es\"] :default \"en\"} lang]]
      ;; do something with name and lang
      )

  The arguments metadata supports the following attributes:
  :short			The short option prefix, e.g \"-o\"
  :long?			Allow long name option. Defaults to true. Long name is generated from the argument name.
  :default		The default value.
  :default-fn	A function invoke to get the default value
  :validate		A tuple like [validate-fn validate-msg] to validate the passed option value
  :enum				A vector of allowed values
  :parse-fn		A function to convert the option value string to the desired type"
  {:arglists '([name docstring? [params*] body])}
  [cmd args & body]
  (let [doc# (when (string? args) args)
        [args# body#] (if (string? args) [(first body) (rest body)] [args body])
        args# (into [] (walk/prewalk eval-meta args#))
        [req-args-meta opt-args-meta] (cmd-params-opts args#)]
    (doseq [arg req-args-meta]
      (when-let [probs (s/explain-data ::spec/param-meta (dissoc arg :name))]
        (throw (Exception. (format "Invalid param %s metadata:\n%s" (:name arg)
                                   (with-out-str (s/explain-out probs)))))))
    (doseq [opt opt-args-meta]
      (when-let [probs (s/explain-data ::spec/opt-args (->> (dissoc opt :name) (apply concat)))]
        (throw (Exception. (format "Invalid param %s metadata:\n%s" (:name opt)
                                   (with-out-str (s/explain-out probs)))))))
    `(defn ~(vary-meta cmd assoc :command? true :doc doc#) ~args#
       ~@body#)))

(defmacro defopt
  "Declares a global variable that's exposed as a command line option.
  The docstring? and the options are used to generate the cli options.
  Example:
  (defopt verbose \"Verbosity Level\" :long? false :short \"-v\" :update-fn inc)

  The following options are allowed, and have the semantics as the corresponding options in Clojure tools.cli
  :short			The short option prefix, e.g \"-o\"
  :long?			Allow long name option. Defaults to true. Long name is generated from the argument name.
  :default		The default value.
  :default-fn	A function invoke to get the default value
  :validate		A tuple like [validate-fn validate-msg] to validate the passed option value
  :enum				A vector of allowed values
  :parse-fn		A function to convert the option value string to the desired type"
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

(defn exit
  "Prints `msg` and exist with the given `status`"
  [status msg]
  (println msg)
  (System/exit status))

(defn prompt
  "Prompts for user input.
  The following options are supported:
  :password?	If true, it will hide the user input
  "
  {:arglists '([message [options*]])}
  [msg & {:keys[password?]}]
  (if password?
    (.readPassword (System/console) msg (into-array []))
    (.readLine (System/console) msg (into-array []))))

(defmacro exit-on-error [action & {:keys [message message-fn]}]
  (let [ex (gensym)]
    `(try
       ~action
       (catch Exception ~ex
         (exit 1 ~(if message-fn `(~message-fn ~ex) message))))))

(defn- opt-long [v]
  (let [opt-name (:name v)]
    (cond
      (false? (:long? v)) nil
      (or (boolean? (:default v)) (str/ends-with? opt-name "?")) (str "--" opt-name)
      :else (str "--" opt-name " " (str/upper-case opt-name)))))

(defn- enum-check-fn [enum]
  (fn [val]
    (some #(= val %) enum)))

(defn- env-fn [env-var]
  (fn [_]
    (System/getenv (str/upper-case env-var))))

(defn- meta->option [{:keys [enum doc short env] :as v}]
  (let [v (cond-> v
            enum (update :validate concat
                         [(enum-check-fn enum) (format "Must be one of %s" (str/join "," enum))])
            env (assoc :default-fn (env-fn env)))]
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

(defn- usage [cmd-name cmd-desc opts-summary sub-cmds args]
  (->> [cmd-desc
        ""
        (str "Usage: " cmd-name " [options] " (when (seq sub-cmds) "command")
             (str/join " " args))
        (when opts-summary
          ["Options:" opts-summary ""])
        (when (seq sub-cmds)
          (cons "Commands:"
                (map #(str "\t" (name (:name %)) "\t" (:doc %)) sub-cmds)))]
       (flatten)
       (str/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn- set-options! [opts values]
  (doseq [[k v] values]
    (alter-var-root (some #(when (= (name (:name %)) (name k))
                             (ns-resolve (:ns %) (:name %))) opts) (constantly v))))

(defn- validate-arg [arg value]
  (let [{:keys [parse-fn validate] :as meta-arg} arg
        [value error] (if parse-fn
                        (try
                          [(parse-fn value)]
                          (catch Exception e
                            [nil (str "Failed parsing :" value " for argument" (:name arg))]))
                        [value])]
    (if error
      [nil error]
      (if (and validate (not ((first validate) value)))
        [nil (or (second validate) (str "Invalid value: " value " for " (:name arg)))]
        [value (when (nil? value) "Missing argument:" (str/upper-case (:name arg)))]))))

(defn- validate-args [args arg-values]
  (let [[values errors] (if (not= (count args) (count arg-values))
                          [arg-values
                           [(format "Wrong number of arguments. Expected %d"  (count args))]]
                          (->> (mapv validate-arg args arg-values)
                               ((juxt (partial sequence (map first))
                                      (partial sequence (map second))))))]
    {:arguments values
     :errors (filter some? errors)}))



(defn- parse-cmd-args [cmd args]
  (let [[req-args opt-args] (cmd-params-opts (-> cmd :arglists first))
        {:keys[options arguments errors summary]}
        (cli/parse-opts args (opts-meta->cli-options (concat opt-args [(meta #'help)])))]
    (cond
      (:help options)
      {:exit-message (usage (str (-> cmd :ns ns-name) " [global-options] " (:name cmd))
                            (:doc cmd) summary [] (map :name req-args)) :ok? true}
      errors
      {:exit-message (error-msg errors)}
      :else
      (let [{:keys [arguments errors]} (validate-args req-args arguments)]
        (if (seq errors)
          {:exit-message (error-msg errors)}
          {:cmd-fn (ns-resolve (:ns cmd) (:name cmd))
           :cmd-args (concat arguments (map #(get options (keyword (:name %))) opt-args))})))))

(defn- parse-main-args [ns args]
  (let [global-opts (global-options ns)
        {:keys [options arguments errors summary]}
        (cli/parse-opts args (opts-meta->cli-options global-opts) :in-order true)
        sub-cmds (sub-commands ns)]
    (cond
      (:help options)
      {:exit-message (usage (ns-name ns) "" summary sub-cmds []) :ok? true}
      errors
      {:exit-message (error-msg errors)}
      (empty? sub-cmds)
      {:exit-message "No commands implemented, at least one defcmd should be defined"}
      (= 1 (count sub-cmds))
      {:command (first sub-cmds) :arguments arguments :options options :global-opts global-opts}
      (empty? (first arguments))
      {:exit-message (usage (ns-name ns) "Missing command" summary sub-cmds [])}
      :else (if-let [cmd (first (filter #(= (first arguments) (-> % :name name)) sub-cmds))]
              {:command cmd :arguments (rest arguments) :options options :global-opts global-opts}
              {:exit-message (str "Unknown command: " (first arguments))}))))

(defn exec! [ns args]
  (let [{:keys[exit-message arguments options command ok? global-opts]} (parse-main-args ns args)
        _ (when exit-message
            (exit (if ok? 0 1) exit-message))
        {:keys[exit-message ok? cmd-fn cmd-args]} (parse-cmd-args command arguments)]
    (when exit-message
      (exit (if ok? 0 1) exit-message))
    (set-options! global-opts options)
    (apply cmd-fn cmd-args)))
