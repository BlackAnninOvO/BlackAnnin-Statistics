package com.blackannin.statistics.session;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.stats.StatsCounter;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.stat.LocationStore;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.stat.StoreMeta;
import com.blackannin.statistics.storage.ProfileIndex;
import com.blackannin.statistics.storage.StoragePaths;
import com.blackannin.statistics.storage.StoreIO;

/**
 * 会话跟踪器：识别当前位置与档案，周期性把原版统计累计快照合并落盘，全程无感。
 *
 * <p>稳态成本：每个保存周期（默认 60s）一次注册表遍历读内存计数器（毫秒级）+
 * 一次异步小文件写；多人另有周期 30s 的一封原版统计请求封包。除此之外零开销。</p>
 */
public final class SessionTracker {
    /** 本地扩展统计类型：方块放置数（客户端确认式采集，单人/多人通用）。 */
    public static final String PLACED_TYPE = "blackannins_statistics:placed";
    /** 本地扩展统计类型：自定义指标。 */
    public static final String CUSTOM_TYPE = "blackannins_statistics:custom";
    /** 现实游玩时长（毫秒）：进入世界到离开的真实墙钟时间，不受游戏卡顿/暂停影响。 */
    public static final String REAL_TIME_KEY = "blackannins_statistics:real_play_time";
    /** 原版统计类型：方块破坏数（生存/冒险由原版统计负责；创造模式下原版不授予该统计，
     *  由客户端确认式采集补充，见 ClientEvents 破坏采集。两种来源共用同一键位与基线+增量公式）。 */
    public static final String MINED_TYPE = "minecraft:mined";

    private static final SessionTracker INSTANCE = new SessionTracker();

    public static SessionTracker get() {
        return INSTANCE;
    }

    private final StoreIO io = new StoreIO();

    private ProfileIndex profileIndex;
    private PlayLocation location;
    private LocationStore activeStore;
    private boolean sessionCounted;

    /** 本次会话确认的放置增量（方块 id → 次数），供落盘合成与 HUD/状态明细使用。 */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> sessionPlaced =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 会话开始时从磁盘载入的放置基线（落盘值恒为 基线+会话增量，杜绝任何形式的重复叠加）。 */
    private java.util.Map<String, Long> loadedPlaced;

    /** 创造模式会话确认的破坏增量（方块 id → 次数）：原版统计在创造下不计数，由客户端确认采集补充。 */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> sessionMined =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 会话开始时从磁盘载入的破坏基线（落盘值恒为 基线+会话增量；生存/冒险下增量恒为空，由原版计数器读数负责）。 */
    private java.util.Map<String, Long> loadedMinedBaseline;

    /** 现实游玩时长：会话起点与上次并入快照的时刻（毫秒）。 */
    private volatile long lastRealTimeFlushMillis;

    /** "本次游玩"会话统计（HUD 小窗数据源，只读不影响主计数）。 */
    private final SessionStats sessionStats = new SessionStats();

    private long lastFlushMillis;
    private long lastSyncMillis;
    private boolean syncRequestedOnce;

    private SessionTracker() {}

    public StoreIO io() {
        return io;
    }

