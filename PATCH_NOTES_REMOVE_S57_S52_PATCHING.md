# Remove S-57 S-52 WebGL2 patch machinery

This update assumes S-52 `WebGlS52Renderer` already contains the strict WebGL2 `unsafeCast` fix.

## Removed from S-57

- Removed `patchS52WebGl2KotlinJsCast(...)` from `settings.gradle.kts`.
- Removed the patch invocation before `includeBuild(...)`.
- Removed the CI step named `Patch S-52 WebGL2 Kotlin/JS cast`.
- The old helper script should be deleted from the S-57 repository:

```
.github/scripts/patch-s52-052-webgl2-cast.sh
```

The zip cannot delete files by overwrite alone, so `DELETE_FILES_REMOVE_S52_PATCHING.txt` lists the file to remove from git.

## Kept intentionally

- The S-52 source download/composite build stays in CI because the JS build still uses the S-52 source release via `-Ps52SourceDir`.
- The project still targets S-52 `0.5.5` without checksum enforcement.
