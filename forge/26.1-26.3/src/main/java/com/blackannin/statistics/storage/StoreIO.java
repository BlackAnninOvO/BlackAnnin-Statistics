package com.blackannin.statistics.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.stat.LocationStore;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.stat.StoreMeta;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

/**
 * 数据文件读写：人可读 JSON + 异步原子写 + 损坏文件隔离备份。
 * 全部文件操作都限制在 {@link StoragePaths#root()} 之下；游戏主线程永不阻塞在磁盘 IO 上。
 */
public final class StoreIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BACKUP_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "blackannins-statistics-io");
        t.setDaemon(true);
        return t;
    });

    // ---- 序列化 ----

    public JsonObject toJson(LocationStore store) {
        JsonObject root = new JsonObject();

        JsonObject meta = new JsonObject();
        meta.addProperty("format", store.meta.format);
        meta.addProperty("kind", store.meta.kind);
        meta.addProperty("locationId", store.meta.locationId);
        meta.addProperty("displayName", store.meta.displayName);
        if (store.meta.address != null) {
            meta.addProperty("address", store.meta.address);
        }
        meta.addProperty("profileId", store.meta.profileId);
        com.google.gson.JsonArray names = new com.google.gson.JsonArray();
        store.meta.namesList().forEach(names::add);
        meta.add("profileNames", names);
        meta.addProperty("firstSeen", store.meta.firstSeen);
        meta.addProperty("lastSeen", store.meta.lastSeen);
        meta.addProperty("sessions", store.meta.sessions);
        meta.addProperty("serverStatReset", store.meta.serverStatReset);
        root.add("meta", meta);

        JsonObject stats = new JsonObject();
        for (Map.Entry<String, Map<String, Long>> typeEntry : store.stats.deepCopyOrdered().entrySet()) {
            JsonObject targets = new JsonObject();
            typeEntry.getValue().forEach(targets::addProperty);
            stats.add(typeEntry.getKey(), targets);
        }
        root.add("stats", stats);
        return root;
    }

    public LocationStore fromJson(JsonObject root) throws JsonParseException {
        JsonObject metaJson = requireObject(root, "meta");
        JsonObject statsJson = requireObject(root, "stats");

        StoreMeta meta = new StoreMeta();
        if (metaJson.has("format")) {
            meta.format = metaJson.get("format").getAsInt();
        }
        meta.kind = requireString(metaJson, "kind");
        meta.locationId = requireString(metaJson, "locationId");
        meta.displayName = metaJson.has("displayName") ? metaJson.get("displayName").getAsString() : meta.locationId;
        meta.address = metaJson.has("address") && metaJson.get("address").isJsonPrimitive()
                ? metaJson.get("address").getAsString() : null;
        meta.profileId = requireString(metaJson, "profileId");
        if (metaJson.has("profileNames") && metaJson.get("profileNames").isJsonArray()) {
            for (JsonElement e : metaJson.getAsJsonArray("profileNames")) {
                if (e.isJsonPrimitive()) {
                    meta.addName(e.getAsString());
                }
            }
        }
        meta.firstSeen = optLong(metaJson, "firstSeen");
        meta.lastSeen = optLong(metaJson, "lastSeen");
        meta.sessions = (int) optLong(metaJson, "sessions");
        meta.serverStatReset = metaJson.has("serverStatReset") && metaJson.get("serverStatReset").getAsBoolean();

        Map<String, Map<String, Long>> raw = new TreeMap<>();
        for (Map.Entry<String, JsonElement> typeEntry : statsJson.entrySet()) {
            if (!typeEntry.getValue().isJsonObject()) {
                continue;
            }
            Map<String, Long> targets = new TreeMap<>();
            for (Map.Entry<String, JsonElement> targetEntry : typeEntry.getValue().getAsJsonObject().entrySet()) {
                try {
                    targets.put(targetEntry.getKey(), targetEntry.getValue().getAsLong());
                } catch (NumberFormatException ignored) {
                    // 单项数值非法：跳过该项而不是丢弃整个文件
                }
            }
            // 迁移旧版键名：现实游玩时长曾以无命名空间形式存储
            if ("blackannins_statistics:custom".equals(typeEntry.getKey())) {
                Long legacy = targets.remove("real_play_time");
                if (legacy != null) {
                    targets.merge("blackannins_statistics:real_play_time", legacy, Long::sum);
                }
            }
            raw.put(typeEntry.getKey(), targets);
        }
        return new LocationStore(meta, StatSnapshot.ofRaw(raw));
    }

    private static JsonObject requireObject(JsonObject parent, String key) throws JsonParseException {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonObject()) {
            throw new JsonSyntaxException("缺少字段 \"" + key + "\"");
        }
        return e.getAsJsonObject();
    }

    private static String requireString(JsonObject parent, String key) throws JsonParseException {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new JsonSyntaxException("缺少字符串字段 \"" + key + "\"");
        }
        return e.getAsString();
    }

    private static long optLong(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0L;
    }

    // ---- 文件读写 ----

    /** 同步读取（仅在 IO 线程或 UI 打开时调用，文件很小）。损坏时移入 backups/ 并返回 null。 */
    public LocationStore read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            if (root == null) {
                throw new JsonSyntaxException("空文件");
            }
            return fromJson(root);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            BlackAnninsStatistics.LOGGER.warn("统计数据文件损坏，已隔离备份: {}", file, e);
            backupCorrupted(file);
            return null;
        }
    }

    /** 异步原子写入：先写 .tmp 再 rename，玩家随时手工编辑/删除不会读到半截文件。 */
    public void writeAsync(Path file, LocationStore store) {
        Path parent = file.getParent();
        Runnable task = () -> {
            try {
                Files.createDirectories(parent);
                String text = GSON.toJson(toJson(store));
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                BlackAnninsStatistics.LOGGER.error("保存统计数据失败: {}", file, e);
            }
        };
        io.execute(task);
    }

    private void backupCorrupted(Path file) {
        try {
            Path backups = StoragePaths.get().backupsDir();
            Files.createDirectories(backups);
            String stamp = LocalDateTime.now().format(BACKUP_TS);
            String base = file.getFileName().toString().replaceFirst("\\.json$", "");
            Files.move(file, backups.resolve(base + "." + stamp + ".corrupted.json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 备份失败不致命：后续写入会覆盖损坏文件
        }
    }

    /** 任意小任务排队到 IO 线程（如 profiles.json 保存）。 */
    public void runAsync(Runnable task) {
        io.execute(task);
    }

    /** 同步等待所有挂起的写入完成（离开世界/退出游戏时调用，带超时；线程池保持存活以供下次会话复用）。 */
    public void flushAndWait(long timeoutSeconds) {
        try {
            io.submit(() -> { }).get(timeoutSeconds, TimeUnit.SECONDS); // 哨兵任务：队列中它之前的写入全部完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
            // 超时：放弃等待，未落盘部分由累计快照机制在下次进入时自动补齐
        }
    }
}
