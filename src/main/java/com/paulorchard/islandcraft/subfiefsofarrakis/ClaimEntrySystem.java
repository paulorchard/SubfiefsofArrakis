package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Feedback for walking around claims. When a player crosses into a different chunk, a title says they are
 * entering or leaving a subfief. While they stand near the edge of a claimed chunk, particles mark that edge.
 * (The server has no "player entered a chunk" event: ChunkTracker only streams chunks to the client. This
 * system compares the player's chunk on each tick instead.)
 */
public class ClaimEntrySystem extends EntityTickingSystem<EntityStore> {

    private static final String LANG = "server.subfiefsOfArrakis.";
    private static final double BORDER_INTERVAL_SECONDS = 0.5;
    private static final double BORDER_REACH = 8.0;
    private static final double BORDER_STEP = 2.0;

    private record Spot(String world, int chunkX, int chunkZ) {
    }

    private static final class PlayerState {
        Spot spot;
        double borderTimer;
    }

    private final Query<EntityStore> query = Archetype.of(
            Player.getComponentType(), PlayerRef.getComponentType(), TransformComponent.getComponentType());
    private final ClaimRegistry registry;
    private final Supplier<SubfiefsConfig> config;
    private final Supplier<Boolean> particleAvailable;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public ClaimEntrySystem(ClaimRegistry registry, Supplier<SubfiefsConfig> config, Supplier<Boolean> particleAvailable) {
        this.registry = registry;
        this.config = config;
        this.particleAvailable = particleAvailable;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    public void forget(UUID player) {
        states.remove(player);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            return;
        }
        Vector3d position = transform.getPosition();
        String world = store.getExternalData().getWorld().getName();
        int chunkX = ChunkUtil.chunkCoordinate(position.x());
        int chunkZ = ChunkUtil.chunkCoordinate(position.z());

        PlayerState state = states.computeIfAbsent(player.getUuid(), id -> new PlayerState());
        Spot previous = state.spot;
        if (previous == null || previous.chunkX() != chunkX || previous.chunkZ() != chunkZ
                || !previous.world().equals(world)) {
            state.spot = new Spot(world, chunkX, chunkZ);
            // Joining the world inside a claim says nothing; only crossing a chunk border does.
            if (previous != null) {
                announce(player, registry.at(previous.world(), previous.chunkX(), previous.chunkZ()),
                        registry.at(world, chunkX, chunkZ));
            }
        }

        SubfiefsConfig cfg = config.get();
        if (cfg.isBorderParticles() && particleAvailable.get()) {
            state.borderTimer += dt;
            if (state.borderTimer >= BORDER_INTERVAL_SECONDS) {
                state.borderTimer = 0;
                drawBorders(cfg, store, commandBuffer, position, world, chunkX, chunkZ);
            }
        }
    }

    private void announce(PlayerRef player, Claim from, Claim to) {
        if (!config.get().isEntryTitles() || java.util.Objects.equals(from, to)) {
            return;
        }
        if (to != null) {
            title(player, to, "enter");
        } else {
            title(player, from, "leave");
        }
    }

    private static void title(PlayerRef player, Claim claim, String kind) {
        boolean own = claim.ownerUuid().equals(player.getUuid());
        Message text = Message.translation(LANG + kind + (own ? ".own" : ".other")).param("owner", claim.ownerName());
        EventTitleUtil.showEventTitleToPlayer(player, text, Message.empty(), false);
    }

    /** Marks the sides of claimed chunks around the player that are within reach of where they stand. */
    private void drawBorders(SubfiefsConfig cfg, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                             Vector3d position, String world, int chunkX, int chunkZ) {
        double reach = cfg.getBorderDistance();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (registry.at(world, chunkX + dx, chunkZ + dz) == null) {
                    continue;
                }
                double minX = (chunkX + dx) * (double) ChunkUtil.SIZE;
                double minZ = (chunkZ + dz) * (double) ChunkUtil.SIZE;
                double maxX = minX + ChunkUtil.SIZE;
                double maxZ = minZ + ChunkUtil.SIZE;
                if (Math.abs(position.x() - minX) <= reach) {
                    alongZ(cfg, commandBuffer, position, minX, minZ, maxZ);
                }
                if (Math.abs(position.x() - maxX) <= reach) {
                    alongZ(cfg, commandBuffer, position, maxX, minZ, maxZ);
                }
                if (Math.abs(position.z() - minZ) <= reach) {
                    alongX(cfg, commandBuffer, position, minZ, minX, maxX);
                }
                if (Math.abs(position.z() - maxZ) <= reach) {
                    alongX(cfg, commandBuffer, position, maxZ, minX, maxX);
                }
            }
        }
    }

    private static void alongZ(SubfiefsConfig cfg, CommandBuffer<EntityStore> accessor, Vector3d position,
                               double x, double minZ, double maxZ) {
        double from = Math.max(minZ, Math.floor(position.z() / BORDER_STEP) * BORDER_STEP - BORDER_REACH);
        double to = Math.min(maxZ, position.z() + BORDER_REACH);
        for (double z = from; z <= to; z += BORDER_STEP) {
            spawn(cfg, accessor, x, position.y(), z);
        }
    }

    private static void alongX(SubfiefsConfig cfg, CommandBuffer<EntityStore> accessor, Vector3d position,
                               double z, double minX, double maxX) {
        double from = Math.max(minX, Math.floor(position.x() / BORDER_STEP) * BORDER_STEP - BORDER_REACH);
        double to = Math.min(maxX, position.x() + BORDER_REACH);
        for (double x = from; x <= to; x += BORDER_STEP) {
            spawn(cfg, accessor, x, position.y(), z);
        }
    }

    private static void spawn(SubfiefsConfig cfg, CommandBuffer<EntityStore> accessor, double x, double y, double z) {
        ParticleUtil.spawnParticleEffect(cfg.getBorderParticleId(), new Vector3d(x, y + 0.5, z), accessor);
        ParticleUtil.spawnParticleEffect(cfg.getBorderParticleId(), new Vector3d(x, y + 1.5, z), accessor);
    }
}
