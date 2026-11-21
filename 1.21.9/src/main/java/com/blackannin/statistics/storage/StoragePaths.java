package com.blackannin.statistics.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import net.fabricmc.loader.api.FabricLoader;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.stat.LocationKind;

/**
 * 数据目录解析。优先级（高 → 低）：
 * <ol>
 *   <li>配置项 dataDirectoryOverride 指定的绝对路径</li>
 *   <li>默认：游戏运行目录下的 blackannins_statistics
 *       （生产环境即 .minecraft\blackannins_statistics，与 NeoForge 版行为一致）</li>
 *   <li>上述不可写时：同样回退到游戏运行目录（GAMEDIR）下的 blackannins_statistics</li>
 * </ol>
 * 与 NeoForge 版的差异：主版本生产默认值是写死的固定路径（含用户名）；
 * Fabric 版统一使用 FabricLoader 的游戏目录（标准启动器下两者指向同一位置），
 * 行为等价且不内嵌用户名。目录创建在客户端入口初始化时执行一次，
 * 此后 {@link #root()} 仅读缓存，零开销。
 */
public final class StoragePaths {

    private static volatile StoragePaths instance;

    private final Path root;
    private final boolean fallbackInUse;

    private StoragePaths(Path root, boolean fallbackInUse) {
        this.root = root;
        this.fallbackInUse = fallbackInUse;
    }

    /** 在客户端入口调用：解析并创建目录。override 可为空串表示使用默认。 */
    public static void init(String override, boolean production, Path gameDir) {
        Path preferred = resolvePreferred(override, production, gameDir);
        Path chosen = preferred;
        boolean fallback = false;
        try {
            Files.createDirectories(preferred.resolve(LocationKind.SINGLEPLAYER.dirName()));
            Files.createDirectories(preferred.resolve(LocationKind.MULTIPLAYER.dirName()));
        } catch (IOException | SecurityException e) {
            // 首选路径不可写（权限/盘符不存在等）：回退游戏运行目录并警告
            fallback = true;
            chosen = gameDir.resolve("blackannins_statistics");
            try {
                Files.createDirectories(chosen.resolve(LocationKind.SINGLEPLAYER.dirName()));
                Files.createDirectories(chosen.resolve(LocationKind.MULTIPLAYER.dirName()));
            } catch (IOException | SecurityException e2) {
                BlackAnninsStatistics.LOGGER.error("统计数据目录创建失败，本会话数据将不会被保存", e2);
            }
        }
        instance = new StoragePaths(chosen, fallback);
        if (fallback) {
            BlackAnninsStatistics.LOGGER.warn("首选数据目录 {} 不可用，已回退至 {}", preferred, chosen);
        } else {
            BlackAnninsStatistics.LOGGER.info("统计数据目录: {}", chosen);
        }
    }

    private static Path resolvePreferred(String override, boolean production, Path gameDir) {
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        // 生产与开发环境统一使用游戏运行目录（开发环境 gameDir 为项目 run/）
        return gameDir.resolve("blackannins_statistics");
    }

    public static StoragePaths get() {
        StoragePaths local = instance;
        if (local == null) {
            // init 未执行（理论不可达：客户端入口最先调用）——退回运行目录，保证不崩
            return instance = new StoragePaths(
                    FabricLoader.getInstance().getGameDir().resolve("blackannins_statistics"), true);
        }
        return local;
    }

    public Path root() {
        return root;
    }

    public Path backupsDir() {
        return root.resolve("backups");
    }

    public Path dataDir(LocationKind kind) {
        return root.resolve("data").resolve(kind.dirName());
    }

    public Path locationDir(LocationKind kind, String locationId) {
        return dataDir(kind).resolve(locationId);
    }

    public Path storeFile(LocationKind kind, String locationId, String profileId) {
        return locationDir(kind, locationId).resolve(profileId + ".json");
    }

    public Path profilesFile() {
        return root.resolve("profiles.json");
    }

    /** 是否因首选路径不可用而回退（UI 管理页需要如实展示当前实际路径）。 */
    public boolean isFallbackInUse() {
        return fallbackInUse;
    }
}
