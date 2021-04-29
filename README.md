# clj-clapps
[![Build Status](https://travis-ci.com/clj-clapps/clj-clapps.svg?branch=main)](https://travis-ci.org/clj-clapps/clj-clapps)
[![codecov](https://codecov.io/gh/clj-clapps/clj-clapps/branch/main/graph/badge.svg?token=JZJUAVUYCB)](https://codecov.io/gh/clj-clapps/clj-clapps)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.clj-clapps/clj-clapps.svg)](https://clojars.org/org.clojars.clj-clapps/clj-clapps)

A Clojure library to write command line apps in a simple and elegant way.

Inspired by Python Typer, and built on top of Clojure's [tools.cli](https://github.com/clojure/tools.cli) library


```clj
[org.clojars.clj-apps/clj-clapps "0.4.7"]
```

## Usage

Add **clj-clapps** as a dependency:

Lein:

```clojure
;; ...
[org.clojars.clj-apps/clj-clapps "0.4.7"]
;; ...
```

Clojure deps.edn:

```clojure
;; ...
org.clojars.clj-apps/clj-clapps {:mvn/version "0.4.7"}
;; ...

```

Declare and specify your **command** function with **defcmd**:


```clojure

(ns my-cool-cli
  (:gen-class)
  (:require [clj-clapps.core :as cl]))
    
;; define your command function
(cl/defcmd main-cmd
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
  ;;...
  )
;; ...

(cl/defcmd start "start action" [port])

(cl/defcmd stop "stop action" [port])

(cl/defcmd re-start "re-start action" [port])
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

(cl/defopt debug "enable debug mode" :short "-d")

```
Then `debug` var will be bound to corresponding command option value, and can be used in any function.

### Supported Metadata

The following metadata options are supported in both global and command options:

* `:short` The short prefix string of the option. Required. e.g. `-o`
* `:long?` Defaults to true. It's used to disable the long option prefix. By default the l*refix matches the option name prefixed with `--`. e.g. `--debug DEBUG` above. The long pr*is also implicitly disabled for boolean options as derived by the :default value or endin*`?`
* `:doc` Option documentation, used when printing the command usage.
* `:default` Option's default value.
* `:default-fn` Option's default value function.
* `:validate` A tuple of validation function and message. e.g. `[int? "must be a number"]`
* `:enum` A vector of allowed values. e.g. `:enum ["AM" "PM"]`
* `:parse-fn` A function to convert the input string into the desired type
* `:env` A string representing the environment variable to use as a default value. Equivalent to `:default-fn #(System/getenv "SOME_ENV_VAR")`
    
The following metadata options are supported in command arguments.


* `:doc` Argument documentation, used when printing the command usage.
* `:validate` A tuple of validation function and message. e.g. `[int? "must be a number"]`
* `:enum` A vector of allowed values. e.g. `:enum ["AM" "PM"]`
* `:parse-fn` A function to convert the input string into the desired type

## License

Copyright © 2018 clj-clapps

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
    
