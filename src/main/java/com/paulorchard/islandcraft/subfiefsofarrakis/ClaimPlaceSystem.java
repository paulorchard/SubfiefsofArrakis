package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * Placing blocks: the claim statue starts the claim flow, anything else is checked against the claims.
 * One system does both so the order between them is not left to chance.
 *
 * <p>A cancelled placement does not consume the item (the server returns before it takes it), so a refused
 * statue is still in the player's hand and nothing has to be given back.
 */
public class ClaimPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final String LANG = "server.subfiefsOfArrakis.claim.";
    private static final Query<EntityStore> PLAYERS = Archetype.of(PlayerRef.getComponentType());

    private final ClaimRegistry registry;
    private final ClaimService service;
    private final Protection protection;

    public ClaimPlaceSystem(ClaimRegistry registry, ClaimService service, Protection protection) {
        super(PlaceBlockEvent.class);
        this.registry = registry;
        this.service = service;
        this.protection = protection;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PLAYERS;
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, PlaceBlockEvent event) {
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Vector3i block = event.getTargetBlock();

        ItemStack held = event.getItemInHand();
        if (held == null || !SubfiefsOfArrakisPlugin.STATUE_ID.equals(held.getItemId())) {
            Claim claim = protection.blockedBy(world, block.x(), block.z(), player);
            if (claim != null) {
                event.setCancelled(true);
                protection.tell(player, claim);
            }
            return;
        }

        Claim existing = registry.at(world.getName(), ChunkUtil.chunkCoordinate(block.x()), ChunkUtil.chunkCoordinate(block.z()));
        if (existing != null) {
            event.setCancelled(true);
            boolean yours = existing.ownerUuid().equals(player.getUuid());
            player.sendMessage(Message.translation(LANG + (yours ? "alreadyYours" : "alreadyClaimed")));
            return;
        }
        if (service.hasPending(player.getUuid())) {
            event.setCancelled(true);
            player.sendMessage(Message.translation(LANG + "pending"));
            return;
        }

        // Keep the statue until the answer is in; the cost takes it on Confirm.
        event.setConsumeItem(false);
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        service.begin(world, ref, store, player, block);
    }
}
