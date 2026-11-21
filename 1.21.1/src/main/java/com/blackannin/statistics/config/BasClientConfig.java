package com.blackannin.statistics.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.network.chat.Component;

import com.blackannin.statistics.BlackAnninsStatistics;

/**
 * 客户端配置（config/blackannins_statistics.json，可经 /bas config 打开设置界面修改）。
 * 等价 NeoForge 版的 ModConfigSpec：配置键、默认值、取值范围完全一致；
 * 差异仅在存储格式（JSON 而非 TOML）与加载时机（客户端初始化时手动加载）。
 * 值容器统一提供 {@code get()}，因此平台无关代码的调用方式与主版本完全相同。
 * 统计数据文件本身不属于配置，位于数据目录（见 {@code StoragePaths}）。
 */
public final class BasClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final BoolValue REPLACE_VANILLA_STATS_SCREEN =
            bool("replaceVanillaStatsScreen", true);
    public static final IntValue SAVE_INTERVAL_SECONDS =
            intVal("saveIntervalSeconds", 60, 10, 3600);
    public static final IntValue SYNC_INTERVAL_SECONDS =
            intVal("syncIntervalSeconds", 30, 5, 600);
    public static final StringValue DATA_DIRECTORY_OVERRIDE =
            str("dataDirectoryOverride", "");
    public static final BoolValue AGGREGATE_ALL_PROFILES_BY_DEFAULT =
            bool("aggregateAllProfilesByDefault", false);
    public static final BoolValue SESSION_HUD_ENABLED =
            bool("sessionHudEnabled", true);
    public static final IntValue SESSION_HUD_BACKGROUND_OPACITY =
            intVal("sessionHudBackgroundOpacity", 56, 0, 100);
    public static final EnumValue<HudCorner> SESSION_HUD_CORNER =
            enumVal("sessionHudCorner", HudCorner.TOP_LEFT, HudCorner.class);
    public static final BoolValue SESSION_HUD_SHOW_REAL_TIME =
            bool("sessionHudShowRealTime", true);
    public static final BoolValue SESSION_HUD_SHOW_GAME_TIME =
            bool("sessionHudShowGameTime", true);
    public static final BoolValue SESSION_HUD_SHOW_PLACED =
            bool("sessionHudShowPlaced", true);
    public static final BoolValue SESSION_HUD_SHOW_MINED =
            bool("sessionHudShowMined", true);
    public static final BoolValue SESSION_HUD_SHOW_PICKED_UP =
            bool("sessionHudShowPickedUp", true);
    public static final BoolValue SESSION_HUD_SHOW_CRAFTED =
            bool("sessionHudShowCrafted", true);
    public static final BoolValue SESSION_HUD_SHOW_DEATHS =
            bool("sessionHudShowDeaths", true);
    public static final BoolValue SESSION_HUD_SHOW_MOB_KILLS =
            bool("sessionHudShowMobKills", true);

    /** 全部配置值（顺序与 NeoForge 版 ModConfigSpec 声明一致，用于 UI 与序列化）。 */
    private static final List<AbstractValue<?>> ALL_VALUES;

    static {
        List<AbstractValue<?>> values = new ArrayList<>();
        values.add(REPLACE_VANILLA_STATS_SCREEN);
        values.add(SAVE_INTERVAL_SECONDS);
        values.add(SYNC_INTERVAL_SECONDS);
        values.add(DATA_DIRECTORY_OVERRIDE);
        values.add(AGGREGATE_ALL_PROFILES_BY_DEFAULT);
        values.add(SESSION_HUD_ENABLED);
        values.add(SESSION_HUD_BACKGROUND_OPACITY);
        values.add(SESSION_HUD_CORNER);
        values.add(SESSION_HUD_SHOW_REAL_TIME);
        values.add(SESSION_HUD_SHOW_GAME_TIME);
        values.add(SESSION_HUD_SHOW_PLACED);
        values.add(SESSION_HUD_SHOW_MINED);
        values.add(SESSION_HUD_SHOW_PICKED_UP);
        values.add(SESSION_HUD_SHOW_CRAFTED);
        values.add(SESSION_HUD_SHOW_DEATHS);
        values.add(SESSION_HUD_SHOW_MOB_KILLS);
        ALL_VALUES = Collections.unmodifiableList(values);
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("blackannins_statistics.json");
    }

    /** 客户端初始化时调用：加载配置；文件缺失时生成默认配置（等价 ModConfig 加载事件）。 */
    public static void load() {
        Path file = configFile();
        try {
            if (Files.isRegularFile(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject root = GSON.fromJson(text, JsonObject.class);
                if (root != null) {
                    for (AbstractValue<?> value : ALL_VALUES) {
                        value.readFrom(root);
                    }
                }
            } else {
                save();
            }
            BlackAnninsStatistics.LOGGER.info("[BAS] 配置已加载");
        } catch (Exception e) {
            BlackAnninsStatistics.LOGGER.warn("[BAS] 配置读取失败，沿用当前值", e);
        }
    }

    /** 保存配置（设置界面点击完成/返回时调用；修改立即生效，等价 Reloading 语义）。 */
    public static void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (AbstractValue<?> value : ALL_VALUES) {
                value.writeTo(root);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            BlackAnninsStatistics.LOGGER.info("[BAS] 配置已保存（修改应立即生效）");
        } catch (IOException e) {
            BlackAnninsStatistics.LOGGER.error("[BAS] 配置保存失败: {}", file, e);
        }
    }

    public static long saveIntervalMillis() {
        return SAVE_INTERVAL_SECONDS.get() * 1000L;
    }

    public static long syncIntervalMillis() {
        return SYNC_INTERVAL_SECONDS.get() * 1000L;
    }

    /** 小窗锚定的屏幕角落。翻译键与 NeoForge 版 TranslatableEnum 一致（gui.bas.hud_corner.*）。 */
    public enum HudCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** 配置界面显示名（按语言文件显示中文名）。 */
        public Component translatedName() {
            return Component.translatable("gui.bas.hud_corner." + name());
        }
    }

    // ================= 值容器 =================

    /** 配置值基类：等价 ModConfigSpec 的 *Value（get/set/序列化），全代码以 {@code .get()} 读取。 */
    private abstract static class AbstractValue<T> {
        final String key;
        final T defaultValue;
        T value;

        AbstractValue(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public void set(T v) {
            this.value = normalize(v);
        }

        /** 语言文件键：与 NeoForge 版 ConfigurationScreen 的翻译键一致。 */
        public String labelKey() {
            return "blackannins_statistics.configuration." + key;
        }

        abstract T normalize(T v);

        abstract void readFrom(JsonObject root);

        abstract void writeTo(JsonObject root);
    }

    /** 布尔值。 */
    public static final class BoolValue extends AbstractValue<Boolean> {
        BoolValue(String key, boolean defaultValue) {
            super(key, defaultValue);
        }

        @Override
        Boolean normalize(Boolean v) {
            return v == null ? defaultValue : v;
        }

        @Override
        void readFrom(JsonObject root) {
            JsonElement e = root.get(key);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    value = e.getAsBoolean();
                } catch (Exception ignored) {
                    // 单项数值非法：保留当前值
                }
            }
        }

        @Override
        void writeTo(JsonObject root) {
            root.addProperty(key, value);
        }
    }

    /** 整数值（带 min/max 夹取，语义同 ModConfigSpec.defineInRange）。 */
    public static final class IntValue extends AbstractValue<Integer> {
        private final int min;
        private final int max;

        IntValue(String key, int defaultValue, int min, int max) {
            super(key, defaultValue);
            this.min = min;
            this.max = max;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        @Override
        Integer normalize(Integer v) {
            if (v == null) {
                return defaultValue;
            }
            return Math.max(min, Math.min(max, v));
        }

        @Override
        void readFrom(JsonObject root) {
            JsonElement e = root.get(key);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    value = e.getAsInt();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        void writeTo(JsonObject root) {
            root.addProperty(key, value);
        }
    }

    /** 字符串值。 */
    public static final class StringValue extends AbstractValue<String> {
        StringValue(String key, String defaultValue) {
            super(key, defaultValue);
        }

        @Override
        String normalize(String v) {
            return v == null ? defaultValue : v;
        }

        @Override
        void readFrom(JsonObject root) {
            JsonElement e = root.get(key);
            if (e != null && e.isJsonPrimitive()) {
                value = e.getAsString();
            }
        }

        @Override
        void writeTo(JsonObject root) {
            root.addProperty(key, value);
        }
    }

    /** 枚举值（循环切换语义，供配置界面使用）。 */
    public static final class EnumValue<E extends Enum<E>> extends AbstractValue<E> {
        private final Class<E> type;

        EnumValue(String key, E defaultValue, Class<E> type) {
            super(key, defaultValue);
            this.type = type;
        }

        /** 循环切换到下一个枚举值。 */
        public E next() {
            E[] constants = type.getEnumConstants();
            return constants[(value.ordinal() + 1) % constants.length];
        }

        @Override
        E normalize(E v) {
            return v == null ? defaultValue : v;
        }

        @Override
        void readFrom(JsonObject root) {
            JsonElement e = root.get(key);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    value = Enum.valueOf(type, e.getAsString());
                } catch (IllegalArgumentException ignored) {
                    // 未知枚举名：保留当前值
                }
            }
        }

        @Override
        void writeTo(JsonObject root) {
            root.addProperty(key, value.name());
        }
    }

    // ---- 工厂 ----

    private static BoolValue bool(String key, boolean defaultValue) {
        return new BoolValue(key, defaultValue);
    }

    private static IntValue intVal(String key, int defaultValue, int min, int max) {
        return new IntValue(key, defaultValue, min, max);
    }

    private static StringValue str(String key, String defaultValue) {
        return new StringValue(key, defaultValue);
    }

    private static <E extends Enum<E>> EnumValue<E> enumVal(String key, E defaultValue, Class<E> type) {
        return new EnumValue<>(key, defaultValue, type);
    }

    private BasClientConfig() {}
}
