# Built-in formats

Built-in patterns are complete, readymade and opinionated sets of configurations which implement a specific revisioning
pattern. They are meant to be quick-to-start patterns

To use any built-in pattern, define plugin configuration's `:format` key as keyword matching to desired built-in:
```clojure
:git-revisions {:format :built-in-format-identifier}
```

## Semantic Versioning (`:semver`)

```clojure
:git-revisions {:format :semver
                :adjust [:env/lein_revisions_adjustment :minor]}
```

Pattern which follows [Semantic Versioning](semver.org/). Most people know this as "Maven pattern", or "Aether pattern"
or just "the three dotted numbers" pattern.

The format supports adjusting the resulting pattern with either `:major`, `:minor` or `:patch` qualifiers.

The format also has fallbacks for unversioned and unreleased projects, producing such revision patterns as
`UNKNOWN-UNVERSIONED` and `UNKNOWN-SNAPSHOT` based on active context.

Git tags are expected to be in format `vX.Y.Z`, where X, Y and Z are all positive integers.

## Commit hash (`:commit-hash`)

```clojure
:git-revisions {:format :commit-hash}
```

Use current commit's full SHA-1 as revision string as-is.

Has a fallback for unversioned projects, in which case the revision string is `UNKNOWN`.
