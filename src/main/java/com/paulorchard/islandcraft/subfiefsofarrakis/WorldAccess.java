package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Loaded check and block access on the 0.7.0 chunk store. Call from the world thread; create one per operation. */
final class WorldAccess {

    private final ChunkStore chunks;
    private final SectionReader sections;

    WorldAccess(World world) {
        this.chunks = world.getChunkStore();
        this.sections = new SectionReader(chunks);
    }

    /** True when the chunk column holding this world x/z is loaded. */
    boolean isLoaded(int x, int z) {
        Ref<ChunkStore> ref = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        return ref != null && ref.isValid();
    }

    /** The block type at a world position; null for air, an unknown id, or a section that is not loaded. */
    BlockType blockType(int x, int y, int z) {
        int id = sections.getBlock(x, y, z);
        return id <= 0 ? null : BlockType.getAssetMap().getAsset(id);
    }

    /** Sets the block by name (settings are SetBlockSettings flags, 0 = normal); false if not loaded or unknown. */
    boolean setBlock(int x, int y, int z, String blockName, int settings) {
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return false;
        }
        int id = BlockType.getAssetMap().getIndex(blockName);
        BlockType type = id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
        return type != null && BlockOperations.setBlock(chunks, section, x, y, z, id, type, 0, 0, settings);
    }

    /** Y of the top block of the column at world x/z (heightmap: plants and decor are skipped); null when unknown. */
    Integer height(int x, int z) {
        Ref<ChunkStore> column = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (column == null || !column.isValid()) {
            return null;
        }
        HeightmapColumn map = chunks.getStore().getComponentConcurrent(column, HeightmapColumn.getComponentType());
        if (map == null) {
            return null;
        }
        int height = map.getHeight(x, z);
        return height == Integer.MIN_VALUE ? null : height;
    }
}
