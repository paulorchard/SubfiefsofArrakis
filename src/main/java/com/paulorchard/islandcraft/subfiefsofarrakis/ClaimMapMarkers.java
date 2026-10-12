package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import org.joml.Vector3d;

/** One point marker at the centre of every claim in the world, labelled with the owner. */
public class ClaimMapMarkers implements WorldMapManager.MarkerProvider {

    public static final String PROVIDER_ID = "Arrakis_Subfiefs";
    private static final String ICON = "Home.png";

    private final ClaimRegistry registry;

    public ClaimMapMarkers(ClaimRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void update(World world, Player player, MarkersCollector collector) {
        for (Claim claim : registry.in(world.getName())) {
            double x = claim.chunkX() * (double) ChunkUtil.SIZE + ChunkUtil.SIZE / 2.0;
            double z = claim.chunkZ() * (double) ChunkUtil.SIZE + ChunkUtil.SIZE / 2.0;
            collector.addIgnoreViewDistance(new MapMarkerBuilder(
                    PROVIDER_ID + "_" + claim.ownerUuid(), ICON, new Transform(new Vector3d(x, 0.0, z)))
                    .withName(Message.translation("server.subfiefsOfArrakis.map.marker").param("owner", claim.ownerName()))
                    .build());
        }
    }
}
