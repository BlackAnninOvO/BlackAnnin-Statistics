package com.blackannin.statistics.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 统一索引文件：档案信息 + 统计条目置顶。全部数据目录只有这一个索引 JSON。
 * 置顶键格式 "<分类>/<条目主键>"，跨存档、跨服务器、跨游戏端共享。
 * 首次创建时默认置顶"现实游玩时长"。
 */
public final class ProfileIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String CAT_GENERAL = "general";
    public static final String CAT_BLOCKS = "blocks";
    public static final String CAT_ITEMS = "items";
    public static final String CAT_MOBS = "mobs";

    public static final class Entry {
        public final Set<String> names = new LinkedHashSet<>();
        public long lastSeen;
        public String lastLocation; // "kind:locationId"
    }

    private final Map<String, Entry> entries = new TreeMap<>();
    /** 分类 → 置顶条目主键集合。 */
    private final Map<String, LinkedHashSet<String>> pins = new LinkedHashMap<>();

    public static ProfileIndex load(Path file) {
        ProfileIndex index = new ProfileIndex();
        if (!Files.isRegularFile(file)) {
            index.pins.getOrDefault(CAT_GENERAL, index.pins
                    .computeIfAbsent(CAT_GENERAL, k -> new LinkedHashSet<>()))
                    .add("blackannins_statistics:real_play_time");
            index.save(file);
            return index;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            if (root == null) {
                return index;
            }
            if (root.has("profiles") && root.get("profiles").isJsonObject()) {
                JsonObject profiles = root.getAsJsonObject("profiles");
                for (String uuid : profiles.keySet()) {
                    JsonObject e = profiles.getAsJsonObject(uuid);
                    Entry entry = new Entry();
                    if (e.has("names") && e.get("names").isJsonArray()) {
                        for (JsonElement n : e.getAsJsonArray("names")) {
                            if (n.isJsonPrimitive()) {
                                entry.names.add(n.getAsString());
                            }
                        }
                    }
                    entry.lastSeen = e.has("lastSeen") ? e.get("lastSeen").getAsLong() : 0L;
                    entry.lastLocation = e.has("lastLocation") && e.get("lastLocation").isJsonPrimitive()
                            ? e.get("lastLocation").getAsString() : null;
                    index.entries.put(uuid, entry);
                }
            }
            if (root.has("pins") && root.get("pins").isJsonObject()) {
                for (Map.Entry<String, JsonElement> cat : root.getAsJsonObject("pins").entrySet()) {
                    LinkedHashSet<String> ids = new LinkedHashSet<>();
                    if (cat.getValue().isJsonArray()) {
                        for (JsonElement id : cat.getValue().getAsJsonArray()) {
                            if (id.isJsonPrimitive()) {
                                ids.add(id.getAsString());
                            }
                        }
                    }
                    index.pins.put(cat.getKey(), ids);
                }
            } else {
                // 旧版索引文件没有 pins 字段：补默认置顶（用户清空置顶后字段仍存在，不会被补回）
                index.pins.computeIfAbsent(CAT_GENERAL, k -> new LinkedHashSet<>())
                        .add("blackannins_statistics:real_play_time");
            }
        } catch (IOException | RuntimeException e) {
            BlackAnninsStatistics.LOGGER.warn("profiles.json 读取失败，将重建", e);
        }
        return index;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonObject profiles = new JsonObject();
            entries.forEach((uuid, entry) -> {
                JsonObject e = new JsonObject();
                com.google.gson.JsonArray names = new com.google.gson.JsonArray();
                entry.names.forEach(names::add);
                e.add("names", names);
                e.addProperty("lastSeen", entry.lastSeen);
                if (entry.lastLocation != null) {
                    e.addProperty("lastLocation", entry.lastLocation);
                }
                profiles.add(uuid, e);
            });
            root.add("profiles", profiles);
            JsonObject pinsJson = new JsonObject();
            pins.forEach((cat, ids) -> {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                ids.forEach(arr::add);
                pinsJson.add(cat, arr);
            });
            root.add("pins", pinsJson);
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BlackAnninsStatistics.LOGGER.warn("profiles.json 保存失败", e);
        }
    }

    /** 会话开始/结束时调用，合并最新档案信息。 */
    public void touch(String uuid, String name, long nowMillis, String locationKey) {
        Entry entry = entries.computeIfAbsent(uuid, k -> new Entry());
        if (name != null && !name.isBlank()) {
            entry.names.add(name);
        }
        entry.lastSeen = Math.max(entry.lastSeen, nowMillis);
        if (locationKey != null) {
            entry.lastLocation = locationKey;
        }
    }

    public Map<String, Entry> view() {
        return new LinkedHashMap<>(entries);
    }

    /** 深拷贝（供 IO 线程落盘，避免与主线程的并发修改）。 */
    public ProfileIndex copy() {
        ProfileIndex out = new ProfileIndex();
        entries.forEach((uuid, entry) -> {
            Entry e = new Entry();
            e.names.addAll(entry.names);
            e.lastSeen = entry.lastSeen;
            e.lastLocation = entry.lastLocation;
            out.entries.put(uuid, e);
        });
        pins.forEach((cat, ids) -> out.pins.put(cat, new LinkedHashSet<>(ids)));
        return out;
    }

    public void remove(String uuid) {
        entries.remove(uuid);
    }

    // ---- 置顶 ----

    public boolean isPinned(String category, String rowKey) {
        Set<String> ids = pins.get(category);
        return ids != null && ids.contains(rowKey);
    }

    /** 切换置顶状态并立即持久化（小文件，低频操作同步写可接受）。 */
    public void togglePin(String category, String rowKey, Path file) {
        LinkedHashSet<String> ids = pins.computeIfAbsent(category, k -> new LinkedHashSet<>());
        if (!ids.remove(rowKey)) {
            ids.add(rowKey);
        }
        save(file);
    }

    /** 该分类的置顶条目主键集合。 */
    public Set<String> pinnedKeys(String category) {
        return pins.getOrDefault(category, new LinkedHashSet<>());
    }
}
