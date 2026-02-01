# Gallery Image Generator Design

## Overview

Generate a promotional/demo image showing trimmed shulker boxes in a cozy storage room setting, rendered from regular first-person Minecraft perspective.

**Goal**: A photogenic, representative sample of the mod's features — not an exhaustive showcase, but enough to demonstrate the variety of trim patterns and materials on different shulker colors.

## Infrastructure

Reuse existing `icongen` source set and Fabric Client GameTest framework. OrthoCamera remains installed but disabled for this generator.

### New Files

- `GalleryGenerator.java` — main generator class implementing `FabricClientGameTest`
- `GalleryConfig.java` — minimal config parsing

### Configuration

Via system properties (minimal — scene is curated, not parameterized):

| Property | Default | Description |
|----------|---------|-------------|
| `shulker_trims.gallery.output` | `build/gallery/gallery.png` | Output file path |
| `shulker_trims.gallery.width` | `1920` | Output width in pixels |
| `shulker_trims.gallery.height` | `1080` | Output height in pixels |

### Gradle Task

New task `generateGallery` following the same pattern as `generateIcon`.

## Scene Design

### Environment: Storage Room

A cozy player storage area with warm lighting.

**Dimensions**: ~5×4×3 blocks interior (width × depth × height)

**Materials**:
- Floor: Oak planks
- Walls: Mix of spruce planks and stripped logs for texture
- Shelving: Spruce slabs/stairs as shelf surfaces
- Lighting: 2-3 lanterns (warm, cozy)

**Props**:
- 1-2 barrels
- A chest
- Possibly item frames (future consideration)

### Shulker Box Selection

6 boxes chosen for visual variety and contrast:

| # | Shulker Color | Trim Pattern | Trim Material | Notes |
|---|---------------|--------------|---------------|-------|
| 1 | Purple | Sentry | Gold | Classic combo, high contrast |
| 2 | Cyan | Wild | Copper | Warm trim on cool box |
| 3 | White | Dune | Netherite | Dark trim on light box |
| 4 | Orange | Coast | Diamond | Blue accent on warm box |
| 5 | Light Gray | Vex | Amethyst | Subtle, sophisticated |
| 6 | Black | Rib | Gold | Rib is darker in palette |

This selection may be adjusted after seeing initial renders.

### Shulker Placement

- **Arrangement**: Deliberately haphazard/lived-in, not a perfect grid
- **Heights**: Mix of floor-level and on shelves
- **Orientations**: Different facing directions; some sideways (`facing=north/south/east/west`) to show top face
- **One open**: Position player camera to keep one shulker box open (within 5 blocks, looking at it)

Note: Shulker boxes only have `facing` direction, no horizontal rotation. Most trim patterns are 4-fold rotationally symmetric.

### Camera

- **Perspective**: Regular first-person (no OrthoCamera)
- **Position**: Likely slightly elevated or slightly low angle
- **Composition**: Hardcoded in code, iterated until it looks good

Future consideration: Dutch angle would require camera roll support (mod or custom rendering).

## Implementation Notes

### Scene Building Sequence

1. Create singleplayer world
2. Build room structure at fixed coordinates (e.g., 0, 100, 0)
3. Place shulker boxes with trims
4. Place props (barrels, lanterns, chest)
5. Set time to noon, disable daylight cycle
6. Put player in spectator mode (or survival for open shulker interaction)
7. Position player camera at composed viewpoint
8. Wait for chunks to render
9. Capture screenshot (no chroma key — room is part of the image)

### Visual Assessment

Use fresh subagents for each image assessment to avoid context-related degradation of visual reasoning.

### Iteration

The layout and composition will likely require multiple iterations. The design is intentionally flexible — hardcoded values in code are easier to tweak than over-engineered configuration.

## Future Considerations (not v1)

- Shader support for enhanced lighting/atmosphere
- Dutch angle via camera roll modification
- Multiple gallery presets (different angles, themes)
- Animated GIF showing shulker opening
