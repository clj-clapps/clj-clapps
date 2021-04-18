(ns clj-clapps.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::short (s/and string? #(str/starts-with? % "-")))

(s/def ::long? boolean?)

(s/def ::validate (s/tuple fn? string?))

(s/def ::enum coll?)

(s/def ::required boolean?)

(s/def ::default any?)

(s/def ::default-fn fn?)

(s/def ::parse-fn fn?)

(s/def ::assoc-fn fn?)

(s/def ::update-fn fn?)

(s/def ::env string?)

(s/def ::opt-args (s/keys* :opt-un [::short
                                    ::long?
                                    ::validate
                                    ::enum
                                    ::required
                                    ::default
                                    ::default-fn
                                    ::parse-fn
                                    ::assoc-fn
                                    ::update-fn
                                    ::env]))

(s/def ::param-meta (s/keys :opt-un [::validate ::enum ::parse-fn]))
