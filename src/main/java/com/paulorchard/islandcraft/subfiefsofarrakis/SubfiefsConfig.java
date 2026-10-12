package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Settings read from Subfiefs_of_Arrakis.json in the plugin's data folder. */
public class SubfiefsConfig {

    public static final String FILE_NAME = "Subfiefs_of_Arrakis";

    public static final BuilderCodec<SubfiefsConfig> CODEC =
            BuilderCodec.builder(SubfiefsConfig.class, SubfiefsConfig::new)
                    .documentation("Settings for subfiefs.")
                    .append(new KeyedCodec<>("ClaimCost", Codec.STRING, false),
                            (config, value) -> config.claimCost = value,
                            config -> config.claimCost)
                    .documentation("What a claim costs. Only \"Statue\" exists so far.")
                    .add()
                    .append(new KeyedCodec<>("SolariPerChunk", Codec.INTEGER, false),
                            (config, value) -> config.solariPerChunk = value,
                            config -> config.solariPerChunk)
                    .documentation("Reserved for a later update: Solari paid per claimed chunk. Not used yet.")
                    .add()
                    .append(new KeyedCodec<>("MessageCooldownSeconds", Codec.DOUBLE, false),
                            (config, value) -> config.messageCooldownSeconds = value,
                            config -> config.messageCooldownSeconds)
                    .documentation("Least time between two \"this chunk belongs to\" messages to one player.")
                    .add()
                    .append(new KeyedCodec<>("EntryTitles", Codec.BOOLEAN, false),
                            (config, value) -> config.entryTitles = value,
                            config -> config.entryTitles)
                    .documentation("Show a title when a player enters or leaves a claimed chunk.")
                    .add()
                    .append(new KeyedCodec<>("BorderParticles", Codec.BOOLEAN, false),
                            (config, value) -> config.borderParticles = value,
                            config -> config.borderParticles)
                    .documentation("Draw particles along the edge of claimed chunks near a player.")
                    .add()
                    .append(new KeyedCodec<>("BorderParticleId", Codec.STRING, false),
                            (config, value) -> config.borderParticleId = value,
                            config -> config.borderParticleId)
                    .documentation("Particle system used for the border.")
                    .add()
                    .append(new KeyedCodec<>("BorderDistance", Codec.DOUBLE, false),
                            (config, value) -> config.borderDistance = value,
                            config -> config.borderDistance)
                    .documentation("How close to a claim edge, in blocks, a player must be to see the border.")
                    .add()
                    .build();

    private String claimCost = "Statue";
    private int solariPerChunk = 0;
    private double messageCooldownSeconds = 3.0;
    private boolean entryTitles = true;
    private boolean borderParticles = true;
    private String borderParticleId = "Dust_Sparkles_Fine";
    private double borderDistance = 4.0;

    public String getClaimCost() {
        return claimCost;
    }

    public int getSolariPerChunk() {
        return solariPerChunk;
    }

    public double getMessageCooldownSeconds() {
        return messageCooldownSeconds;
    }

    public boolean isEntryTitles() {
        return entryTitles;
    }

    public boolean isBorderParticles() {
        return borderParticles;
    }

    public String getBorderParticleId() {
        return borderParticleId;
    }

    public double getBorderDistance() {
        return borderDistance;
    }
}
