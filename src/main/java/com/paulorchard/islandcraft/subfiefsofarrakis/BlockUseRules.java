package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;

/**
 * Which blocks a non-owner may not "use" inside a claim: anything that holds an inventory, any bench,
 * and doors, gates and trapdoors. Everything else (buttons, seats, plain interactions) stays usable.
 */
public final class BlockUseRules {

    private BlockUseRules() {
    }

    /**
     * True if the block opens something. Recognised by, in order:
     * <ol>
     *   <li>the block entity at that position has an item container (chests, barrels, anything with an inventory);</li>
     *   <li>{@link BlockType#isDoor()} (doors and fence gates);</li>
     *   <li>{@link BlockType#getBench()} is set (crafting, processing and other benches);</li>
     *   <li>its Use interaction is {@code Door}, {@code Door_Horizontal} (trapdoors) or starts with {@code Open_}
     *       ({@code Open_Container}, {@code Open_Processing_Bench}, {@code Open_Treasure_Container}).</li>
     * </ol>
     */
    public static boolean isRestricted(World world, BlockType type, int x, int y, int z) {
        if (type == null) {
            return false;
        }
        if (type.isDoor() || type.getBench() != null) {
            return true;
        }
        Map<InteractionType, String> interactions = type.getInteractions();
        String use = interactions == null ? null : interactions.get(InteractionType.Use);
        if (use != null && (use.equals("Door") || use.startsWith("Door_") || use.startsWith("Open_"))) {
            return true;
        }
        return BlockModule.getComponent(BlockModule.get().getItemContainerBlockComponentType(), world, x, y, z) != null;
    }
}
