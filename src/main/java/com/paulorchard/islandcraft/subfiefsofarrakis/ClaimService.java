package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The claiming flow. A placed statue stays in the world, un-consumed, while the player answers the popup:
 * Confirm removes the block and takes the statue (via {@link ClaimCost}) and saves the claim; Cancel,
 * closing the popup and disconnecting only remove the block, so the item never has to be given back.
 */
public class ClaimService {

    private static final String LANG = "server.subfiefsOfArrakis.claim.";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** A statue that is placed and waiting for an answer. */
    private record Pending(World world, int x, int y, int z, int chunkX, int chunkZ) {
    }

    private final ClaimRegistry registry;
    private final Supplier<ClaimCost> cost;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ClaimService(ClaimRegistry registry, Supplier<ClaimCost> cost) {
        this.registry = registry;
        this.cost = cost;
    }

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    /**
     * Starts a claim for a statue just placed at {@code block}. Called from the PlaceBlockEvent handler,
     * so the popup is queued to the world's next tick and the block gets placed first.
     */
    public void begin(World world, Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef player, Vector3i block) {
        Pending entry = new Pending(world, block.x(), block.y(), block.z(),
                ChunkUtil.chunkCoordinate(block.x()), ChunkUtil.chunkCoordinate(block.z()));
        pending.put(player.getUuid(), entry);
        world.execute(() -> {
            if (!ref.isValid() || pending.get(player.getUuid()) != entry) {
                return;
            }
            try {
                Player p = store.getComponent(ref, Player.getComponentType());
                Claim previous = registry.ownedBy(player.getUuid());
                p.getPageManager().openCustomPage(ref, store, new ClaimConfirmPage(player, this, entry.chunkX(),
                        entry.chunkZ(), previous));
            } catch (RuntimeException e) {
                // No popup could be built: claim at once, and say so.
                LOGGER.at(Level.WARNING).withCause(e).log("Could not show the claim popup; claiming at once");
                player.sendMessage(Message.translation(LANG + "immediate"));
                confirm(ref, store, player);
            }
        });
    }

    /** The player pressed Confirm. Runs on the world's thread. */
    public void confirm(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef player) {
        Pending entry = pending.remove(player.getUuid());
        close(ref, store);
        if (entry == null) {
            return;
        }
        removeStatue(entry);
        String worldName = entry.world().getName();
        if (registry.at(worldName, entry.chunkX(), entry.chunkZ()) != null) {
            player.sendMessage(Message.translation(LANG + "takenMeanwhile"));
            return;
        }
        if (!cost.get().charge(player, ref, store)) {
            player.sendMessage(Message.translation(LANG + "cannotPay"));
            return;
        }
        Claim released = registry.claim(worldName, entry.chunkX(), entry.chunkZ(), player.getUuid(), player.getUsername());
        Claim claim = registry.at(worldName, entry.chunkX(), entry.chunkZ());
        ClaimMarkers.mark(claim);
        if (released != null) {
            ClaimMarkers.unmark(released);
            cost.get().released(released);
            player.sendMessage(Message.translation(LANG + "doneReleased")
                    .param("x", entry.chunkX()).param("z", entry.chunkZ())
                    .param("oldX", released.chunkX()).param("oldZ", released.chunkZ()));
        } else {
            player.sendMessage(Message.translation(LANG + "done").param("x", entry.chunkX()).param("z", entry.chunkZ()));
        }
    }

    /** The player pressed Cancel. Runs on the world's thread. */
    public void cancel(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef player) {
        close(ref, store);
        if (abandon(player.getUuid())) {
            player.sendMessage(Message.translation(LANG + "cancelled"));
        }
    }

    /**
     * Drops a pending claim without a word: the popup was dismissed or the player left. Removes the statue.
     * Returns true if there was one.
     */
    public boolean abandon(UUID player) {
        Pending entry = pending.remove(player);
        if (entry == null) {
            return false;
        }
        removeStatue(entry);
        return true;
    }

    /** Server stopping: take every waiting statue out of the world. */
    public void abandonAll() {
        for (UUID player : pending.keySet()) {
            abandon(player);
        }
    }

    /** Releases a claim for good (admin). Returns it, or null. */
    public Claim release(UUID owner) {
        Claim released = registry.release(owner);
        if (released != null) {
            ClaimMarkers.unmark(released);
            cost.get().released(released);
        }
        return released;
    }

    public Collection<Claim> claims() {
        return registry.all();
    }

    private static void close(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    /** Clears the block the statue was placed as, without the item drop breaking it would give. */
    private static void removeStatue(Pending entry) {
        entry.world().execute(() -> {
            WorldAccess access = new WorldAccess(entry.world());
            if (!access.isLoaded(entry.x(), entry.z())) {
                return;
            }
            BlockType type = access.blockType(entry.x(), entry.y(), entry.z());
            if (type != null && SubfiefsOfArrakisPlugin.statueBlockId().equals(type.getId())) {
                access.setBlock(entry.x(), entry.y(), entry.z(), "Empty", 0);
            }
        });
    }
}
