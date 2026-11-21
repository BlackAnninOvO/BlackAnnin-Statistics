package com.blackannin.statistics.stat;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatSnapshotTest {

    @Test
    void putIgnoresZeroAndNegative() {
        StatSnapshot s = new StatSnapshot();
        s.put("minecraft:mined", "minecraft:stone", 0);
        s.put("minecraft:mined", "minecraft:dirt", -5);
        s.put("minecraft:mined", "minecraft:stone", 3);
        assertEquals(1, s.entryCount());
        assertEquals(3, s.get("minecraft:mined", "minecraft:stone"));
        assertEquals(0, s.get("minecraft:mined", "minecraft:dirt"));
    }

    @Test
    void sumAggregatesAcrossLocations() {
        StatSnapshot a = new StatSnapshot();
        a.put("minecraft:custom", "minecraft:play_time", 1000);
        a.put("minecraft:mined", "minecraft:stone", 10);
        StatSnapshot b = new StatSnapshot();
        b.put("minecraft:custom", "minecraft:play_time", 250);
        b.put("minecraft:mined", "minecraft:stone", 7);

        StatSnapshot total = StatSnapshot.sum(List.of(a, b));
        assertEquals(1250, total.get("minecraft:custom", "minecraft:play_time"));
        assertEquals(17, total.get("minecraft:mined", "minecraft:stone"));
    }

    @Test
    void mergeMaxNeverDecreasesEvenWhenServerResets() {
        // 服务器重置统计后，新快照可能小于本地历史 —— 合并必须保留历史
        LocationStore store = newStore("mp", 100, 50);
        StatSnapshot afterReset = new StatSnapshot();
        afterReset.put("minecraft:custom", "minecraft:play_time", 3);

        store.mergeSnapshot(afterReset, 200L);

        assertEquals(100, store.stats.get("minecraft:custom", "minecraft:play_time"));
        assertEquals(50, store.stats.get("minecraft:mined", "minecraft:stone"));
    }

    @Test
    void mergeMaxTakesLargerValue() {
        LocationStore store = newStore("sp", 100, 50);
        StatSnapshot fresh = new StatSnapshot();
        fresh.put("minecraft:custom", "minecraft:play_time", 140);
        fresh.put("minecraft:mined", "minecraft:stone", 10);

        store.mergeSnapshot(fresh, 200L);

        assertEquals(140, store.stats.get("minecraft:custom", "minecraft:play_time"));
        assertEquals(50, store.stats.get("minecraft:mined", "minecraft:stone")); // 取最大而非覆盖
    }

    @Test
    void copyIsDeep() {
        StatSnapshot s = new StatSnapshot();
        s.put("minecraft:custom", "minecraft:jump", 5);
        StatSnapshot copy = s.copy();
        copy.put("minecraft:custom", "minecraft:jump", 99);
        assertEquals(5, s.get("minecraft:custom", "minecraft:jump"));
    }

    @Test
    void emptySnapshotIsEmpty() {
        assertTrue(new StatSnapshot().isEmpty());
        assertTrue(StatSnapshot.sum(List.of()).isEmpty());
    }

    private static LocationStore newStore(String kind, long playTime, long stoneMined) {
        StoreMeta meta = new StoreMeta();
        meta.kind = kind;
        meta.locationId = "w1";
        meta.displayName = "World 1";
        meta.profileId = "uuid-1";
        StatSnapshot stats = new StatSnapshot();
        stats.put("minecraft:custom", "minecraft:play_time", playTime);
        stats.put("minecraft:mined", "minecraft:stone", stoneMined);
        return new LocationStore(meta, stats);
    }
}
