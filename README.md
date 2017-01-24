# lyceum
[![Build Status](https://travis-ci.org/spotify/lyceum.svg)](https://travis-ci.org/spotify/lyceum)
[![Clojars Project](http://clojars.org/lyceum/latest-version.svg)](http://clojars.org/lyceum)

A Clojure library designed to help in the authoring and testing of modules
riemann rules.

Each rule lives and is distributed within a different namespace.
Lyceum will take care to iterate the classpath and look for namespaces
containing rules.

Each namespace can be put under test using a test fixture provided by lyceum
that takes care to setup and tear down all necessary state to simulate riemann
operation.

Tests are run with little overhead, it is not required to start a riemann
core, instead lyceum takes the approach of completely _simulating_ riemann
operation while imposing a little bit of structure on the way you write rules
to make things manageable.

Some key points.

* When running tests, all external interaction is faked and verifiable (using
  `check-externals`).
* The riemann schedules is replaced with a global override of
  `riemann.time/schedule!` that has a thread-local implementation without global
  state.
  With this comes also faked time (`riemann.time/unix-time` et. al.).
* Provides a [restful HTTP service](#http-service) for evaluating rules on-the-fly.

# Getting Started

Start by initializing an empty leiningen project;

```
#> lein new my-rules
#> cd my-rules
#> rm src/my_rules/core.clj
#> rm test/my_rules/core_test.clj
```

Add a testing scope dependency to lyceum in your project.clj

```clojure
(defproject my-rules "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 ; add both as a test dependency.
                 [lyceum "0.1.0" :scope "test"]]
  ; ... and as a plugin.
  :plugins [[lyceum "0.1.0"]]
  ; define which namespace lyceum should initialize new rules in.
  :lyceum-namespace my-rules.rules)
```

Download templates and generate a new namespace skeleton.

```
#> mkdir templates
#> wget https://raw.githubusercontent.com/spotify/lyceum/master/templates/rule.clj -O templates/rule.clj
#> wget https://raw.githubusercontent.com/spotify/lyceum/master/templates/test.clj -O templates/test.clj
```

Now it's time to initialize rules for a group called __my-group__.

```
#> lein lyceum init my-group
```

Almost there, verify that your rules are working by running your skeleton
test-cases!

```
#> lein test
```

Now you can start modifying *src/my\_rules/rules/my\_group.clj* to suit your
needs.

Any test-cases you come up with should be added to
*test/my\_rules/rules/my_group_test.clj*, use the generated one as inspiration
for writing more.

When you are done, compile your rules.

```
#> lein uberjar
```

Add lyceum and the rules jar containing to the classpath of riemann and
add the following declaration in your riemann.config.

```clojure
; load-plugins has to be present!
(load-plugins)

(streams
  ; load any namespaces containing rules under 'my-rules.rules'.
  ; blacklist any tutorial namespaces to avoid loading them.
  (lyceum/load-rules
    'my-rules.rules
    :opts {:index index}
    :blacklist [
      #"my-rules.rules.tutorial\d+"
    ]))
```

Make sure that you start the riemann service with the system property
`-Dlyceum.mode=real` (pass this as an argument to _java_ like `java
-Dlyceum.mode=real ... riemann.bin`).
See the [externals section](#externals) for more details about this.

# Time

Time is controlled in a similar fashion to how `riemann.controlled.time` works,
but it's done with a separate implementation in lyceum.

Any input event is inspected for its `:time` field, and if present, the time
specified is used as the current time for the simulation.

This can bee seen in the example test-case that you generated if you followed
the guide above.

# Externals

Lyceum uses __externals__ to interact with external systems, externals are thin
wrappers around the riemann's external integrations (email, pagerduty, etc.).

By default lyceum uses the __fake__ mode which will cause any external
interactions to be logged instead of realized.
This is what allows the test-cases to verify external effects with
`check-externals`.

However when running in production, the mode is set to __real__. This will
cause the wrapping to be discarded and the real external interaction to be
realized.

The mode is set with the `-Dlyceum.mode=<mode>` system property that should
be passed to your JVM.

Valid modes are.

* __fake (default)__ - Log any externals that are triggered to an internal
  data-structure, allowing for later verification.
* __test__ - Write external interaction to a log file.
* __real__ - Realize any externals that are triggered.

Wrapping your own external is straight forward, you can use
[the email external](src/lyceum/external/email.clj) as an example for how to do
this.

# HTTP Service

You can start the HTTP service by running.

```
#> lein run [lyceum.conf]
```

Or the ___lyceum.service___ class in the resulting uberjar.

```
#> java -jar <path-to-jar> lyceum.service [lyceum.conf]
```

It expects to find a [lyceum.conf](lyceum.conf) in the current working directory, or one can be provided as an argument.

The service is currently capable of loading rules the following ways.

+ Github through [github-rules](src/lyceum/service/rules_loader/github.clj)
+ Filesystem through [directory-rules](src/lyceum/service/rules_loader/directory.clj)

#### POST /eval

Will evaluate the received data (___VERY UNSAFE___) and apply the provided rules to them.

+ Response 200 (application/json)
+ Response 500 (application/json)

###### Request Structure
```javascript
{"data": <string>, "events": [<event>, ..]}
```

###### Response Structure
```javascript
{/* contains any external events (like pagerduty) that happened */
 "reports":[<report>, ..],
 /* contains the events which was indexed during this evaluation. */
 "index":[<event>, ..]
}
```

###### Example CURL
```
#> curl http://localhost:8080/eval -H "Content-Type: application/json"
          -d '{"data": "(ns hello.world) (defn rules [{:keys [index]}] (fn [e] (index e)))", "events": [{"service": "foo"}]}'
```

#### GET /ns/

Will request a list of namespaces, see [lyceum.conf](lyceum.conf) for how this is configured.

#### GET /ns/{ns}

Will request the content of a specific namespace ___{ns}___, see [lyceum.conf](lyceum.conf) for how this is configured.
