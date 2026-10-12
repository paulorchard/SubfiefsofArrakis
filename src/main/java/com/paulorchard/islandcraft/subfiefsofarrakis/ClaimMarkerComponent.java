package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.time.Instant;
import java.util.UUID;

/**
 * The claim marker stored on the chunk itself, so another mod could read a claim straight off a chunk.
 * The registry in {@link ClaimRegistry} stays the source of truth: this is a spike, see the README notes.
 */
public class ClaimMarkerComponent implements Component<ChunkStore> {

    public static final String ID = "Arrakis_Subfief_Claim";

    public static final BuilderCodec<ClaimMarkerComponent> CODEC =
            BuilderCodec.builder(ClaimMarkerComponent.class, ClaimMarkerComponent::new)
                    .append(new KeyedCodec<>("Owner", Codec.UUID_STRING, false),
                            (marker, value) -> marker.owner = value,
                            marker -> marker.owner)
                    .add()
                    .append(new KeyedCodec<>("ClaimedAt", Codec.INSTANT, false),
                            (marker, value) -> marker.claimedAt = value,
                            marker -> marker.claimedAt)
                    .add()
                    .build();

    private UUID owner;
    private Instant claimedAt;

    public ClaimMarkerComponent() {
    }

    public ClaimMarkerComponent(UUID owner, Instant claimedAt) {
        this.owner = owner;
        this.claimedAt = claimedAt;
    }

    public UUID getOwner() {
        return owner;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new ClaimMarkerComponent(owner, claimedAt);
    }
}
