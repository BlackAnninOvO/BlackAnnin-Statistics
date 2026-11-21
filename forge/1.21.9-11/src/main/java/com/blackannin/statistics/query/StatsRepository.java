package com.blackannin.statistics.query;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.stat.LocationKind;
import com.blackannin.statistics.stat.LocationStore;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * UI 查询层：扫描数据目录、列出位置/档案、按范围聚合、删除。
 * 仅在 UI 打开或数据变更时扫描（文件量级：位置数 × 档案数，通常几十个以内）。
 */
public final class StatsRepository {

    /** 一个位置的概要（列表页展示用）。 */
    public record LocationSummary(LocationKind kind, String id, String displayName, String address,
                                  long lastSeen, int sessions, long playTimeTicks, long realPlayTimeMillis,
                                  boolean serverStatReset) {}

    /** 单个位置的完整数据。 */
    public record LocationData(LocationSummary summary, LocationStore store) {}

    private final StoragePaths paths = StoragePaths.get();

    /** 读取一个位置下所有档案的数据文件。null profileId = 全部档案。 */
    public List<LocationStore> loadLocationStores(LocationKind kind, String locationId, String profileId) {
        List<LocationStore> out = new ArrayList<>();
        Path dir = paths.locationDir(kind, locationId);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                if (profileId != null && !file.getFileName().toString().equals(profileId + ".json")) {
                    continue;
                }
                LocationStore store = SessionTracker.get().io().read(file);
                if (store != null) {
                    out.add(store);
                }
            }
        } catch (IOException e) {
            BlackAnninsStatistics.LOGGER.warn("读取位置目录失败: {}", dir, e);
        }
        return out;
    }

    /** 扫描某类别下全部位置概要（合并该位置所有档案的展示信息）。 */
    public List<LocationSummary> listLocations(LocationKind kind) {
        List<LocationSummary> out = new ArrayList<>();
        Path dataDir = paths.dataDir(kind);
        if (!Files.isDirectory(dataDir)) {
            return out;
        }
        try (DirectoryStream<Path> locationDirs = Files.newDirectoryStream(dataDir)) {
            for (Path dir : locationDirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                List<LocationStore> stores = loadLocationStores(kind, dir.getFileName().toString(), null);
                if (stores.isEmpty()) {
                    continue;
                }
                String displayName = stores.get(0).meta.displayName;
                String address = stores.get(0).meta.address;
                long lastSeen = 0;
                int sessions = 0;
                long playTime = 0;
                long realTime = 0;
                boolean reset = false;
                for (LocationStore s : stores) {
                    lastSeen = Math.max(lastSeen, s.meta.lastSeen);
                    sessions += s.meta.sessions;
                    playTime += s.playTimeTicks();
                    realTime += s.stats.get("blackannins_statistics:custom", "blackannins_statistics:real_play_time");
                    reset |= s.meta.serverStatReset;
                }
                out.add(new LocationSummary(kind, dir.getFileName().toString(), displayName, address,
                        lastSeen, sessions, playTime, realTime, reset));
            }
        } catch (IOException e) {
            BlackAnninsStatistics.LOGGER.warn("扫描数据目录失败: {}", dataDir, e);
        }
        out.sort(Comparator.comparingLong(LocationSummary::lastSeen).reversed());
        return out;
        // 注：displayName 用第一个文件的展示名；换名后以最新会话写入的文件为准
    }

    /** 聚合一个位置的指定档案数据（当前会话位置自动包含实时未落盘部分）。 */
    public StatSnapshot aggregateLocation(LocationKind kind, String locationId, String profileId) {
        List<StatSnapshot> parts = new ArrayList<>();
        var tracker = SessionTracker.get();
        boolean liveAdded = false;
        if (tracker.currentLocation() != null
                && tracker.currentLocation().kind() == kind
                && tracker.currentLocation().id().equals(locationId)) {
            var live = tracker.liveStoreForCurrentLocation();
            if (live != null && (profileId == null || live.meta.profileId.equals(profileId))) {
                parts.add(live.stats);
                liveAdded = true;
            }
        }
        String liveProfileId = liveAdded ? tracker.currentProfileId() : null;
        for (LocationStore store : loadLocationStores(kind, locationId, profileId)) {
            // live store 已包含磁盘上当前档案的累计值，跳过以免双重计数（其他档案照常累加）
            if (liveAdded && store.meta.profileId.equals(liveProfileId)) {
                continue;
            }
            parts.add(store.stats);
        }
        return StatSnapshot.sum(parts);
    }

    /** 聚合一个范围内（kind，或全部）指定档案的统计数据；当前会话位置会合并实时数据。 */
    public StatSnapshot aggregate(LocationKind kind /* null = 全部 */, String profileId /* null = 全部档案 */) {
        List<StatSnapshot> parts = new ArrayList<>();
        var tracker = SessionTracker.get();
        for (LocationKind k : kind == null ? LocationKind.values() : new LocationKind[]{kind}) {
            for (LocationSummary summary : listLocations(k)) {
                // 当前会话位置用实时数据（包含尚未落盘的部分）
                boolean liveAdded = false;
                if (tracker.currentLocation() != null
                        && tracker.currentLocation().kind() == k
                        && tracker.currentLocation().id().equals(summary.id())) {
                    var live = tracker.liveStoreForCurrentLocation();
                    if (live != null && (profileId == null || live.meta.profileId.equals(profileId))) {
                        parts.add(live.stats);
                        liveAdded = true;
                    }
                }
                String liveProfileId = liveAdded ? tracker.currentProfileId() : null;
                for (LocationStore store : loadLocationStores(k, summary.id(), profileId)) {
                    // live store 已含磁盘上当前档案的累计值，跳过以免双重计数
                    if (liveAdded && store.meta.profileId.equals(liveProfileId)) {
                        continue;
                    }
                    parts.add(store.stats);
                }
            }
        }
        return StatSnapshot.sum(parts);
    }

    /** 列出数据目录中出现过全部档案（UUID）。 */
    public List<String> listProfileIds() {
        List<String> ids = new ArrayList<>();
        for (LocationKind kind : LocationKind.values()) {
            Path dataDir = paths.dataDir(kind);
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (DirectoryStream<Path> locationDirs = Files.newDirectoryStream(dataDir)) {
                for (Path dir : locationDirs) {
                    if (!Files.isDirectory(dir)) {
                        continue;
                    }
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                        for (Path file : files) {
                            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
                            if (isValidUuid(name) && !ids.contains(name)) {
                                ids.add(name);
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return ids;
    }

    private static boolean isValidUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 删除一个位置的全部数据（管理页）。返回是否删除了内容。 */
    public boolean deleteLocation(LocationKind kind, String locationId) {
        Path dir = paths.locationDir(kind, locationId);
        return deleteRecursive(dir);
    }

    /** 删除某档案在某位置的数据。 */
    public boolean deleteProfileData(LocationKind kind, String locationId, String profileId) {
        return deleteRecursive(paths.storeFile(kind, locationId, profileId));
    }

    /** 清空全部数据（保留目录结构）。 */
    public void deleteAll() {
        for (LocationKind kind : LocationKind.values()) {
            deleteRecursive(paths.dataDir(kind));
        }
        deleteRecursive(paths.profilesFile());
    }

    private boolean deleteRecursive(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    BlackAnninsStatistics.LOGGER.warn("删除失败: {}", p, e);
                }
            });
            return true;
        } catch (IOException e) {
            BlackAnninsStatistics.LOGGER.warn("递归删除失败: {}", path, e);
            return false;
        }
    }
}
