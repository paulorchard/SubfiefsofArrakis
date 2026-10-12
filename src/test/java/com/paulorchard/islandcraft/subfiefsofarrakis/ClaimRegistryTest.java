package com.paulorchard.islandcraft.subfiefsofarrakis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class ClaimRegistryTest {

    @TempDir
    Path dir;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private ClaimRegistry registry() {
        return new ClaimRegistry(dir.resolve("claims.json"));
    }

    @Test
    void claimedChunkIsClaimedAndNeighbourIsNot() {
        ClaimRegistry registry = registry();
        registry.claim("default", 3, -4, alice, "Alice");
        assertEquals(alice, registry.at("default", 3, -4).ownerUuid());
        assertNull(registry.at("default", 4, -4));
        assertNull(registry.at("other", 3, -4));
    }

    @Test
    void claimingElsewhereReleasesThePreviousClaim() {
        ClaimRegistry registry = registry();
        assertNull(registry.claim("default", 0, 0, alice, "Alice"));
        Claim released = registry.claim("default", 5, 5, alice, "Alice");
        assertNotNull(released);
        assertEquals(0, released.chunkX());
        assertNull(registry.at("default", 0, 0));
        assertNotNull(registry.at("default", 5, 5));
        assertEquals(1, registry.all().size());
    }

    @Test
    void aClaimedChunkCannotBeClaimedAgain() {
        ClaimRegistry registry = registry();
        registry.claim("default", 0, 0, alice, "Alice");
        assertThrows(IllegalStateException.class, () -> registry.claim("default", 0, 0, bob, "Bob"));
        assertThrows(IllegalStateException.class, () -> registry.claim("default", 0, 0, alice, "Alice"));
        assertEquals(alice, registry.at("default", 0, 0).ownerUuid());
    }

    @Test
    void claimsSurviveARestart() {
        ClaimRegistry first = registry();
        first.claim("default", 1, 2, alice, "Alice");
        first.claim("default", -7, 9, bob, "Bob");

        ClaimRegistry second = registry();
        second.load();
        assertEquals("Alice", second.at("default", 1, 2).ownerName());
        assertEquals(bob, second.at("default", -7, 9).ownerUuid());
        assertEquals(first.at("default", 1, 2).claimedAt(), second.at("default", 1, 2).claimedAt());
        assertEquals(second.at("default", 1, 2), second.ownedBy(alice));
    }

    @Test
    void releaseFreesTheChunkAndIsSaved() {
        ClaimRegistry first = registry();
        first.claim("default", 1, 2, alice, "Alice");
        assertNotNull(first.release(alice));
        assertNull(first.release(alice));

        ClaimRegistry second = registry();
        second.load();
        assertTrue(second.all().isEmpty());
    }

    @Test
    void claimedChunksIsASnapshotPerWorld() {
        ClaimRegistry registry = registry();
        registry.claim("default", 1, 2, alice, "Alice");
        registry.claim("nether", 1, 2, bob, "Bob");
        var chunks = registry.chunksIn("default");
        assertEquals(1, chunks.size());
        assertTrue(chunks.contains(ChunkUtil.indexChunk(1, 2)));
        registry.release(alice);
        assertEquals(1, chunks.size(), "a snapshot does not change afterwards");
        assertTrue(registry.chunksIn("default").isEmpty());
        assertTrue(registry.chunksIn("missing").isEmpty());
    }

    @Test
    void fileCarriesAVersionAndATrustedList() throws Exception {
        ClaimRegistry registry = registry();
        registry.claim("default", 0, 0, alice, "Alice");
        String text = Files.readString(dir.resolve("claims.json"));
        assertTrue(text.contains("\"Version\": 1"));
        assertTrue(text.contains("\"Trusted\": []"));
    }

    @Test
    void anUnreadableFileIsKeptAndTheRegistryStartsEmpty() throws Exception {
        Files.writeString(dir.resolve("claims.json"), "{ not json");
        ClaimRegistry registry = registry();
        registry.load();
        assertTrue(registry.all().isEmpty());
        assertTrue(Files.exists(dir.resolve("claims.json.broken")));
        assertFalse(Files.exists(dir.resolve("claims.json")));
    }

    @Test
    void renameKeepsTheClaim() {
        ClaimRegistry registry = registry();
        registry.claim("default", 0, 0, alice, "Alice");
        registry.rename(alice, "Alicia");
        assertEquals("Alicia", registry.at("default", 0, 0).ownerName());
    }
}