    /** 每个客户端 tick 调用（事件桥接入）。 */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // 离开世界的兜底（正常路径由 onLeave 处理）
            if (activeStore != null) {
                endSession();
            }
            return;
        }

        if (location == null) {
            beginSession();
            if (location == null) {
                return; // 位置暂不可解析，下个 tick 再试
            }
        }

        long now = System.currentTimeMillis();
        sessionStats.ensureBaseline(now);
        boolean multiplayer = location.kind() == com.blackannin.statistics.stat.LocationKind.MULTIPLAYER;

        if (multiplayer) {
            if (!syncRequestedOnce || now - lastSyncMillis >= BasClientConfig.syncIntervalMillis()) {
                syncRequestedOnce = true;
                lastSyncMillis = now;
                ClientStatsBridge.requestStatsSync();
            }
        }

        if (now - lastFlushMillis >= BasClientConfig.saveIntervalMillis()) {
            lastFlushMillis = now;
            captureAndFlush();
        }
    }

    private void beginSession() {
        Minecraft mc = Minecraft.getInstance();
        try {
            location = ClientStatsBridge.resolveLocation();
        } catch (Exception e) {
            BlackAnninsStatistics.LOGGER.warn("解析当前位置失败，暂停统计跟踪", e);
            return;
        }
        if (location == null) {
            return;
        }

        String uuid = mc.player.getUUID().toString();
        String name = mc.getUser().getName();

        Path file = StoragePaths.get().storeFile(location.kind(), location.id(), uuid);
        LocationStore store = io.read(file);
        if (store == null) {
            StoreMeta meta = new StoreMeta();
            meta.kind = location.kind().dirName();
            meta.locationId = location.id();
            meta.displayName = location.displayName();
            meta.address = location.address();
            meta.profileId = uuid;
            store = new LocationStore(meta, new StatSnapshot());
        }
        // 换存档/换服后 meta 展示信息以最新为准
        store.meta.displayName = location.displayName();
        store.meta.address = location.address();
        store.meta.addName(name);

        activeStore = store;
        sessionCounted = false;
        lastFlushMillis = System.currentTimeMillis();
        lastRealTimeFlushMillis = lastFlushMillis;
        syncRequestedOnce = false;
        // 放置计数的落盘基线：固定为载入时的磁盘值，会话增量恒等叠加不会重复
        loadedPlaced = new java.util.HashMap<>(store.stats.getType(PLACED_TYPE));
        sessionPlaced.clear();
        // 破坏计数的落盘基线：同款公式（仅创造模式增量生效，生存/冒险由原版计数器读数负责）
        loadedMinedBaseline = new java.util.HashMap<>(store.stats.getType(MINED_TYPE));
        sessionMined.clear();
        sessionStats.begin(System.currentTimeMillis());

        if (profileIndex == null) {
            profileIndex = ProfileIndex.load(StoragePaths.get().profilesFile());
        }
        profileIndex.touch(uuid, name, System.currentTimeMillis(), location.locationKey());
        persistProfileIndex();

        BlackAnninsStatistics.LOGGER.debug("统计会话开始: {} ({})", location.displayName(), location.locationKey());
    }

    /** 采集一次累计快照并异步落盘（周期保存 / 退出前都会调用）。 */
    public void captureAndFlush() {
        if (activeStore == null || location == null || Minecraft.getInstance().player == null) {
            return;
        }
        StatSnapshot fresh = readCurrentCounter();
        if (fresh == null) {
            return;
        }
        long now = System.currentTimeMillis();

        if (!sessionCounted) {
            sessionCounted = true;
            activeStore.meta.sessions++;
        }

        // 方块放置：落盘值恒为"会话开始时的磁盘基线 + 本次会话确认增量"，
        // 与累计内存值解耦，任何捕获次数下都不会重复叠加
        if (!sessionPlaced.isEmpty() && loadedPlaced != null) {
            sessionPlaced.forEach((blockId, sessionCount) ->
                    fresh.put(PLACED_TYPE, blockId,
                            loadedPlaced.getOrDefault(blockId, 0L) + sessionCount));
        }

        // 方块破坏（创造模式增量）：与放置同款"磁盘基线 + 会话增量"叠加。
        // 生存/冒险下本会话增量为空，破坏值完全由原版计数器读数（fresh）负责；
        // 若同一方块两种来源并存，mergeMax 取历史最大，不会回退。
        if (!sessionMined.isEmpty() && loadedMinedBaseline != null) {
            sessionMined.forEach((blockId, sessionCount) ->
                    fresh.put(MINED_TYPE, blockId,
                            loadedMinedBaseline.getOrDefault(blockId, 0L) + sessionCount));
        }

        // 现实游玩时长：并入自上次落盘以来的墙钟增量
        if (lastRealTimeFlushMillis > 0) {
            long delta = Math.max(0, now - lastRealTimeFlushMillis);
            lastRealTimeFlushMillis = now;
            fresh.put(CUSTOM_TYPE, REAL_TIME_KEY,
                    activeStore.stats.get(CUSTOM_TYPE, REAL_TIME_KEY) + delta);
        }

        detectServerReset(fresh);
        activeStore.mergeSnapshot(fresh, now);
        activeStore.meta.addName(Minecraft.getInstance().getUser().getName());

        Path file = StoragePaths.get().storeFile(location.kind(), location.id(), activeStore.meta.profileId);
        // 落盘前深拷贝：确认线程（主线程）会继续修改 activeStore.stats，
        // 若把引用交给 IO 线程遍历会引发并发修改异常与数据竞争
        LocationStore snapshot = new LocationStore(copyMeta(activeStore.meta), copySnapshot(activeStore.stats));
        io.writeAsync(file, snapshot);
        profileIndex.touch(activeStore.meta.profileId, Minecraft.getInstance().getUser().getName(), now, location.locationKey());
        persistProfileIndex();
    }

    /** 读取当前统计计数器：单人直读集成服务器；多人读客户端计数器（内容来自服务器原版同步）。 */
    private StatSnapshot readCurrentCounter() {
        StatsCounter counter = ClientStatsBridge.isInSingleplayer()
                ? ClientStatsBridge.singleplayerCounter()
                : Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getStats() : null;
        return counter == null ? null : ClientStatsBridge.readCounter(counter);
    }

    /** 服务器统计被重置检测：任一项来源值小于本地历史值（且不为 0）即判定。 */
    private void detectServerReset(StatSnapshot fresh) {
        if (activeStore.meta.serverStatReset) {
            return; // 已标记过
        }
        boolean decreased = fresh.typeIds().stream().anyMatch(type ->
                fresh.getType(type).entrySet().stream().anyMatch(e ->
                        e.getValue() > 0 && e.getValue() < activeStore.stats.get(type, e.getKey())));
        if (decreased) {
            activeStore.meta.serverStatReset = true;
            BlackAnninsStatistics.LOGGER.info("检测到 {} 的服务器统计被重置，本地保留历史最大值",
                    activeStore.meta.displayName);
        }
    }

    /** 离开世界 / 断开服务器：立即采集并等待落盘（≤2s），保证"游玩结束后自动保存"。 */
    public void onLeave() {
        if (activeStore != null) {
            captureAndFlush();
            endSession();
            io.flushAndWait(2);
        }
    }

    private void endSession() {
        BlackAnninsStatistics.LOGGER.debug("统计会话结束: {}", location == null ? "?" : location.displayName());
        location = null;
        activeStore = null;
        sessionCounted = false;
        syncRequestedOnce = false;
        sessionPlaced.clear();
        loadedPlaced = null;
        sessionMined.clear();
        loadedMinedBaseline = null;
        lastRealTimeFlushMillis = 0L;
        sessionStats.end();
    }

    /** 方块放置事件入口（客户端确认式采集，事件在客户端 tick 线程触发）。
     *  确认即直接累加进累计值：放在内存累计里再由周期捕获合并会导致同一份增量被反复叠加。 */
    public void onBlockPlaced(String blockId) {
        if (activeStore != null) {
            sessionPlaced.merge(blockId, 1L, Long::sum);
            BlackAnninsStatistics.LOGGER.info("[BAS] 放置已计数: {} (本次会话 {})",
                    blockId, sessionPlaced.get(blockId));
        } else {
            BlackAnninsStatistics.LOGGER.warn("[BAS] 放置计数被丢弃（无活动会话）: {}", blockId);
        }
    }

    /** 方块破坏事件入口（客户端确认式采集，仅创造模式启用；事件在客户端 tick 线程触发）。
     *  生存/冒险下事件桥不会调用本入口（原版统计负责，避免双计）。 */
    public void onBlockMined(String blockId) {
        if (activeStore != null) {
            sessionMined.merge(blockId, 1L, Long::sum);
            BlackAnninsStatistics.LOGGER.info("[BAS] 破坏已计数: {} (本次会话 {})",
                    blockId, sessionMined.get(blockId));
        } else {
            BlackAnninsStatistics.LOGGER.warn("[BAS] 破坏计数被丢弃（无活动会话）: {}", blockId);
        }
    }

    /** 当前会话档案 UUID（无会话为 null）。 */
    public String currentProfileId() {
        return activeStore == null ? null : activeStore.meta.profileId;
    }

    /** 会话内放置计数明细（方块 id → 本会话确认次数），供 /bas status 核验。 */
    public java.util.Map<String, Long> sessionPlacedView() {
        return new java.util.TreeMap<>(sessionPlaced);
    }

    /** 本次会话放置总数（HUD 小窗用）。 */
    public long sessionPlacedTotal() {
        long total = 0;
        for (long v : sessionPlaced.values()) {
            total += v;
        }
        return total;
    }

    /** 会话内破坏计数明细（创造模式确认增量），供 /bas status 核验。 */
    public java.util.Map<String, Long> sessionMinedView() {
        return new java.util.TreeMap<>(sessionMined);
    }

    /** 本次会话破坏总数（创造模式增量；生存/冒险下恒为 0，HUD 小窗叠加在原版增量之上）。 */
    public long sessionMinedTotal() {
        long total = 0;
        for (long v : sessionMined.values()) {
            total += v;
        }
        return total;
    }

    /** "本次游玩"会话统计（HUD 小窗数据源）。 */
    public SessionStats sessionStats() {
        return sessionStats;
    }

    /** 游戏退出（客户端停止事件）：最后兜底一次。 */
    public void onShutdown() {
        if (activeStore != null) {
            captureAndFlush();
        }
        io.flushAndWait(3);
    }

    private void persistProfileIndex() {
        // 传深拷贝给 IO 线程：主线程的 touch/remove 会并发修改 entries
        ProfileIndex snapshot = profileIndex.copy();
        Path file = StoragePaths.get().profilesFile();
        io.runAsync(() -> snapshot.save(file));
    }

    // ---- UI 查询辅助 ----

    /** 当前会话位置（无会话时为 null）。 */
    public PlayLocation currentLocation() {
        return location;
    }

    /**
     * 当前位置的实时数据 = 已存储累计 与 内存计数器实时读数 的逐项最大值，
     * 让刚发生的行为不必等下个保存周期就能在 UI 看到。
     */
    public LocationStore liveStoreForCurrentLocation() {
        if (activeStore == null) {
            return null;
        }
        StatSnapshot fresh = readCurrentCounter();
        LocationStore live = new LocationStore(copyMeta(activeStore.meta), copySnapshot(activeStore.stats));
        if (fresh != null) {
            live.mergeSnapshot(fresh, System.currentTimeMillis());
        }
        // 放置计数实时合成：磁盘基线 + 会话确认增量（与落盘同公式，无叠加）
        if (loadedPlaced != null) {
            java.util.Set<String> ids = new java.util.HashSet<>(loadedPlaced.keySet());
            ids.addAll(sessionPlaced.keySet());
            for (String id : ids) {
                long v = loadedPlaced.getOrDefault(id, 0L) + sessionPlaced.getOrDefault(id, 0L);
                if (v > 0) {
                    live.stats.put(PLACED_TYPE, id, v);
                }
            }
        }
        // 破坏计数实时合成（创造模式）：磁盘基线 + 会话确认增量。
        // 取 max 与原版计数器读数比较：生存期原版历史值可能更高，避免实时视图回退。
        if (loadedMinedBaseline != null) {
            java.util.Set<String> minedIds = new java.util.HashSet<>(loadedMinedBaseline.keySet());
            minedIds.addAll(sessionMined.keySet());
            for (String id : minedIds) {
                long v = loadedMinedBaseline.getOrDefault(id, 0L) + sessionMined.getOrDefault(id, 0L);
                long vanilla = live.stats.get(MINED_TYPE, id);
                if (v > vanilla) {
                    live.stats.put(MINED_TYPE, id, v);
                }
            }
        }
        // 现实时长则按"存量 + 距上次落盘的墙钟增量"实时展示（增量消费后已重置基准，无重复）
        if (lastRealTimeFlushMillis > 0) {
            long delta = Math.max(0, System.currentTimeMillis() - lastRealTimeFlushMillis);
            live.stats.put(CUSTOM_TYPE, REAL_TIME_KEY,
                    activeStore.stats.get(CUSTOM_TYPE, REAL_TIME_KEY) + delta);
        }
        return live;
    }

    private static StoreMeta copyMeta(StoreMeta src) {
        StoreMeta m = new StoreMeta();
        m.format = src.format;
        m.kind = src.kind;
        m.locationId = src.locationId;
        m.displayName = src.displayName;
        m.address = src.address;
        m.profileId = src.profileId;
        src.namesList().forEach(m::addName);
        m.firstSeen = src.firstSeen;
        m.lastSeen = src.lastSeen;
        m.sessions = src.sessions;
        m.serverStatReset = src.serverStatReset;
        return m;
    }

    private static StatSnapshot copySnapshot(StatSnapshot src) {
        return src.copy();
    }
}
