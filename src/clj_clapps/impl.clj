(ns clj-clapps.impl
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn eq [c]
  (fn [v] (= v c)))

(defn opt-spec [opt-key value-spec]
  (s/cat :key (eq opt-key) :value value-spec))

(s/def ::short-opt (opt-spec :short (s/and string? #(str/starts-with? % "-"))))

(s/def ::long-opt (opt-spec :long? boolean?))

(s/def ::validate-opt (opt-spec :validate (s/tuple fn? string?)))

(s/def ::enum-opt (opt-spec :enum coll?))

(s/def ::required-opt (opt-spec :required boolean?))

(s/def ::default-opt (opt-spec :default any?))

(s/def ::default-fn-opt (opt-spec :default-fn fn?))

(s/def ::parse-fn-opt (opt-spec :parse-fn fn?))

(s/def ::assoc-fn-opt (opt-spec :assoc-fn fn?))

(s/def ::update-fn-opt (opt-spec :update-fn fn?))

(s/def ::env-opt (opt-spec :env string?))

(s/def ::valid-opt (s/alt :short ::short-opt
                          :long? ::long-opt
                          :validate ::validate-opt
                          :enum ::enum-opt
                          :required ::required-opt
                          :default ::default-opt
                          :default-fn ::default-fn-opt
                          :parse-fn ::default-fn-opt
                          :assoc-fn ::assoc-fn-opt
                          :update-fn ::update-fn-opt
                          :env ::env-opt))

(s/def ::opt-args (s/* ::valid-opt))
