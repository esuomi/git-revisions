# git-revisions

[![Deploy to Clojars](https://github.com/esuomi/git-revisions/actions/workflows/deploy.yaml/badge.svg)](https://github.com/esuomi/git-revisions/actions/workflows/deploy.yaml)
[![Clojars Project](https://img.shields.io/clojars/v/fi.polycode/git-revisions.svg)](https://clojars.org/fi.polycode/git-revisions)
[![cljdoc badge](https://cljdoc.org/badge/fi.polycode/git-revisions)](https://cljdoc.org/jump/release/fi.polycode/git-revisions)

Generate software revision strings based on Git and system context data.

## Quick Start

> **This repository describes the use of git-revisions core!**

What you are probably really looking for is a build tool specific library. For one, see

 - [`git-revisions-lein`](https://github.com/esuomi/git-revisions-lein) for [Leiningen](https://leiningen.org/) plugin
 - [`git-revisions-buildtools`](https://github.com/esuomi/git-revisions-buildtools) for [clojure.tools.build](https://github.com/clojure/tools.build) task

Reading core documentation will be helpful, tool specific documentation exists in the library repositories.

## Table of Contents

- [Glossary](#glossary)
- [Usage](#usage)
    * [Prerequisites](#prerequisites)
        + [Project work directory is a Git repository](#project-work-directory-is-a-git-repository)
        + [Initial revision tag must already exist in repository](#initial-revision-tag-must-already-exist-in-repository)
    * [Resolution logic](#resolution-logic)
    * [Available lookups](#available-lookups)
    * [Configuration](#configuration)
        + [Format (`:format`)](#format----format--)
            - [Tag Pattern (`:tag-pattern`)](#tag-pattern----tag-pattern--)
            - [Revision pattern (`:pattern`)](#revision-pattern----pattern--)
            - [Revision adjustments (`:adjustments`)](#revision-adjustments----adjustments--)
            - [Common constants (`:constants`)](#common-constants----constants--)
        + [Adjust selector (`:adjust`)](#adjust-selector----adjust--)
        + [Revision metadata output file (`:revision-file`)](#revision-metadata-output-file----revision-file--)
- [Motivation, prior art and differences](#motivation--prior-art-and-differences)
- [Acknowledgements](#acknowledgements)
- [License](#license)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

## Glossary

- **lookup** Function which can resolve a value based on given keyword's namespace.
- **revision** Alternate word for _version_ chosen for semantics; this plugin can in theory support any
  revisioning scheme, not just common versioning schemes, so it fits better
- **segment** Each revision string consists of one or more segments. A segment is defined by whether it is required or
  optional and its content is further specified by _parts_.
- **part** is a single token in revision segment, such as MAJOR version number in Semantic Versioning.
- **resolution/generation** The general process of producing the revision string.

## Usage

Basic usage of the core library is
```clojure
(require '[git-revisions.core :as revisions])

(def configuration {})  ; content described below

(defn get-revision
  [configuration]
  (let [{:keys [format adjust revision-file]} configuration]
    (revisions/revision-generator format adjust (when (some? revision-file)
                                                  {:output-path  revision-file
                                                   :project-root (:root ".")}))))
```

Exact utilization depends on the used build tool, which is why the helper libraries exist.

### Prerequisites

#### Project work directory is a Git repository

No way around it, at least `git init` must have been run in the directory.

#### Initial revision tag must already exist in repository

If no tags exist yet, add an all-zeroes tag such as `v0.0.0` to any commit:
````shell
git tag -a v0.0.0 -m "initial version"
````

If you're not sure, you can check if a suitable tag exists with the command
```shell
git describe --tags --dirty --long
v3.0.1-28-gf2e808057  # this repository has the tag v3.0.1
```

Suitable format for the tag is dependent on [Configuration/Tag Pattern](#tag-pattern-tag-pattern).

### Resolution logic

There's roughly three phases in the plugin's resolution process:

1. **Lookup generation and gathering.** The tool starts by creating lookup sources which can then be referenced in
   the configuration.
2. **Revision string resolution.** The segments and parts of the revision are resolved using the lookups and some
   limited crossreferencing to produce the final revision string.
3. **Emit metadata.** Return next revision string, output metadata file if configured.

### Available lookups

All lookups are referenced as namespaced keywords, wherein the namespace defines the kind of lookup to be done. For
example `:env/user` looks up the value of environment variable `USER`.

More in-depth documentation for lookups is available in [Available Lookups](docs/lookups.md).

### Configuration

Top-level configuration is as follows:
```clojure
{:format        ...  ; required
 :adjust        ...  ; optional
 :revision-file ...  ; optional
 }
```

#### Format (`:format`)

Use either a predefined built-in pattern:
```clojure
{:format :semver}
```
or define your own:
```clojure
{:format {:tag-pattern ...
          :pattern     ...
          :adjustments ...
          :constants   ...}}
```

- `:semver` is built-in configuration set for the [Semantic Versioning](https://semver.org/) scheme.
- `:commit-hash` is built-in configuration for using Git commit hash as-is as the version string.

You can either use a built-in pattern or make your own. Built-ins are heavily opinionated, so if you have a bit of time,
it is highly recommended for you to define your own.

The built-ins are documented separately in
[Built-in formats](docs/built-ins.md), instructions for defining your own are below.

> More built-in formats as contributions are more than welcome!

##### Tag Pattern (`:tag-pattern`)

Regular expression with named groups. Used as lookup source for `:rev/*` keys.

```clojure
; use any matching Git tag as-is as direct revision pattern
{:format {:tag-pattern #"(?<everything>.+)$"
          :pattern     [:segment/always [:rev/everything]]}}
```

##### Revision pattern (`:pattern`)

Vector of segments expressed as ordered pairing of segment directives and segment parts for the pattern.

- `:segment/always` is always included in the revision string
- `:segment/when` inspects the first value of the vector and based on its truthiness includes the following parts in
  the resulting revision string
- `:segment/when-not` complement of `:segment/when`
- Strings are always included as-is

```clojure
{:format {:pattern [:segment/when     [:env/kenobi "Hello there."]
                    :segment/when     [:env/grievous " General Kenobi!"]]}}
; When environment variable KENOBI is set, produces "Hello there."
; When environment variable GRIEVOUS is set, produces "General Kenobi!"
; When neither are set, an empty string is produced.
; When both are set, both are included as is; "Hello there. General Kenobi!"
```
or more practical
```clojure
; only CI can build non-snapshots, optionally including pre-release tag
{:format {:pattern [:segment/when-not [:env/ci "-SNAPSHOT"]
                    :segment/when     [:env/mylibrary_prerelease "-" :env/mylibrary_prerelease]]}}
```

##### Revision adjustments (`:adjustments`)

Map of labeled adjustments to be executed conditionally during resolution based on lookup context.

```clojure
{:format {:tag-pattern #"(?<numbers>\d.+)$"
          :pattern     [:segment/always [:rev/numbers]]
          :adjustments {:bump {:rev/numbers :inc}}}
 :adjust [:env/project_next_revision :bump]}
```

The [adjust selector](#adjust-selector-adjust) controls the revision adjustment done during resolving. In the example
above the `:adjust` selector is set to look up the value first from environment variable `PROJECT_NEXT_VERSION` and if
such isn't present, use the `:bump` adjustment instead.

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

##### Common constants (`:constants`)

Define constants to be used for revision string resolution.

```clojure
{:format {:pattern   [:segment/always [:constants/coca-cola]]
          :constants {:coca-cola "Pepsi"}}}
; resulting revision is always "Pepsi"
```

#### Adjust selector (`:adjust`)

Control the modification of the revision string based on external information, such as environment variable set by
Continuous Integration. Should be set so that last value is some known adjustment to ensure the revision automatically
rolls onwards. Without `:adjust` the revision will be stuck in whatever tag was previously defined defeating the purpose
of the tool.

```clojure
{:format {:tag-pattern #"v(?<numbers>\d.+)$"
          :pattern     [:segment/always "v" [:rev/numbers]]
          :adjustments {:bump {:rev/numbers :inc}}}
 :adjust [:bump]}
; For tag v27 produces revision v28
```

#### Revision metadata output file (`:revision-file`)

Capture plugin's metadata into a file and use it as static source for metadata API endpoint, or with another plugin, in
template substitutions or whatever else you come up with.

```clojure
{:revision-file "resources/metadata.edn"
 ...}
```

Git metadata and the produced version string are written to the file.

> The given path must resolve as absolute path within the current project root.

## Motivation, prior art and differences

Using Git tags to control project version isn't a new problem and by no means an unsolved issue, in fact there are
alternative libraries already available which solve this same issue but in a different way:

- [arrdem/lein-git-version](https://github.com/arrdem/lein-git-version/)
- [day8/lein-git-inject](https://github.com/day8/lein-git-inject)
- [devth/lein-inferv](https://github.com/devth/lein-inferv)

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

