package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * Break, damage and use protection. Only events raised by a player are looked at, because the query asks
 * for a PlayerRef: events from anything else (NPCs, scripts) pass untouched.
 */
public final class BlockProtectionSystems {

    private static final Query<EntityStore> PLAYERS = Archetype.of(PlayerRef.getComponentType());

    private BlockProtectionSystems() {
    }

    /** Looks up the acting player and decides. Returns the blocking claim, or null if the action may go on. */
    private static Claim check(Protection protection, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, Vector3i block) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return null;
        }
        World world = store.getExternalData().getWorld();
        Claim claim = protection.blockedBy(world, block.x(), block.z(), player);
        if (claim != null) {
            protection.tell(player, claim);
        }
        return claim;
    }

    public static class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private final Protection protection;

        public Break(Protection protection) {
            super(BreakBlockEvent.class);
            this.protection = protection;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
            if (check(protection, index, chunk, store, event.getTargetBlock()) != null) {
                event.setCancelled(true);
            }
        }
    }

    public static class Damage extends EntityEventSystem<EntityStore, DamageBlockEvent> {

        private final Protection protection;

        public Damage(Protection protection) {
            super(DamageBlockEvent.class);
            this.protection = protection;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, DamageBlockEvent event) {
            if (check(protection, index, chunk, store, event.getTargetBlock()) != null) {
                event.setCancelled(true);
                // Cancelling alone could still leave a crack on the block; no damage means nothing to show.
                event.setDamage(0.0f);
            }
        }
    }

    public static class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

        private final Protection protection;

        public Use(Protection protection) {
            super(UseBlockEvent.Pre.class);
            this.protection = protection;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, UseBlockEvent.Pre event) {
            Vector3i block = event.getTargetBlock();
            World world = store.getExternalData().getWorld();
            // Cheap checks first: most blocks are not in a claim, and most claimed-chunk blocks are not openable.
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) {
                return;
            }
            Claim claim = protection.blockedBy(world, block.x(), block.z(), player);
            if (claim == null || !BlockUseRules.isRestricted(world, event.getBlockType(), block.x(), block.y(), block.z())) {
                return;
            }
            event.setCancelled(true);
            protection.tell(player, claim);
        }
    }
}
