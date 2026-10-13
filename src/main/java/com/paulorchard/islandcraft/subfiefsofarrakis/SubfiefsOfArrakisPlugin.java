package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Files;
import java.util.logging.Level;

public class SubfiefsOfArrakisPlugin extends JavaPlugin {

    /** The item and block id of the claim statue. */
    public static final String STATUE_ID = "Arrakis_Subfief_Claim_Statue";

    private static final String CLAIMS_FILE = "Subfiefs_of_Arrakis_Claims.json";

    private static volatile ClaimRegistry registry;

    // Must be created before setup(); the server loads it from disk in between.
    private final Config<SubfiefsConfig> config = withConfig(SubfiefsConfig.FILE_NAME, SubfiefsConfig.CODEC);

    private ClaimService service;
    private Protection protection;
    private ClaimEntrySystem entrySystem;
    private boolean particleAvailable;

    public SubfiefsOfArrakisPlugin(JavaPluginInit init) {
        super(init);
    }

    /** The id of the block the statue places; the item and block ids need not match. */
    static String statueBlockId() {
        Item item = Item.getAssetMap().getAsset(STATUE_ID);
        return item != null && item.hasBlockType() ? item.getBlockId() : STATUE_ID;
    }

    /** The registry for {@link SubfiefsApi}; null before this plugin has set up. */
    static ClaimRegistry registry() {
        return registry;
    }

    @Override
    protected void setup() {
        writeConfig();

        ClaimRegistry claims = new ClaimRegistry(getDataDirectory().resolve(CLAIMS_FILE));
        claims.load();
        registry = claims;
        getLogger().at(Level.INFO).log("Loaded %d claim(s)", claims.all().size());

        ClaimMarkers.setType(getChunkStoreRegistry().registerComponent(
                ClaimMarkerComponent.class, ClaimMarkerComponent.ID, ClaimMarkerComponent.CODEC));

        ClaimCost cost = new StatueClaimCost();
        service = new ClaimService(claims, () -> cost);
        protection = new Protection(claims, config::get);

        getEntityStoreRegistry().registerSystem(new ClaimPlaceSystem(claims, service, protection));
        getEntityStoreRegistry().registerSystem(new BlockProtectionSystems.Break(protection));
        getEntityStoreRegistry().registerSystem(new BlockProtectionSystems.Damage(protection));
        getEntityStoreRegistry().registerSystem(new BlockProtectionSystems.Use(protection));
        entrySystem = new ClaimEntrySystem(claims, config::get, () -> particleAvailable);
        getEntityStoreRegistry().registerSystem(entrySystem);

        getCommandRegistry().registerCommand(new SubfiefCommand(claims, service));

        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            var id = event.getPlayerRef().getUuid();
            service.abandon(id);
            protection.forget(id);
            entrySystem.forget(id);
        });

        ClaimMapMarkers markers = new ClaimMapMarkers(claims);
        getEventRegistry().registerGlobal(AddWorldEvent.class,
                event -> event.getWorld().getWorldMapManager().addMarkerProvider(ClaimMapMarkers.PROVIDER_ID, markers));
        for (World world : Universe.get().getWorlds().values()) {
            world.getWorldMapManager().addMarkerProvider(ClaimMapMarkers.PROVIDER_ID, markers);
        }
    }

    /** Assets are loaded by now, so say at once if one this mod relies on is missing. */
    @Override
    protected void start() {
        String particle = config.get().getBorderParticleId() + "_Own";
        particleAvailable = ParticleSystem.getAssetMap().getAsset(particle) != null;
        if (config.get().isBorderParticles() && !particleAvailable) {
            getLogger().at(Level.WARNING).log("Border particle '%s' does not exist, so no borders are drawn", particle);
        }
        if (Item.getAssetMap().getAsset(STATUE_ID) == null) {
            getLogger().at(Level.SEVERE).log("Item '%s' is not loaded, so nobody can claim anything", STATUE_ID);
        } else {
            getLogger().at(Level.INFO).log("Claim statue places block '%s'", statueBlockId());
        }
    }

    @Override
    protected void shutdown() {
        if (service != null) {
            service.abandonAll();
        }
        registry = null;
    }

    /**
     * The server reads the config file but never creates it. Writing it back on every start
     * creates it on first run and adds any setting a newer version of the mod has introduced.
     */
    private void writeConfig() {
        try {
            Files.createDirectories(getDataDirectory());
            config.save().join();
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Could not write config to %s", getDataDirectory());
        }
    }
}
