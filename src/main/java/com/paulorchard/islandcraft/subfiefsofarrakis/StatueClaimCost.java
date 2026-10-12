package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The default cost: the statue. Placing it does not consume it (see {@link ClaimPlaceSystem}); it is taken
 * here, when the player confirms. In Creative mode nothing is taken, as with any other block.
 */
public class StatueClaimCost implements ClaimCost {

    @Override
    public boolean charge(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store) {
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p == null || p.getGameMode() == GameMode.Creative) {
            return true;
        }
        // If the player moved the statue out of reach while the popup was open, the claim still goes
        // through: they placed it legitimately and the block is about to be removed.
        InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING)
                .removeItemStack(new ItemStack(SubfiefsOfArrakisPlugin.STATUE_ID, 1));
        return true;
    }
}
