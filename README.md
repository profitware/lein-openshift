# lein-openshift

[![Clojars Project](https://img.shields.io/clojars/v/lein-openshift.svg)](https://clojars.org/lein-openshift)

A Leiningen plugin to build and deploy your Clojure applications to RedHat OpenShift.

Inspired by https://blog.openshift.com/using-clojure-on-openshift/

## Usage

In `:plugins` in your `project.clj`:

```text
[lein-openshift "0.1.1"]
```

To build uberjar for your application and release it to OpenShift via S2I builder:

```
$ lein openshift release
```

It is possible to run the command two or more times. In that case the steps for
project creation, application creation and build creation are skipped.

The steps for uberjar creation, starting build and service exposure are done each time.

## Configuration

You can add the following configuration options at the root of your `project.clj`:

```clojure
:openshift {:namespace "openshift-namespace"
            :app "application-name"
            :env {"KEY1" "PREDEFINED VALUE OR nil FOR INTERACTIVE INPUT"
                  "KEY2" nil}
            :domains ["example.com"]
            :recreate true}
```

Defaults:

* `:namespace` is your project's name (or group ID if there is one)
* `:app` is your project's name (without group ID)
* `:env` map defines environment variables that would be used for application creation and is empty by default
* `:recreate` is `false` by default and if `true` then patches your deployment configuration to use `Recreate` deployment strategy
* `:domains` is the list of domains to expose sevice to and is empty by default


## Releasing your OpenShift applications

You can use Leiningen to handle your technical release process. In order to do that with your application,
configure your release tasks similar to that:

```clojure
:release-tasks [["vcs" "assert-committed"]
                ["change" "version" "leiningen.release/bump-version" "release"]
                ["vcs" "commit"]
                ["vcs" "tag"]
                ["clean"]
                ["openshift" "release"]
                ["change" "version" "leiningen.release/bump-version"]
                ["vcs" "commit"]
                ["vcs" "push"]]
```

## License

Copyright © 2018 Sergey Sobko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
