# clj-clapps
[![Build Status](https://travis-ci.org/rinconj/clj-clapps.svg?branch=master)](https://travis-ci.org/rinconj/clj-clapps)
[![codecov](https://codecov.io/gh/rinconj/clj-clapps/branch/master/graph/badge.svg)](https://codecov.io/gh/rinconj/clj-clapps)
[![Clojars Project](https://img.shields.io/clojars/v/clj-clapps.svg)](https://clojars.org/clj-clapps)

A Clojure library designed to make building command line apps simple and elegant.

Inspired by Python **Typer library** and Rust's own CLI library, and built on top of Clojure's own [tools.cli](https://github.com/clojure/tools.cli) library


```clj
[clj-clapps "0.4.0"]
```

## Usage

Add **clj-clapps** as a dependency:

Lein:

```clojure
;; ...
[clj-clapps "0.4.0"]
;; ...
```

Deps:

```clojure
;; ...
clj-clapps {:mvn/version "0.4.0"}
;; ...

```


```clojure

(ns my-cool-cli
  (:gen-class)
  (:require [clj-clapps.core :as cl]))
    
;; define your command function
(cl/defcmd main-cmd
  "My cool command help description"
  [^{:doc "Argument 1 of cool command" } arg1
   ^{:doc "Option 1 of cool command" :short "-o" } opt1]
  ;; do something with arg1 and opt1 
  )

;; execute your command
(defn -main [& args]
  (cl/exec! 'my-cool-cli args))
```

Clojure metadata is turned into command line options!

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


## License

Copyright © 2018 clj-clapps

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
