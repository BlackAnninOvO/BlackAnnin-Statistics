package com.blackannin.statistics.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.stat.LocationKind;

/**
 * 数据目录解析。优先级（高 → 低）：
 * <ol>
 *   <li>配置项 dataDirectoryOverride 指定的绝对路径</li>
 *   <li>生产环境默认：固定路径 C:\Users\ASUS\AppData\Roaming\.minecraft\blackannins_statistics</li>
 *   <li>开发环境默认：项目 run/blackannins_statistics（避免测试数据污染真实 .minecraft）</li>
 *   <li>上述皆不可写时：回退到游戏运行目录（GAMEDIR）下的 blackannins_statistics</li>
 * </ol>
 * 目录创建在客户端入口初始化时执行一次，此后 {@link #root()} 仅读缓存，零开销。
 */
public final class StoragePaths {
    /** 用户指定的固定数据路径（发布版默认值；公开发布时可改为 GAMEDIR）。 */
    public static final String FIXED_DEFAULT_DIR =
            "C:\\Users\\ASUS\\AppData\\Roaming\\.minecraft\\blackannins_statistics";

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
            // 固定路径不可写（权限/盘符不存在等）：回退游戏运行目录并警告
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
        if (production) {
            return Paths.get(FIXED_DEFAULT_DIR);
        }
        return gameDir.resolve("blackannins_statistics"); // 开发环境 run/
    }

    public static StoragePaths get() {
        StoragePaths local = instance;
        if (local == null) {
            // init 未执行（理论不可达：客户端入口最先调用）——退回运行目录，保证不崩
            return instance = new StoragePaths(
                    net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("blackannins_statistics"), true);
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
