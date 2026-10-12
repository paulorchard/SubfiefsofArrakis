package com.paulorchard.islandcraft.subfiefsofarrakis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One claimed chunk column. {@code trusted} is reserved for a later sharing feature: it is saved and loaded
 * already, so adding trust commands will not change the file format.
 */
public record Claim(String world, int chunkX, int chunkZ, UUID ownerUuid, String ownerName,
                    Instant claimedAt, List<UUID> trusted) {

    public Claim {
        trusted = List.copyOf(trusted);
    }

    public Claim(String world, int chunkX, int chunkZ, UUID ownerUuid, String ownerName, Instant claimedAt) {
        this(world, chunkX, chunkZ, ownerUuid, ownerName, claimedAt, List.of());
    }
}
