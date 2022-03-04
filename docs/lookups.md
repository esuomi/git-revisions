# Available Lookups

> See also project tests for examples

## Environment variables (`:env/*`)

Environment variable lookup. Key is uppercased but otherwise untouched; remember to use underscores as word
delimiter.

Available values are dependent on what the JVM process sees and cannot be altered during runtime.

```clojure
{:format {:pattern [:segment/always [:env/lein_username]]}}  ; will lookup environment variable LEIN_USERNAME
;=> "esuomi"
```

## Revision metadata (`:rev/*`)

`:rev/*` Lookup a value from [Tag pattern](../README.md#tag-pattern-tag-pattern) based on named capture group.

This lookup is dynamic and entirely dependent on the provided regular expression. There's no caching and values in the
pattern are not guaranteed to be extracted if not used.

```clojure
{:format {:tag-pattern #"(?<suffix>.{,3}).+$"
          :pattern [:segment/always [:rev/suffix]]}}  ; up to three first characters of matching Git tag
;=> "v3." (for tag v3.1.0)
```

## Git metadata (`:git/*`)

Values matching the current Git state, such as previous tag, versioning status, ahead/dirty...

Values ending in question mark are meant to be used like predicates and are guaranteed to be booleans.

Always available:

 - `:git/unversioned?` is version control enabled?
 - `:git/untagged?` does a suitable - if any - Git tag exist? Usually this means no tags available, see [Prerequisites](../README.md#0-prerequisites)

Conditionally available:
 -
 - `:git/ref` Current commit's reference hash, implementation specific on used Git version and configuration, most likely SHA-1
 - `:git/ref-short` Short hash of the current commit
 - `:git/tag` Latest known visible tag which matches the [Tag pattern](../README.md#tag-pattern-tag-pattern)
 - `:git/tag-ref` Reference has the tag points to
 - `:git/dirty?` Working tree has local modifications
 - `:git/ahead?` Is current HEAD ahead of its upstream
 - `:git/ahead` How many commits ahead the current HEAD is
 - `:git/branch` Name of current branch

## Common Constants (`:constants/*`)

Mechanism for piggybacking constant values from build to the revision resolution process. See [Common constants](#common-constants-constants)

## Calendar versioning schemes (`:calver/*`)

Support for looking up [Calendar Versioning](https://calver.org/) schemes. Note that due to Clojure keyword limitations
  the zero-padding patterns are flipped, so `0M` is `:calver/m0` and so on.

As CalVer is not a stabilized specification but a just a set of common schemes, there's a near-infinite amount of
combinations that could be used with tag patterns and outputs.

## Datetime parts (`:dt/*`)

Common datetime parts, from current year to current second. All in singular form.

 - `:dt/year`
 - `:dt/month`
 - `:dt/day`
 - `:dt/hour`
 - `:dt/minute`
 - `:dt/second`
