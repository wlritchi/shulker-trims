# BBModel Asset Extraction Design

## Summary

Extract shulker trim pattern PNGs from a BlockBench model file at build time instead of committing them directly to the repository.

## Motivation

- Single source of truth: artists edit the bbmodel, build generates assets
- Cleaner git history: binary PNG changes don't pollute commits
- Freedom for artists to add reference/markup layers without affecting the build

## File Layout

**Source file:**
```
art/
└── shulker-trims.bbmodel
```

**Generated output:**
```
fabric/build/generated/resources/assets/shulker_trims/textures/trims/entity/shulker/
├── sentry.png
├── vex.png
├── wild.png
├── coast.png
├── dune.png
├── wayfinder.png
├── raiser.png
├── shaper.png
├── host.png
├── ward.png
├── silence.png
├── tide.png
├── snout.png
├── rib.png
├── eye.png
├── spire.png
├── flow.png
└── bolt.png
```

## Build Flow

1. Gradle task `extractShulkerTrims` parses the bbmodel JSON
2. For each hardcoded pattern name, finds the matching layer and decodes its base64 PNG data
3. Writes PNGs to the generated resources directory
4. Task runs before `processResources` in the `:fabric` module
5. Generated resources directory is added to fabric's resource sourceSets

## Gradle Task Implementation

**Location:** `fabric/build.gradle`

**Task class:**
- Input: `art/shulker-trims.bbmodel` (declared as `@InputFile`)
- Output: generated resources directory (declared as `@OutputDirectory`)
- Hardcoded list of 18 trim pattern names
- Uses JDK standard library only (JSON parsing, Base64 decoding)

**Task wiring:**
- `:fabric:extractShulkerTrims` runs before `:fabric:processResources`
- Output directory added to `sourceSets.main.resources.srcDirs`

## Error Handling

All errors fail the build with clear messages:
- bbmodel file doesn't exist
- bbmodel isn't valid JSON
- No textures array or empty
- Layer missing for a hardcoded pattern name
- Invalid base64 data in a layer

## Incremental Build Support

- Task declares proper inputs/outputs for Gradle up-to-date checking
- Skipped automatically if bbmodel unchanged and outputs exist
- Clean build removes generated directory, forcing regeneration

## Implementation Steps

1. Create `art/` directory and move bbmodel file (renamed to `shulker-trims.bbmodel`)
2. Add `ExtractShulkerTrims` task to `fabric/build.gradle`
3. Wire task to run before `processResources`
4. Add generated resources to sourceSets
5. Remove committed trim pattern PNGs from git
6. Test build and verify mod works
