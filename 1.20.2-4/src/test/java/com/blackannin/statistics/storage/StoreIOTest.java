package com.blackannin.statistics.storage;

import java.util.Map;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import com.blackannin.statistics.stat.LocationStore;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.stat.StoreMeta;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoreIOTest {

    @Test
    void jsonRoundTripPreservesEverything() {
        StoreIO io = new StoreIO();
        LocationStore store = sample();
        JsonObject json = io.toJson(store);
        LocationStore parsed = io.fromJson(json);

        assertEquals(store.meta.kind, parsed.meta.kind);
        assertEquals(store.meta.locationId, parsed.meta.locationId);
        assertEquals(store.meta.displayName, parsed.meta.displayName);
        assertEquals(store.meta.address, parsed.meta.address);
        assertEquals(store.meta.profileId, parsed.meta.profileId);
        assertEquals(store.meta.namesList(), parsed.meta.namesList());
        assertEquals(store.meta.firstSeen, parsed.meta.firstSeen);
        assertEquals(store.meta.lastSeen, parsed.meta.lastSeen);
        assertEquals(store.meta.sessions, parsed.meta.sessions);
        assertEquals(store.meta.serverStatReset, parsed.meta.serverStatReset);

        assertEquals(store.stats.get("minecraft:custom", "minecraft:play_time"),
                parsed.stats.get("minecraft:custom", "minecraft:play_time"));
        assertEquals(store.stats.get("minecraft:mined", "minecraft:stone"),
                parsed.stats.get("minecraft:mined", "minecraft:stone"));
        assertEquals(store.playTimeTicks(), parsed.playTimeTicks());
    }

    /** 玩家手工编辑场景：缺失可选字段时能安全回退默认值。 */
    @Test
    void parsesMinimalJson() {
        String json = """
                {
                  "meta": {
                    "kind": "singleplayer",
                    "locationId": "NewWorld",
                    "profileId": "069a79f4-44e9-4726-a5be-fca90e38aaf5"
                  },
                  "stats": {
                    "minecraft:custom": { "minecraft:play_time": 42 }
                  }
                }
                """;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        StoreIO io = new StoreIO();
        LocationStore parsed = io.fromJson(gson.fromJson(json, JsonObject.class));

        assertEquals("singleplayer", parsed.meta.kind);
        assertEquals("NewWorld", parsed.meta.displayName); // 缺 displayName 时回退 locationId
        assertEquals(42, parsed.playTimeTicks());
        assertEquals(0, parsed.meta.sessions);
    }

    private static LocationStore sample() {
        StoreMeta meta = new StoreMeta();
        meta.kind = "multiplayer";
        meta.locationId = "play.example.net_25565";
        meta.displayName = "Example Server";
        meta.address = "play.example.net:25565";
        meta.profileId = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        meta.addName("Steve");
        meta.addName("Steve_");
        meta.firstSeen = 1000L;
        meta.lastSeen = 2000L;
        meta.sessions = 7;
        meta.serverStatReset = true;
        StatSnapshot stats = new StatSnapshot(Map.of(
                "minecraft:custom", Map.of("minecraft:play_time", 123456L),
                "minecraft:mined", Map.of("minecraft:stone", 10L)));
        return new LocationStore(meta, stats);
    }
}
