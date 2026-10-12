package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who may change blocks in a claimed chunk, and the throttled "this chunk belongs to" message. */
public final class Protection {

    public static final String BYPASS_PERMISSION = "islandcraft.subfiefs.bypass";

    private final ClaimRegistry registry;
    private final java.util.function.Supplier<SubfiefsConfig> config;
    private final Map<UUID, Long> lastTold = new ConcurrentHashMap<>();

    public Protection(ClaimRegistry registry, java.util.function.Supplier<SubfiefsConfig> config) {
        this.registry = registry;
        this.config = config;
    }

    /**
     * The claim that stops this player from acting at a block, or null if they may act: the chunk is free,
     * they own it, or they hold the bypass permission.
     */
    public Claim blockedBy(World world, int blockX, int blockZ, PlayerRef player) {
        Claim claim = registry.at(world.getName(), ChunkUtil.chunkCoordinate(blockX), ChunkUtil.chunkCoordinate(blockZ));
        if (claim == null || claim.ownerUuid().equals(player.getUuid()) || claim.trusted().contains(player.getUuid())) {
            return null;
        }
        return player.hasPermission(BYPASS_PERMISSION) ? null : claim;
    }

    /** Tells the player whose chunk this is, at most once every few seconds. */
    public void tell(PlayerRef player, Claim claim) {
        long now = System.nanoTime();
        long cooldown = (long) (config.get().getMessageCooldownSeconds() * 1_000_000_000L);
        Long last = lastTold.get(player.getUuid());
        if (last != null && now - last < cooldown) {
            return;
        }
        lastTold.put(player.getUuid(), now);
        player.sendMessage(Message.translation("server.subfiefsOfArrakis.protected").param("owner", claim.ownerName()));
    }

    public void forget(UUID player) {
        lastTold.remove(player);
    }
}
