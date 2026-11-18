package com.blackannin.statistics.stat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 一次采集到的原版统计数据快照：统计类型 id → 目标 id → 累计值。
 * 键形如 ("minecraft:mined", "minecraft:stone")，值使用 long 以便跨存档/服务器求和。
 *
 * <p>本类位于可移植核心层，禁止引用任何 Minecraft / NeoForge 类。</p>
 */
public final class StatSnapshot {
    /** typeId → (targetId → value)，使用 TreeMap 保证 JSON 输出稳定、便于玩家手工编辑阅读。 */
    private final Map<String, Map<String, Long>> values;

    public StatSnapshot() {
        this.values = new TreeMap<>();
    }

    public StatSnapshot(Map<String, Map<String, Long>> values) {
        // 拷贝入参，杜绝外部可变引用
        this.values = new TreeMap<>();
        values.forEach((type, targets) -> this.values.put(type, new TreeMap<>(targets)));
    }

    public void put(String typeId, String targetId, long value) {
        if (value <= 0) {
            return; // 原版计数以 0 为无数据，不落盘，保持文件干净
        }
        values.computeIfAbsent(typeId, k -> new TreeMap<>()).put(targetId, value);
    }

    public long get(String typeId, String targetId) {
        Map<String, Long> targets = values.get(typeId);
        return targets == null ? 0L : targets.getOrDefault(targetId, 0L);
    }

    public Map<String, Long> getType(String typeId) {
        return values.getOrDefault(typeId, Map.of());
    }

    public Set<String> typeIds() {
        return values.keySet();
    }

    public int entryCount() {
        return values.values().stream().mapToInt(Map::size).sum();
    }

    public boolean isEmpty() {
        return values.values().stream().allMatch(Map::isEmpty);
    }

    /** 逐项差值（当前 - 基线，负值按 0 处理），用于"本次游玩"会话统计。 */
    public StatSnapshot minus(StatSnapshot baseline) {
        StatSnapshot out = new StatSnapshot();
        values.forEach((type, targets) -> targets.forEach((target, value) -> {
            long delta = value - baseline.get(type, target);
            if (delta > 0) {
                out.put(type, target, delta);
            }
        }));
        return out;
    }

    /** 该统计类型下所有目标值之和（如"全部破坏方块数"）。 */
    public long typeSum(String typeId) {
        long sum = 0;
        for (long v : getType(typeId).values()) {
            sum += v;
        }
        return sum;
    }

    /** 快照逐项求和，用于全局 / 分类聚合。 */
    public static StatSnapshot sum(Iterable<StatSnapshot> snapshots) {
        Map<String, Map<String, Long>> out = new HashMap<>();
        for (StatSnapshot snapshot : snapshots) {
            snapshot.values.forEach((type, targets) -> targets.forEach((target, value) ->
                    out.computeIfAbsent(type, k -> new HashMap<>())
                       .merge(target, value, Long::sum)));
        }
        return new StatSnapshot(out);
    }

    /**
     * 逐项取最大值，用于"累计快照合并进存档"：合并时历史只增不减，
     * 因此服务器重置统计后本地仍保留历史最大值（重置事件由上层标记到 meta）。
     */
    public void mergeMax(StatSnapshot newer) {
        newer.values.forEach((type, targets) -> targets.forEach((target, value) -> {
            long old = get(type, target);
            if (value > old) {
                put(type, target, value);
            }
        }));
    }

    /** 内部视图（含可变子 map），仅供同包存储层序列化使用。 */
    Map<String, Map<String, Long>> rawView() {
        return values;
    }

    /** 深拷贝。 */
    public StatSnapshot copy() {
        return new StatSnapshot(values);
    }

    /** 存储层从反序列化的原始 map 重建快照（构造器会再拷贝一份）。 */
    public static StatSnapshot ofRaw(Map<String, Map<String, Long>> raw) {
        return new StatSnapshot(raw);
    }

    public LinkedHashMap<String, Map<String, Long>> deepCopyOrdered() {
        LinkedHashMap<String, Map<String, Long>> out = new LinkedHashMap<>();
        values.forEach((type, targets) -> out.put(type, new TreeMap<>(targets)));
        return out;
    }
}
