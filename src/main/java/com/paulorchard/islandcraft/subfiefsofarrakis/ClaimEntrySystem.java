package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import org.joml.Vector3d;

import java.util.List;
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
    private static final double BORDER_STEP = 0.5;

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
                drawBorders(cfg, store, commandBuffer, player, position, world, chunkX, chunkZ);
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

    /**
     * Lays a flat strip along every edge of the claimed chunks within reach of the player, white for their own
     * claim and red for anyone else's, so a whole claim can be seen from up to {@code BorderDistance} blocks away.
     */
    private void drawBorders(SubfiefsConfig cfg, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                             PlayerRef player, Vector3d position, String world, int chunkX, int chunkZ) {
        double reach = cfg.getBorderDistance();
        int radius = (int) Math.ceil(reach / ChunkUtil.SIZE);
        Ref<EntityStore> self = player.getReference();
        if (self == null) {
            return;
        }
        // Only the viewer gets the strips: their colour depends on whose claim it is for them.
        List<Ref<EntityStore>> viewer = List.of(self);
        WorldAccess access = new WorldAccess(store.getExternalData().getWorld());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Claim claim = registry.at(world, chunkX + dx, chunkZ + dz);
                if (claim == null) {
                    continue;
                }
                String particle = cfg.getBorderParticleId() + (claim.ownerUuid().equals(player.getUuid()) ? "_Own" : "_Other");
                double minX = (chunkX + dx) * (double) ChunkUtil.SIZE;
                double minZ = (chunkZ + dz) * (double) ChunkUtil.SIZE;
                double maxX = minX + ChunkUtil.SIZE;
                double maxZ = minZ + ChunkUtil.SIZE;
                if (Math.abs(position.x() - minX) <= reach) {
                    alongZ(particle, reach, access, commandBuffer, viewer, position, minX, minX, minZ, maxZ);
                }
                if (Math.abs(position.x() - maxX) <= reach) {
                    alongZ(particle, reach, access, commandBuffer, viewer, position, maxX, maxX - 1, minZ, maxZ);
                }
                if (Math.abs(position.z() - minZ) <= reach) {
                    alongX(particle, reach, access, commandBuffer, viewer, position, minZ, minZ, minX, maxX);
                }
                if (Math.abs(position.z() - maxZ) <= reach) {
                    alongX(particle, reach, access, commandBuffer, viewer, position, maxZ, maxZ - 1, minX, maxX);
                }
            }
        }
    }

    /** An edge running north-south at {@code x}; {@code column} is the block column just inside the chunk. */
    private static void alongZ(String particle, double reach, WorldAccess access, CommandBuffer<EntityStore> accessor, List<Ref<EntityStore>> viewer,
                               Vector3d position, double x, double column, double minZ, double maxZ) {
        double from = Math.max(minZ, position.z() - reach);
        double to = Math.min(maxZ, position.z() + reach);
        for (double z = Math.floor(from / BORDER_STEP) * BORDER_STEP + BORDER_STEP / 2; z < to; z += BORDER_STEP) {
            if (z >= minZ && z < maxZ) {
                spawn(particle, access, accessor, viewer, position, x, column, z, Math.floor(z), 0f);
            }
        }
    }

    /** An edge running east-west at {@code z}; the strip is turned a quarter to lie along it. */
    private static void alongX(String particle, double reach, WorldAccess access, CommandBuffer<EntityStore> accessor, List<Ref<EntityStore>> viewer,
                               Vector3d position, double z, double column, double minX, double maxX) {
        double from = Math.max(minX, position.x() - reach);
        double to = Math.min(maxX, position.x() + reach);
        for (double x = Math.floor(from / BORDER_STEP) * BORDER_STEP + BORDER_STEP / 2; x < to; x += BORDER_STEP) {
            if (x >= minX && x < maxX) {
                spawn(particle, access, accessor, viewer, position, x, Math.floor(x), z, column, (float) (Math.PI / 2));
            }
        }
    }

    /** One strip lying on the top face of the column's surface block; at the player's height where it is unknown. */
    private static void spawn(String particle, WorldAccess access, CommandBuffer<EntityStore> accessor, List<Ref<EntityStore>> viewer,
                              Vector3d position, double x, double columnX, double z, double columnZ, float yaw) {
        Integer height = access.height((int) columnX, (int) columnZ);
        // The block's top face is at height + 1; a hair above it avoids z-fighting with the terrain.
        double y = height == null ? position.y() : height + 1.02;
        ParticleUtil.spawnParticleEffect(particle, new Vector3d(x, y, z), new Rotation3f(0f, yaw, 0f), viewer, accessor);
    }
}
