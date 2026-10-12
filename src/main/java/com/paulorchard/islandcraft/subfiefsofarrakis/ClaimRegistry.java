package com.paulorchard.islandcraft.subfiefsofarrakis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.logging.Logger;
import com.hypixel.hytale.math.util.ChunkUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Every claim, in memory and in one JSON file. Reads are lock free and can come from any thread;
 * changes are serialised and saved at once.
 *
 * <p>File format, version 1:
 * {@code {"Version":1,"Claims":[{"World","ChunkX","ChunkZ","OwnerUuid","OwnerName","ClaimedAt","Trusted":[...]}]}}.
 * Unknown fields are ignored on load, so a newer version can add fields without breaking an older one.
 */
public class ClaimRegistry {

    public static final int FILE_VERSION = 1;

    private static final Logger LOGGER = Logger.getLogger(ClaimRegistry.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Key(String world, long chunk) {
    }

    private final Path file;
    private final Map<Key, Claim> byChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Claim> byOwner = new ConcurrentHashMap<>();

    public ClaimRegistry(Path file) {
        this.file = file;
    }

    private static Key keyOf(String world, int chunkX, int chunkZ) {
        return new Key(world, ChunkUtil.indexChunk(chunkX, chunkZ));
    }

    public Claim at(String world, int chunkX, int chunkZ) {
        return byChunk.get(keyOf(world, chunkX, chunkZ));
    }

    public Claim ownedBy(UUID owner) {
        return byOwner.get(owner);
    }

    /** A snapshot of the chunks claimed in a world, as {@link ChunkUtil#indexChunk} values. */
    public Set<Long> chunksIn(String world) {
        Set<Long> chunks = new HashSet<>();
        for (Key key : byChunk.keySet()) {
            if (key.world().equals(world)) {
                chunks.add(key.chunk());
            }
        }
        return Set.copyOf(chunks);
    }

    public Collection<Claim> all() {
        return List.copyOf(byChunk.values());
    }

    public Collection<Claim> in(String world) {
        return byChunk.values().stream().filter(c -> c.world().equals(world)).toList();
    }

    /**
     * Gives the chunk to the player and releases whatever they owned before. Returns the released claim,
     * or null if they had none. Fails (IllegalStateException) if the chunk is already claimed by anyone.
     */
    public synchronized Claim claim(String world, int chunkX, int chunkZ, UUID owner, String ownerName) {
        Key key = keyOf(world, chunkX, chunkZ);
        if (byChunk.containsKey(key)) {
            throw new IllegalStateException("Chunk " + chunkX + ", " + chunkZ + " in " + world + " is already claimed");
        }
        Claim previous = byOwner.get(owner);
        if (previous != null) {
            byChunk.remove(keyOf(previous.world(), previous.chunkX(), previous.chunkZ()));
        }
        Claim claim = new Claim(world, chunkX, chunkZ, owner, ownerName, Instant.now());
        byChunk.put(key, claim);
        byOwner.put(owner, claim);
        save();
        return previous;
    }

    /** Releases a player's claim. Returns it, or null if they had none. */
    public synchronized Claim release(UUID owner) {
        Claim claim = byOwner.remove(owner);
        if (claim != null) {
            byChunk.remove(keyOf(claim.world(), claim.chunkX(), claim.chunkZ()));
            save();
        }
        return claim;
    }

    /** Refreshes the saved name of an owner who has changed it. */
    public synchronized void rename(UUID owner, String name) {
        Claim claim = byOwner.get(owner);
        if (claim == null || claim.ownerName().equals(name)) {
            return;
        }
        Claim renamed = new Claim(claim.world(), claim.chunkX(), claim.chunkZ(), owner, name,
                claim.claimedAt(), claim.trusted());
        byOwner.put(owner, renamed);
        byChunk.put(keyOf(claim.world(), claim.chunkX(), claim.chunkZ()), renamed);
        save();
    }

    public synchronized void load() {
        byChunk.clear();
        byOwner.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                return;
            }
            int version = root.has("Version") ? root.get("Version").getAsInt() : 1;
            if (version > FILE_VERSION) {
                LOGGER.warning(file.getFileName() + " is version " + version + ", newer than this mod's " + FILE_VERSION
                        + "; reading what it can");
            }
            JsonArray claims = root.has("Claims") ? root.getAsJsonArray("Claims") : new JsonArray();
            for (JsonElement element : claims) {
                Claim claim = read(element.getAsJsonObject());
                if (claim == null) {
                    continue;
                }
                Key key = keyOf(claim.world(), claim.chunkX(), claim.chunkZ());
                if (byChunk.containsKey(key) || byOwner.containsKey(claim.ownerUuid())) {
                    LOGGER.warning("Ignoring duplicate claim for " + claim.ownerName() + " at " + claim.world()
                            + " " + claim.chunkX() + ", " + claim.chunkZ());
                    continue;
                }
                byChunk.put(key, claim);
                byOwner.put(claim.ownerUuid(), claim);
            }
        } catch (Exception e) {
            // Keep the unreadable file: the next save would otherwise overwrite someone's claims.
            LOGGER.log(Level.SEVERE, "Could not read " + file + "; claims start empty. The file is kept as .broken", e);
            byChunk.clear();
            byOwner.clear();
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Nothing more can be done.
            }
        }
    }

    private static Claim read(JsonObject o) {
        try {
            List<UUID> trusted = new ArrayList<>();
            if (o.has("Trusted")) {
                for (JsonElement t : o.getAsJsonArray("Trusted")) {
                    trusted.add(UUID.fromString(t.getAsString()));
                }
            }
            return new Claim(o.get("World").getAsString(), o.get("ChunkX").getAsInt(), o.get("ChunkZ").getAsInt(),
                    UUID.fromString(o.get("OwnerUuid").getAsString()), o.get("OwnerName").getAsString(),
                    Instant.parse(o.get("ClaimedAt").getAsString()), trusted);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Skipping a claim entry that could not be read: " + o, e);
            return null;
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("Version", FILE_VERSION);
        JsonArray claims = new JsonArray();
        for (Claim claim : byChunk.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("World", claim.world());
            o.addProperty("ChunkX", claim.chunkX());
            o.addProperty("ChunkZ", claim.chunkZ());
            o.addProperty("OwnerUuid", claim.ownerUuid().toString());
            o.addProperty("OwnerName", claim.ownerName());
            o.addProperty("ClaimedAt", claim.claimedAt().toString());
            JsonArray trusted = new JsonArray();
            claim.trusted().forEach(u -> trusted.add(u.toString()));
            o.add("Trusted", trusted);
            claims.add(o);
        }
        root.add("Claims", claims);

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not save claims to " + file, e);
        }
    }
}
