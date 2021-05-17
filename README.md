# clj-clapps
[![Build Status](https://travis-ci.com/clj-clapps/clj-clapps.svg?branch=main)](https://travis-ci.org/clj-clapps/clj-clapps)
[![codecov](https://codecov.io/gh/clj-clapps/clj-clapps/branch/main/graph/badge.svg?token=JZJUAVUYCB)](https://codecov.io/gh/clj-clapps/clj-clapps)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.clj-clapps/clj-clapps.svg)](https://clojars.org/org.clojars.clj-clapps/clj-clapps)

A Clojure library to write command line apps in a simple and elegant way.

Inspired by Python Typer, and built on top of Clojure's [tools.cli](https://github.com/clojure/tools.cli) library


```clj
[org.clojars.clj-clapps/clj-clapps "0.4.10"]
```

## Usage

Add **clj-clapps** as a dependency:

Lein:

```clojure
;; ...
[org.clojars.clj-clapps/clj-clapps "0.4.10"]
;; ...
```

Clojure deps.edn:

```clojure
;; ...
org.clojars.clj-clapps/clj-clapps {:mvn/version "0.4.10"}
;; ...

```

Declare and specify your **command** function with **defcmd**:


```clojure

(ns my-cool-cli
  (:gen-class)
  (:require [clj-clapps.core :as cl :refer [defcmd defopt]]))

;; define your command function
(defcmd main-cmd
  "My cool command help description"
  [^{:doc "Argument 1 of cool command" } arg1
  ;; optional arguments vector become command options
   & [^{:doc "Option 1 of cool command" :short "-o" } opt1]]
  ;; do something with arg1 and opt1
  )

;; execute your command
(defn -main [& args]
  (cl/exec! 'my-cool-cli args))
```

The function and arguments metadata are turned into [command line options](https://github.com/clojure/tools.cli)!

Executing the above namespace should output the following:

```bash
$ lein run -m my-cool-cli -h
My cool command help description

Usage:
my-cool-cli [options] arg1

Arguments:
    arg1	Argument 1 of cool command

Options:
	-o --opt1 OPT1	Option 1 of cool command
```

### Sub Commands

Multiple command definitions in a namespace are turned into sub-commands, with the command function name matching the sub-command name:

e.g.

```clojure
(ns service
  (:gen-class)
  (:require [clj-clapps.core :as cl :refer [defcmd defopt]])
  ;;...
  )
;; ...

(defcmd start "start action" [port])

(defcmd stop "stop action" [port])

(defcmd re-start "re-start action" [port])
;; ...
```
The above can be invoked as `clj -M -m service start 8080` or `clj -M service stop 8080` , etc.

Sub commands help option is implicitly added with *-h* or **--help**:

`clj -M -m service start -h` will print:

```bash
Usage service [global-options] start [options] port

Arguments:
	port	start action

Options:
	-h  --help	Prints command help
```

### Global Options

When implementing multiple sub-commands, some common options can be factored out as global options using the macro `defopt`.

e.g.

```clojure

(defopt debug "enable debug mode" :short "-d")

```
Then `debug` var will be bound to corresponding command option value, and can be used in any function.

### Supported Metadata

The following metadata options are supported in both global and command options:

* `:short` The short prefix string of the option. Required. e.g. `-o`
* `:long?` Defaults to true. It's used to disable the long option prefix. By default the long prefix is generated as the option name prefixed with `--` e.g. `--debug DEBUG` above. The long prefix is implicitly disabled for boolean options (as inferred from the `:default` value or the suffix: `?`)
* `:doc` Option documentation, used when printing the command usage.
* `:default` Option's default value.
* `:default-fn` A one-arg function that returns the option's default value, given the parsed options.
* `:validate` A tuple of validation function and message. e.g. `[int? "must be a number"]`
* `:enum` A vector of allowed values. e.g. `:enum ["AM" "PM"]`
* `:parse-fn` A function that converts the input string into the desired type
* `:env` A string representing the environment variable to use as a default value. Equivalent to `:default-fn (fn[_] (System/getenv "SOME_ENV_VAR"))`

The following metadata options are supported in command arguments.


* `:doc` Argument documentation, used when printing the command usage.
* `:validate` A tuple of validation function and message. e.g. `[int? "must be a number"]`
* `:enum` A vector of allowed values. e.g. `:enum ["AM" "PM"]`
* `:parse-fn` A function to convert the input string into the desired type

### Usage With [Babashka](https://babashka.org/)

[@borkdude](https://github.com/borkdude) has shown that this library can be used with [babashka](https://github.com/borkdude). Please see the [demo](https://github.com/clj-clapps/clj-clapps/issues/1) for more details.

In short, the [spartan.spec](https://github.com/borkdude/spartan.spec) needs to be added as dependency.

```clojure
#!/usr/bin/env bb

(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {org.clojars.clj-clapps/clj-clapps {:mvn/version "0.4.10"}
                        borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                               :sha "12947185b4f8b8ff8ee3bc0f19c98dbde54d4c90"}}})

(require 'spartan.spec) ;; defines clojure.spec.alpha

(ns my-cool-cli
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
(when (= *file* (System/getProperty "babashka.file"))
  (cl/exec! 'my-cool-cli *command-line-args*))
```


## License

Copyright © 2021 clj-clapps

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

