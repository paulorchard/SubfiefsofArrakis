package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * What a claim costs. Today that is the statue itself; a Solari charge paid as ongoing production can be
 * added later as another implementation, chosen by {@link SubfiefsConfig#getClaimCost()}.
 */
public interface ClaimCost {

    /**
     * Takes the price of a claim from the player, on the world's thread, at the moment they confirm.
     * Returns false, having told the player why, if it cannot be paid. Nothing is claimed then.
     */
    boolean charge(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store);

    /** Called after a claim has been released, whether by the owner claiming elsewhere or by an admin. */
    default void released(Claim claim) {
    }
}
