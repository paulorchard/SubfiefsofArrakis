package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.logging.Level;

/** Puts the claim marker on, and takes it off, the chunk of a claim. Best effort: the registry is the truth. */
public final class ClaimMarkers {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static ComponentType<ChunkStore, ClaimMarkerComponent> type;

    private ClaimMarkers() {
    }

    static void setType(ComponentType<ChunkStore, ClaimMarkerComponent> markerType) {
        type = markerType;
    }

    public static ComponentType<ChunkStore, ClaimMarkerComponent> type() {
        return type;
    }

    /** Marks the claimed chunk. Loads the chunk if it is not loaded. */
    static void mark(Claim claim) {
        apply(claim, true);
    }

    /** Removes the marker from a released claim's chunk. */
    static void unmark(Claim claim) {
        apply(claim, false);
    }

    private static void apply(Claim claim, boolean on) {
        if (type == null) {
            return;
        }
        World world = Universe.get().getWorld(claim.world());
        if (world == null) {
            return;
        }
        long index = ChunkUtil.indexChunk(claim.chunkX(), claim.chunkZ());
        world.getChunkStore().getChunkReferenceAsync(index).whenComplete((Ref<ChunkStore> ref, Throwable error) -> {
            if (error != null || ref == null) {
                LOGGER.at(Level.WARNING).log("Could not reach chunk %d, %d in %s to update its claim marker",
                        claim.chunkX(), claim.chunkZ(), claim.world());
                return;
            }
            world.execute(() -> {
                if (!ref.isValid()) {
                    return;
                }
                Store<ChunkStore> store = world.getChunkStore().getStore();
                if (on) {
                    store.putComponent(ref, type, new ClaimMarkerComponent(claim.ownerUuid(), claim.claimedAt()));
                } else {
                    store.tryRemoveComponent(ref, type);
                }
                WorldChunk chunk = store.getComponent(ref, WorldChunk.getComponentType());
                if (chunk != null) {
                    chunk.markNeedsSaving();
                }
            });
        });
    }
}
