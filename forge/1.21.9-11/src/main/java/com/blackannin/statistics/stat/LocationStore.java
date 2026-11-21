package com.blackannin.statistics.stat;

/**
 * 一个"位置 × 档案"的完整存储单元：元信息 + 累计统计快照。
 * 文件内 JSON 结构：
 * <pre>
 * {
 *   "meta": { ...StoreMeta 字段... },
 *   "stats": { "minecraft:custom": { "minecraft:play_time": 123 }, ... }
 * }
 * </pre>
 */
public final class LocationStore {
    public final StoreMeta meta;
    public final StatSnapshot stats;

    public LocationStore(StoreMeta meta, StatSnapshot stats) {
        this.meta = meta;
        this.stats = stats;
    }

    /** 将新采集的累计快照合并进来（逐项取最大，历史只增不减），并刷新 meta 时间戳。 */
    public void mergeSnapshot(StatSnapshot newer, long nowMillis) {
        stats.mergeMax(newer);
        if (meta.firstSeen <= 0) {
            meta.firstSeen = nowMillis;
        }
        meta.lastSeen = nowMillis;
    }

    public long playTimeTicks() {
        return stats.get("minecraft:custom", "minecraft:play_time");
    }
}
