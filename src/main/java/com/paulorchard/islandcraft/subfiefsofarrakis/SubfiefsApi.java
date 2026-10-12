package com.paulorchard.islandcraft.subfiefsofarrakis;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only view of the claims for other mods. Safe to call from any thread. Before this mod has started,
 * or in a world with no claims, every call answers "nothing claimed" without error.
 *
 * <p>A mod that lists this one under OptionalDependencies should call it only when the plugin is loaded,
 * so this class is never resolved otherwise.
 */
public final class SubfiefsApi {

    private SubfiefsApi() {
    }

    public static boolean isClaimed(String worldName, int chunkX, int chunkZ) {
        ClaimRegistry registry = SubfiefsOfArrakisPlugin.registry();
        return registry != null && registry.at(worldName, chunkX, chunkZ) != null;
    }

    public static Optional<UUID> ownerOf(String worldName, int chunkX, int chunkZ) {
        ClaimRegistry registry = SubfiefsOfArrakisPlugin.registry();
        Claim claim = registry == null ? null : registry.at(worldName, chunkX, chunkZ);
        return claim == null ? Optional.empty() : Optional.of(claim.ownerUuid());
    }

    /**
     * A snapshot of the claimed chunks in a world. Each value is a chunk index as made by
     * {@code com.hypixel.hytale.math.util.ChunkUtil.indexChunk(chunkX, chunkZ)}; decode with
     * {@code ChunkUtil.xOfChunkIndex} and {@code zOfChunkIndex}.
     */
    public static Set<Long> claimedChunks(String worldName) {
        ClaimRegistry registry = SubfiefsOfArrakisPlugin.registry();
        return registry == null ? Set.of() : registry.chunksIn(worldName);
    }
}
