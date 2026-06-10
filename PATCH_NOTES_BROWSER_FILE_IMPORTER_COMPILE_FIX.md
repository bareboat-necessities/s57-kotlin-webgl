# BrowserS57FileImporter Kotlin/JS compile fix

This incremental patch fixes the Kotlin/JS compile failure introduced by the
non-rendering-objects patch.

## Problem

Kotlin/JS does not expose `Int8Array` element assignment/access through the
same operator syntax on this toolchain. The previous patch used:

```kotlin
view[index] = this[index]
bytes[index] = this[index]
```

which fails in `:s57-render-webgl:compileKotlinJs`.

## Fix

`BrowserS57FileImporter.kt` now copies through the typed array's dynamic JS
view:

```kotlin
val dynamicView = view.asDynamic()
dynamicView[index] = this[index].toInt()
```

and converts reads back to Kotlin `Byte` explicitly.

## Apply order

Apply this zip after `s57-nonrendering-objects-incremental-fix.zip`.
