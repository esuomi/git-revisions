# lein-git-revisions

Automatically control Leiningen project version based on Git metadata.

## Quick Start

We assume you'll want to follow [Semantic Versioning](https://semver.org/).

## Prerequisites

### Tag matching to expected tag pattern must exist in repository in advance

If no tags exist yet, we recommend adding an all-zeroes tag such as `v0.0.0` to any commit:
````shell
git tag -a v0.0.0 -m "initial version"
````

If you're not sure, you can check if a suitable tag exists with the command
```shell
git describe --tags --dirty --long
v3.0.1-28-gf2e808057  # this repository has the tag v3.0.1
```

Suitable format for the tag is dependent on [Configuration/Tag Pattern](#tag-pattern-tag-pattern).

## Quick Start

Simply add the plugin to your `:plugins` vector:
```clojure
:plugins [[fi.polycode/lein-git-revisions "LATEST"]
          ...]
```

and default configuration
```clojure
:git-revisions {:format :semver
                :adjust [:env/project_revision_adjustment :minor]}
```

This automatically registers the plugin middleware which handles the version string generation and applies the Semantic
Versioning scheme.

Additionally, you may want to set project version to a dummy string to highlight the fact it's controlled elsewhere:
```clojure
(defproject foo "_"
 ...)
```

> The version must be a string for IDE compatability. For example Cursive makes assumptions based on the version always
> being a string.

## Glossary

 - **lookup** Is a function which can resolve a value based on given keyword's namespace.
 - **revision** Is an alternate word for _version_ chosen for semantics; this plugin can in theory support any
   revisioning scheme, not just common versioning schemes so it fits better
 - **segment** Each revision string consists of one or more segments. A segment is defined by whether it is required or
   optional and its content is further specified by _parts_.
 - **part** is a single token in revision segment, eg. MAJOR version number in Semantic Versioning.
 - **resolution/generation** The general process of producing the version string.

## Execution logic explained

There's roughly three phases in the plugin's execution:

 1. **Lookup generation and gathering.** The plugin starts by creating lookup sources which can then be referenced in
    the configuration.
 2. **Revision string resolution.** The segments and parts of the revision are resolved using the lookups and some
    limited crossreferencing to produce the final revision string.
 3. **Emit metadata.** Change project version, output metadata file.

### Available lookups

All lookups are referenced as namespaced keywords, wherein the namespace defines the kind of lookup to be done. For
example `:env/user` looks up the value of environment variable `USER`.

 - `:env/*` Environment variable lookup. Key is uppercased but otherwise untouched; remember to use underscores as word
   delimiter
 - `:rev/*` Lookup a value from [Tag pattern](#tag-pattern-tag-pattern) based on named capture group
 - `:git/*` Properties matching the current Git state, such as previous tag, versioning status, ahead/dirty...
 - `:constants/*` See [Common constants](#common-constants-constants)
 - `:gen/*` Miscellanous values generated during runtime

## Configuration

Top-level configuration is as follows:
```clojure
:git-revisions {:format        ...  ; required
                :adjust        ...  ; optional
                :revision-file ...  ; optional
                }
```


### Format (`:format`)

Use either a predefined built-in pattern:
```clojure
:git-revisions {:format :semver}
```
or define your own:
```clojure
:git-revisions {:format {:tag-pattern ...
                         :pattern     ...
                         :adjustments ...
                         :constants   ...}}
```

 - `:semver` is built-in configuration set for the [Semantic Versioning](https://semver.org/) scheme.

#### Tag Pattern (`:tag-pattern`)

Regular expression with named groups. Used as lookup source for `:rev/*` keys.

```clojure
; use any matching Git tag as-is as direct revision pattern
{:format {:tag-pattern #"(?<everything>.+)$"
          :pattern     [:segment/always [:rev/everything]]}}
```

#### Revision pattern (`:pattern`)

Vector of segments expressed as implicit, ordered pairing of segment directives and segment parts for the pattern.

 - `:segment/always` is always included in the revision string
 - `:segment/when` inspects the first value of the vector and based on its truthiness includes the following parts in
   the resulting revision string
 - Strings are always included as-is

```clojure
{:format {:pattern [:segment/when [:env/kenobi "Hello there."]
                    :segment/when [:env/grievous "General Kenobi!"]]}}
; When environment variable KENOBI is set, produces "Hello there."
; When environment variable GRIEVOUS is set, produces "General Kenobi!"
; When neither are set, an empty string is produced.
; When both are set, both are included as is; "Hello there.General Kenobi!"
```

#### Revision adjustments (`:adjustments`)

Map of labeled adjustments to be executed conditionally during resolution based on lookup context.

```clojure
{:format {:tag-pattern #"(?<numbers>\d.+)$"
          :pattern     [:segment/always [:rev/numbers]]
          :adjustments {:bump {:rev/numbers :inc}}}
 :adjust [:env/project_next_revision :bump]}
```

The [adjust selector](#adjust-selector-adjust) drives the selection of an adjustment to be applied to the revision
string during resolving. In the example above the `:adjust` selector is set to look up the value first from environment
variable `PROJECT_NEXT_VERSION` and if such isn't present, use the `:bump` adjustment instead.

All parts referenced in the [revision pattern](#revision-pattern-pattern) can be adjusted.
For example [Semantic Versioning Specification item 8](https://semver.org/#spec-item-8) declares that when the MAJOR
part is incremented, MINOR and PATCH parts must be reset to zero. This can be expressed - and in fact is in the built-in
configuration with
```clojure
{:format {...
          :adjustments {:major {:rev/major :inc :rev/minor :clear :rev/patch :clear}}
          ...}}
```

Available operations for adjustments are

 - `:inc` Increment numeric part by one
 - `:clear` Always use `0` as value

#### Common constants (`:constants`)

Define constants to be used for revision string resolution.

```clojure
{:format {:pattern   [:segment/always [:constants/coca-cola]]
          :constants {:coca-cola "Pepsi"}}}
; resulting revision is always "Pepsi"
```

### Adjust selector (`:adjust`)

Control the modification of the revision string based on external information, such as environment variable set by
Continuous Integration. Should be set so that last value is some known adjustment to ensure the revision automatically
rolls onwards. Without `:adjust` the revision will be stuck in whatever tag was previously defined defeating the purpose
of the plugin.

```clojure
{:format {:tag-pattern #"v(?<numbers>\d.+)$"
          :pattern     [:segment/always "v" [:rev/numbers]]
          :adjustments {:bump {:rev/numbers :inc}}}
 :adjust [:bump]}
; For tag v27 produces revision v28
```

## Motivation, prior art and differences

Using Git tags to control project version isn't a new problem and by no means an unsolved issue, in fact there are
alternative libraries already available which solve this same issue but in a different way:

- [arrdem/lein-git-version](https://github.com/arrdem/lein-git-version/)
- [day8/lein-git-inject](https://github.com/day8/lein-git-inject)

The main two differences compared to the ones above are

1. Entirely data/configuration driven, admittedly overengineered, as probably is obvious if you made it this far
2. Zero interaction after initial Git tagging

Additional motivator in creating our own was including the ability to alter specific parts of the revision string in
automatic, externally configured fashion.

## Acknowledgements

All the contributors to the other plugins, in no specific order, non-exhaustively:

 - [Reid "arrdem" McKenzie](https://github.com/arrdem)
 - [Mike Thompson](https://github.com/mike-thompson-day8)
 - [Colin Steele](https://github.com/cvillecsteele)
 - [Michał Marczyk](https://github.com/michalmarczyk)

## License

Copyright © 2022 Esko Suomi

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

