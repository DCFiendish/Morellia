# Entities, Items & Blocks

The basic gameplay primitives other libraries (Aechronis's `combat` guns, `nodes`' territory blocks, `vanilla`'s reimplemented mechanics) are built from.

## Entities

Instantiate `new Entity(EntityType.X)` (or a `LivingEntity` subclass), then `entity.setInstance(instance, pos)`. `EntityMeta` drives client-visible state. `setAutoViewable(true)` (the default) handles visibility tracking automatically.

Source: [Entities docs](https://minestom.net/docs/feature/entities).

**Relevant to already-researched systems**: `nodes`' minimap ([../RESEARCH.md §15](../RESEARCH.md)) uses a **packet-only** text-display entity attached as a virtual passenger — meaning it deliberately bypasses this normal entity-instance model (no real `setInstance` call, no auto-viewability tracking) to avoid the overhead of a real tracked entity per player. That's an advanced, deliberate departure from the standard entity API, not the typical way to spawn something — worth keeping in mind as a pattern (packet-only virtual entities) rather than assuming all Aechronis entities work through the normal API.

## Items — the DataComponents model

`ItemStack` uses a modern **DataComponents** model (matching 1.20.5+ vanilla), not legacy NBT-only. Immutable component records like `CUSTOM_NAME`, `LORE`, `ATTRIBUTE_MODIFIERS`, `FOOD` are accessed/mutated via `get`/`with`/`without`, or via `ItemStack.Builder#set`/`remove`.

Source: [Items docs](https://minestom.net/docs/feature/items), [DataComponents javadoc](https://javadoc.minestom.net/net.minestom.server/net/minestom/server/component/DataComponents.html).

**Directly relevant to already-planned mechanics**:
- `combat`'s gun ammo tracking deliberately uses `DataComponents.DAMAGE` rather than an NBT tag specifically to avoid triggering item-swap animations on fire/reload (per [../RESEARCH.md §2](../RESEARCH.md)) — this is a concrete example of working *with* the component model rather than against it.
- The Town Overview GUI and WaypointMenu's `namedItem()` helper ([../RESEARCH.md §15](../RESEARCH.md)) build display items via `CUSTOM_NAME`/lore components directly — the standard, idiomatic way to build menu items in this model.

## Blocks

`instance.setBlock(x, y, z, Block.X)` for direct placement. Custom behavior attaches via `BlockHandler` (e.g. `onInteract`), registered through `BlockManager` with a namespace key + supplier. Batch edits use `Batch` (`setBlockStateId`/`setCustomBlock`, applied via `Batch#apply`) — the efficient way to place many blocks at once rather than looping individual `setBlock` calls.

Source: [Blocks docs](https://minestom.net/docs/world/blocks), [Batch docs](https://minestom.net/docs/world/batch).

**Relevant to the mining/ore mechanic** ([../RESEARCH.md §11](../RESEARCH.md)): `nodes`' hidden-ore system operates on ordinary blocks (configured via `Nodes.config.oreBlocks`, e.g. STONE) via the standard `PlayerBlockBreakEvent`/`setBlock` path — no custom `BlockHandler` needed for the ore-sampling mechanic itself, since the "ore" is a drop calculated on break, not a distinct block type. `Batch` would matter more for actual world-building/terrain work (e.g. if the eventual custom map, per the deferred map-design discussion, needs large-scale procedural touch-ups) than for the mining economy mechanic itself.
