package com.blackannin.statistics.session;

import net.minecraft.client.Minecraft;
import net.minecraft.stats.StatsCounter;

import com.blackannin.statistics.stat.StatSnapshot;

/**
 * "本次游玩"会话统计：以进入世界为起点，统计本次会话内的各项增量。
 * 基线取自会话开始 5 秒后的原版统计计数器（等待服务器把全量统计同步到客户端），
 * 因此关闭客户端重开后会话归零；跨存档/服务器重新进入世界同样重置。
 * 只读展示，不影响主体的累计数据。
 */
public final class SessionStats {
    /** 等待服务器全量统计同步的基线延迟。 */
    private static final long BASELINE_DELAY_MILLIS = 5000;

    private long startWallMillis;
    private boolean active;
    private StatSnapshot baseline;
    private static final long DELTA_CACHE_MILLIS = 500;
    private StatSnapshot cachedDelta;
    private long lastDeltaMillis;
    private java.util.Map<String, Long> cachedTypeTotals = java.util.Map.of();

    /** 进入世界（会话开始）。 */
    public void begin(long nowMillis) {
        startWallMillis = nowMillis;
        active = true;
        baseline = null;
        cachedDelta = null;
    }

    /** 离开世界（会话结束，小窗归零）。 */
    public void end() {
        active = false;
        baseline = null;
        cachedDelta = null;
        cachedTypeTotals = java.util.Map.of();
    }

    public boolean isActive() {
        return active;
    }

    /** 会话开始 5 秒后固定原版统计基线。 */
    public void ensureBaseline(long nowMillis) {
        if (!active || baseline != null) {
            return;
        }
        if (nowMillis - startWallMillis < BASELINE_DELAY_MILLIS) {
            return;
        }
        StatsCounter counter = currentCounter();
        if (counter != null) {
            baseline = ClientStatsBridge.readCounter(counter);
        }
    }

    public long realMillis(long nowMillis) {
        return active ? Math.max(0, nowMillis - startWallMillis) : 0;
    }

    /** 增量快照中某统计类型的合计（走 500ms 缓存）。 */
    public long vanillaDeltaSum(String typeId) {
        vanillaDelta();
        return cachedTypeTotals.getOrDefault(typeId, 0L);
    }

    /** 本次会话的原版统计增量（基线未就绪时返回空快照）。
     *  结果缓存 500ms：读增量需遍历整个注册表，HUD 每帧调用不能全速重算。 */
    public StatSnapshot vanillaDelta() {
        long now = System.currentTimeMillis();
        if (!active || baseline == null) {
            return new StatSnapshot();
        }
        if (cachedDelta != null && now - lastDeltaMillis < DELTA_CACHE_MILLIS) {
            return cachedDelta;
        }
        StatsCounter counter = currentCounter();
        if (counter == null) {
            return new StatSnapshot();
        }
        cachedDelta = ClientStatsBridge.readCounter(counter).minus(baseline);
        // 顺带缓存各类型合计（HUD 破坏/拾取/合成行使用），避免每次渲染重复遍历
        java.util.Map<String, Long> totals = new java.util.HashMap<>();
        for (String type : cachedDelta.typeIds()) {
            totals.put(type, cachedDelta.typeSum(type));
        }
        cachedTypeTotals = java.util.Collections.unmodifiableMap(totals);
        lastDeltaMillis = now;
        return cachedDelta;
    }

    private static StatsCounter currentCounter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        return ClientStatsBridge.isInSingleplayer()
                ? ClientStatsBridge.singleplayerCounter()
                : mc.player.getStats();
    }
}
