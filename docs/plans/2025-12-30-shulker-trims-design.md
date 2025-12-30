# Shulker Trims - Design Document

## Overview

A Minecraft mod that allows shulker boxes to be trimmed using the smithing table, similar to armor trims. Applies decorative patterns to shulker boxes using existing armor trim templates and materials.

**Target version:** Minecraft 1.21.0+

## Platforms

| Platform | Type | Priority |
|----------|------|----------|
| Fabric | Mod (client + server) | Primary |
| Bukkit/Spigot/Paper | Server plugin | Primary |
| NeoForge | Mod | Future (via Architectury if needed) |

## Project Structure

```
shulker-trims/
├── fabric/                    # Fabric mod (client + server)
│   ├── src/main/java/com/wlritchi/shulkertrims/
│   ├── src/main/resources/
│   └── build.gradle.kts
├── bukkit/                    # Bukkit/Spigot/Paper plugin
│   ├── src/main/java/com/wlritchi/shulkertrims/
│   ├── src/main/resources/
│   └── build.gradle.kts
├── common/                    # Shared logic (data models, validation)
│   └── src/main/java/com/wlritchi/shulkertrims/common/
├── build.gradle.kts           # Root build config
├── settings.gradle.kts
└── gradle.properties
```

- `common/` - Platform-agnostic code: trim data models, validation, material mappings
- `fabric/` - Fabric-specific: registries, rendering, recipes, components
- `bukkit/` - Bukkit-specific: event handlers, recipe registration, NBT manipulation

## Recipe

Uses vanilla smithing table mechanics:

| Slot | Input |
|------|-------|
| Template | Any armor trim template (16 patterns) |
| Base | Any shulker box (17 color variants) |
| Addition | Any trim material (10 materials) |

**Output:** Same shulker box with trim applied. All existing item data preserved (contents, custom name, lore, etc.).

**Re-trimming:** Applying a new trim to an already-trimmed shulker replaces the existing trim.

## Data Storage

**Storage location:** Block entity NBT with tag `shulker_trims:trim`

```nbt
{
  shulker_trims:trim: {
    pattern: "minecraft:sentry",
    material: "minecraft:copper"
  }
}
```

**Why block entity NBT:**
- Vanilla servers preserve unknown block entity tags through break/place cycles via `minecraft:block_entity_data` component
- Enables trimmed shulkers to survive on vanilla servers without mod
- Both Fabric and Bukkit can read/write this location

**Platform implementation:**
- Fabric: Read/write block entity NBT, expose as component for recipe handling
- Bukkit: Read/write same NBT path directly

## Rendering

**Contexts:**
1. Block entity - Placed shulker box in world
2. Item - Inventory icons and held items

**Approach:** Runtime palette swap (matches vanilla armor trim rendering)
- Ship 16 grayscale pattern textures
- Colorize at runtime based on trim material
- Reduces texture count vs pre-baked combinations

**Texture layout:** Match vanilla shulker box UV layout for easy overlay alignment

**Coverage:** All 10 exterior faces (5 body + 5 lid), lid follows open/close animation

**Debug textures (v1):** Solid grayscale with simple pattern indicators, tinted by material color

## Color Support

Full matrix support:
- 17 shulker box colors (undyed + 16 dyes)
- 16 trim patterns
- 10 trim materials

Trim color is independent of shulker box color (matches vanilla armor trim behavior).

## Vanilla Compatibility

**Clients without mod:**
- See normal shulker boxes (trim data ignored)
- Can interact with trimmed shulkers normally (open, move, place, break)
- No crashes or errors

**Servers without mod:**
- Preserve trim data in NBT through break/place cycles
- Cannot create new trimmed shulkers (no recipes)
- Existing trims remain in data, render on modded clients

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Dispenser places trimmed shulker | Trim preserved |
| Piston pushes trimmed shulker | Trim preserved |
| Explosion breaks trimmed shulker | Dropped item has trim |
| Hopper moves trimmed shulker | Trim preserved |
| Invalid/missing trim data | Render as normal shulker |
| Unknown pattern/material ID | Log warning, render without trim |
| World transfer Fabric↔Bukkit | Trim preserved (shared NBT location) |

## Testing Strategy

**Unit tests:** Where straightforward
- Component/NBT serialization and deserialization
- Material color palette lookups
- Data validation logic

**Manual testing:** In-game scenarios
- Recipe crafting with all combinations
- Place/break cycles
- Dispenser, piston, explosion interactions
- Cross-platform world transfers
- Vanilla client/server compatibility

**Automated E2E tests:** Stretch goal for future

## Out of Scope (v1)

- Shulker mob trim support
- NeoForge support
- Debug commands
- F3 debug overlay integration

## Assets

**Pattern textures:**
```
assets/shulker_trims/textures/trims/patterns/
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
└── spire.png
```

**Material colors:** Defined in code, matching vanilla armor trim palette
- Copper: `#B4684D`
- Gold: `#DEB12D`
- Iron: `#ECECEC`
- (etc.)
