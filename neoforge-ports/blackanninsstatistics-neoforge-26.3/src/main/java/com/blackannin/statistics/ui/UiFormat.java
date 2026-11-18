package com.blackannin.statistics.ui;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;

import net.minecraft.network.chat.Component;

/**
 * 统计值展示格式化。与原版 StatFormatter 行为一致，但支持 long（跨存档/服务器求和会超过 int）。
 */
public final class UiFormat {
    private static final DecimalFormat DECIMAL = new DecimalFormat("#,##0.#");

    private static final Set<String> TIME_STATS = Set.of(
            "minecraft:play_time", "minecraft:total_world_time", "minecraft:time_since_death",
            "minecraft:time_since_rest", "minecraft:sneak_time");

    private static final Set<String> DISTANCE_STATS = Set.of(
            "minecraft:walk_one_cm", "minecraft:crouch_one_cm", "minecraft:sprint_one_cm",
            "minecraft:walk_on_water_one_cm", "minecraft:fall_one_cm", "minecraft:climb_one_cm",
            "minecraft:fly_one_cm", "minecraft:walk_under_water_one_cm", "minecraft:minecart_one_cm",
            "minecraft:boat_one_cm", "minecraft:pig_one_cm", "minecraft:horse_one_cm",
            "minecraft:aviate_one_cm", "minecraft:swim_one_cm", "minecraft:strider_one_cm");

    private static final Set<String> DIVIDE10_STATS = Set.of(
            "minecraft:damage_dealt", "minecraft:damage_dealt_absorbed", "minecraft:damage_dealt_resisted",
            "minecraft:damage_taken", "minecraft:damage_blocked_by_shield", "minecraft:damage_absorbed",
            "minecraft:damage_resisted");

    private UiFormat() {}

    private static final String REAL_TIME_FULL = "blackannins_statistics:real_play_time";
    private static final String REAL_TIME_LEGACY = "real_play_time";

    /** custom 统计的展示格式（未知的模组 custom 统计按整数处理）。 */
    public static String custom(String targetId, long value) {
        if (REAL_TIME_FULL.equals(targetId) || REAL_TIME_LEGACY.equals(targetId)) {
            return durationMillis(value);
        }
        if (TIME_STATS.contains(targetId)) {
            // 名称前缀（游戏内）已由 customStatName 标注，值本身保持简洁（后缀按用户要求移除）
            return time(value);
        }
        if (DISTANCE_STATS.contains(targetId)) {
            return distance(value);
        }
        if (DIVIDE10_STATS.contains(targetId)) {
            return DECIMAL.format(value * 0.1);
        }
        return String.valueOf(value);
    }

    /**
     * 现实时长（毫秒）展示：本地化"几小时几分"，最高到小时粒度。
     * 注意：必须用带参的 I18n.get(key, args)——无参调用也会走 String.format，
     * lang 值含 %d 时会抛异常并显示 "Format error: ..."。
     */
    public static String durationMillis(long millis) {
        long totalMinutes = millis / 60000;
        if (totalMinutes >= 60) {
            return net.minecraft.client.resources.language.I18n.get("gui.bas.duration.hours",
                    totalMinutes / 60, totalMinutes % 60);
        }
        return net.minecraft.client.resources.language.I18n.get("gui.bas.duration.minutes", totalMinutes);
    }

    /**
     * 游戏内时长（tick）展示：本地化"小时+分"，最高到小时粒度（不转换到天）。
     * 必须用带参 I18n.get（无参调用也会走 String.format，lang 值含 %d 会抛异常）。
     */
    public static String time(long ticks) {
        long totalMinutes = ticks / 20 / 60;
        if (totalMinutes >= 60) {
            return net.minecraft.client.resources.language.I18n.get("gui.bas.time.hours",
                    totalMinutes / 60, totalMinutes % 60);
        }
        return net.minecraft.client.resources.language.I18n.get("gui.bas.time.minutes", totalMinutes);
    }

    /** 距离（厘米）展示：本地化公里/米/厘米（英文 km/m/cm）。 */
    public static String distance(long cm) {
        double meters = cm / 100.0;
        double km = meters / 1000.0;
        if (km > 0.5) {
            return net.minecraft.client.resources.language.I18n.get("gui.bas.distance.km", DECIMAL.format(km));
        }
        if (meters > 0.5) {
            return net.minecraft.client.resources.language.I18n.get("gui.bas.distance.m", DECIMAL.format(meters));
        }
        return net.minecraft.client.resources.language.I18n.get("gui.bas.distance.cm", cm);
    }

    public static String num(long v) {
        return String.valueOf(v);
    }

    /**
     * custom 统计名称：优先用原版约定翻译键 "stat.&lt;ns&gt;.&lt;path&gt;"；
     * 模组注册且未提供语言条目的统计（会显示成原始键名）回退为可读的人性化名称。
     */
    public static net.minecraft.network.chat.Component customStatName(String targetId) {
        // 原版 TIME 统计：代码级强制"游戏内"前缀（与"现实"时间明确区分，不依赖语言文件覆盖）
        if (TIME_STATS.contains(targetId)) {
            String key = "stat." + targetId.replace(':', '.');
            String base = net.minecraft.locale.Language.getInstance().has(key)
                    ? net.minecraft.client.resources.language.I18n.get(key)
                    : humanizeStatId(targetId);
            return net.minecraft.network.chat.Component.literal(
                    net.minecraft.client.resources.language.I18n.get("gui.bas.tag.in_game") + base);
        }
        String key = "stat." + targetId.replace(':', '.');
        if (net.minecraft.locale.Language.getInstance().has(key)) {
            return net.minecraft.network.chat.Component.translatable(key);
        }
        return net.minecraft.network.chat.Component.literal(humanizeStatId(targetId));
    }

    /** "items_inserted" → "Items inserted"；带命名空间的附上模组 id 便于辨认。 */
    static String humanizeStatId(String id) {
        int colon = id.indexOf(':');
        String ns = colon >= 0 ? id.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (sb.length() == 0) {
            sb.append(path);
        }
        return ns.equals("minecraft") ? sb.toString() : sb + " (" + ns + ")";
    }

    /** 简短日期时间（最后游玩时间）。 */
    public static String dateTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()));
    }
}
