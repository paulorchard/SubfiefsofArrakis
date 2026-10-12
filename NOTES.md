# IslandCraft - Subfiefs of Arrakis

Claim a chunk with a statue. In a claimed chunk only the owner can place, break, damage or open things.

Server version checked against: 0.6.8 (`HytaleServer.jar` from the installed game).

## Iteration loop

- `gradlew deployMod` builds the jar and replaces this mod's jar in `%APPDATA%\Hytale\UserData\Mods`.
- `tools/headless/run.sh "wait 20" "subfief list"` boots the real server with no client and types console commands.
  It proves loading, assets, registration, persistence and the console commands. It cannot place blocks, open the
  popup or show titles/particles: those need a client.

## Naming

- Item and block: `Arrakis_Subfief_Claim_Statue`. Asset folders `Subfiefs_of_Arrakis`. Chunk component `Arrakis_Subfief_Claim`.
- Permissions: `islandcraft.subfiefs.bypass` (ignore protection), `islandcraft.subfiefs.give`, `islandcraft.subfiefs.admin` (release, list).
- Data: `<data folder>/Subfiefs_of_Arrakis_Claims.json` (versioned, `Trusted` list reserved), config `Subfiefs_of_Arrakis.json`.

## Engine facts confirmed from the 0.6.8 server jar

- Block events are `EntityEventSystem<EntityStore, Event>`. The acting player is the entity the event was raised on:
  `archetypeChunk.getReferenceTo(index)`, then `PlayerRef.getComponentType()`. A query of `Archetype.of(PlayerRef)` limits
  a system to events raised by players. The events themselves carry no player.
- A cancelled `PlaceBlockEvent` returns before the item is taken (`BlockPlaceUtils.placeBlock`), so a refused statue
  stays in the hand. `setConsumeItem(false)` keeps it there on an accepted placement too.
- There is no "player entered a chunk" hook. `ChunkTracker` / `PlayerChunkTrackerSystems` only stream chunks to the client.
  `ClaimEntrySystem` compares the player's chunk each tick.
- Custom pages: `InteractiveCustomUIPage` with a `.ui` file under `Common/UI/Custom/Pages/`. `PrefabEditorExitConfirmPage` is the model.
- Map: `world.getWorldMapManager().addMarkerProvider(id, provider)`; a provider adds `MapMarkerBuilder` markers to a `MarkersCollector`.
- Chunk components: `getChunkStoreRegistry().registerComponent(Class, id, BuilderCodec)` on `ChunkStore`.
- `chunk regenerate x z --confirm` drops chunk components that were added after generation (see below).
- Inventory is component based now (`InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING)`), not `Player.getInventory()`.
- `DefaultAssetMap` has `getAsset(id)`, not `getIndex`.

## Block "use" recognition (UseBlockEvent.Pre)

A non-owner is blocked when any of these is true:

1. the block entity at that position has an `ItemContainerBlock` (chests, barrels, anything with an inventory);
2. `BlockType.isDoor()` (doors, fence gates);
3. `BlockType.getBench() != null` (all benches);
4. the Use interaction is `Door`, `Door_Horizontal` (trapdoors) or starts with `Open_`
   (`Open_Container`, `Open_Processing_Bench`, `Open_Treasure_Container`).

Seats (`Block_Seat`), buttons and other interactions stay usable.

## Still unprotected (not player events)

Not observed in play (no client was available); this is what follows from the design. The systems only see
PlaceBlockEvent, BreakBlockEvent, DamageBlockEvent and UseBlockEvent.Pre raised on a player entity. So these are not stopped:
anything that fires `EnvironmentBreakBlockEvent` (not cancellable, so it cannot be stopped from here anyway), and
likely explosions, fluids, worms, falling blocks, fire, plant growth and changes made through the world API by other
mods. Events raised by non-player entities (NPCs, scripts) pass untouched.

## Known limits

- A statue that is placed and waiting for an answer is removed on Cancel, dismiss or disconnect. If the server stops
  while a popup is open the block stays in the world (the item was never taken from the player's inventory).
- The popup, titles and border particles could not be seen without a client; only their server side was run.
- In Creative mode the statue is never taken, as with any block.
- Claims are per world name. Renaming a world orphans its claims.
- The saved owner name is the name at claim time (`ClaimRegistry.rename` exists but nothing calls it yet).

## Claim marker on the chunk

`ClaimMarkerComponent` (owner UUID + time) is put on the claimed chunk and removed on release. Measured in a headless server:
it survives a restart and is lost by `chunk regenerate`. The registry (`SubfiefsApi`) is the only source of truth.

## Reserved for later

- `ClaimCost` (default `StatueClaimCost`) and the config keys `ClaimCost` / `SolariPerChunk`: Solari as ongoing production.
- `Claim.trusted` is saved and honoured by `Protection` but nothing fills it yet.
