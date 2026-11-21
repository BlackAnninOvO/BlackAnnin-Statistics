package com.blackannin.statistics.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置（config/blackannins_statistics-client.toml，可通过模组列表的配置界面修改）。
 * Forge 1.20.1 对应 NeoForge 的 ModConfigSpec（同源 API，包名与个别方法签名差异）。
 * 统计数据文件本身不属于配置，位于数据目录（见 {@code StoragePaths}）。
 */
public final class BasClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue REPLACE_VANILLA_STATS_SCREEN = BUILDER
            .comment("用模组统计中心替换原版\"统计信息\"按钮打开的界面",
                    "Replace the screen opened by the vanilla Statistics button with the mod hub")
            .define("replaceVanillaStatsScreen", true);

    public static final ForgeConfigSpec.IntValue SAVE_INTERVAL_SECONDS = BUILDER
            .comment("统计数据自动保存间隔（秒）", "Autosave interval of tracked statistics, in seconds")
            .defineInRange("saveIntervalSeconds", 60, 10, 3600);

    public static final ForgeConfigSpec.IntValue SYNC_INTERVAL_SECONDS = BUILDER
            .comment("多人模式下向服务器请求统计数据的间隔（秒），与打开原版统计界面的请求方式相同",
                    "Interval to request stats from multiplayer servers, in seconds")
            .defineInRange("syncIntervalSeconds", 30, 5, 600);

    public static final ForgeConfigSpec.ConfigValue<String> DATA_DIRECTORY_OVERRIDE = BUILDER
            .comment("统计数据目录覆盖（绝对路径，留空使用默认目录）",
                    "Override directory for statistics data (absolute path, empty = default)")
            .define("dataDirectoryOverride", "");

    public static final ForgeConfigSpec.BooleanValue AGGREGATE_ALL_PROFILES_BY_DEFAULT = BUILDER
            .comment("全局统计默认显示所有档案（多个玩家 ID）的合计",
                    "Show the sum of all profiles in the Global tab by default")
            .define("aggregateAllProfilesByDefault", false);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_ENABLED = BUILDER
            .comment("启用\"本次游玩\"小窗（按 F9 可切换显示，离开世界自动归零）",
                    "Enable the \"This session\" HUD overlay (toggle with F9, resets on leaving a world)")
            .define("sessionHudEnabled", true);

    public static final ForgeConfigSpec.IntValue SESSION_HUD_BACKGROUND_OPACITY = BUILDER
            .comment("小窗背景不透明度（0=完全透明，100=不透明）",
                    "HUD overlay background opacity (0 = fully transparent, 100 = opaque)")
            .defineInRange("sessionHudBackgroundOpacity", 56, 0, 100);

    public static final ForgeConfigSpec.EnumValue<HudCorner> SESSION_HUD_CORNER = BUILDER
            .comment("小窗显示在屏幕的哪个角落",
                    "Which corner of the screen the HUD overlay is anchored to")
            .defineEnum("sessionHudCorner", HudCorner.TOP_LEFT);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_REAL_TIME = BUILDER
            .comment("小窗显示现实时长", "Show real time in the HUD overlay")
            .define("sessionHudShowRealTime", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_GAME_TIME = BUILDER
            .comment("小窗显示游戏内时长", "Show in-game time in the HUD overlay")
            .define("sessionHudShowGameTime", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_PLACED = BUILDER
            .comment("小窗显示放置方块数", "Show placed blocks in the HUD overlay")
            .define("sessionHudShowPlaced", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_MINED = BUILDER
            .comment("小窗显示破坏方块数", "Show mined blocks in the HUD overlay")
            .define("sessionHudShowMined", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_PICKED_UP = BUILDER
            .comment("小窗显示拾取物品数", "Show picked up items in the HUD overlay")
            .define("sessionHudShowPickedUp", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_CRAFTED = BUILDER
            .comment("小窗显示合成物品数", "Show crafted items in the HUD overlay")
            .define("sessionHudShowCrafted", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_DEATHS = BUILDER
            .comment("小窗显示死亡次数", "Show deaths in the HUD overlay")
            .define("sessionHudShowDeaths", true);

    public static final ForgeConfigSpec.BooleanValue SESSION_HUD_SHOW_MOB_KILLS = BUILDER
            .comment("小窗显示击杀生物数", "Show mob kills in the HUD overlay")
            .define("sessionHudShowMobKills", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * 小窗锚定的屏幕角落。
     * 与 NeoForge 版差异：Forge 无 TranslatableEnum 接口，中文名由自制配置界面
     * （{@link BasConfigScreen}）按语言键渲染，本枚举保持纯数据。
     */
    public enum HudCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** 显示名（配置界面用）：按语言键渲染，与 NeoForge 版 TranslatableEnum 行为一致。 */
        public net.minecraft.network.chat.Component translatedName() {
            return net.minecraft.network.chat.Component.translatable("gui.bas.hud_corner." + name());
        }
    }

    private BasClientConfig() {}

    public static long saveIntervalMillis() {
        return SAVE_INTERVAL_SECONDS.get() * 1000L;
    }

    public static long syncIntervalMillis() {
        return SYNC_INTERVAL_SECONDS.get() * 1000L;
    }
}
