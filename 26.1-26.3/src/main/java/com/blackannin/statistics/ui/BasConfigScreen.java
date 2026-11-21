package com.blackannin.statistics.ui;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.config.BasClientConfig;

/**
 * 自制配置界面（Forge 版专用）。
 * NeoForge 21.1 自带 ConfigurationScreen，Forge 没有——本类提供等价能力：
 * 模组列表"配置"按钮 → 全部 CLIENT 配置项可视化修改（布尔切换 / 数值预设循环 /
 * 枚举循环 / 目录覆盖输入），与 NeoForge 版的配置项、语言键完全一致。
 * 修改即写入 TOML（ForgeConfigSpec.set 自动保存并触发 ModConfigEvent.Reloading）。
 */
public final class BasConfigScreen extends Screen {
    /** 数值型配置的候选值（配置界面循环选择；任意合法值仍可直接编辑 TOML 文件）。 */
    private static final int[] SAVE_INTERVAL_PRESETS = {10, 30, 60, 120, 300, 600, 1800, 3600};
    private static final int[] SYNC_INTERVAL_PRESETS = {5, 10, 30, 60, 120, 300, 600};
    private static final int[] OPACITY_PRESETS = {0, 10, 25, 40, 56, 70, 85, 100};

    private final Screen parent;
    private final java.util.List<Runnable> changeListeners = new java.util.ArrayList<>();
    @Nullable
    private EditBox dirBox;

    public BasConfigScreen(Screen parent) {
        super(Component.translatable("blackannins_statistics.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 每行高度自适应：保证在低 GUI 高度（如 240）下也不与底部按钮重叠
        int rows = 9; // 6 行布尔 + 2 行(数值/枚举) + 1 行目录覆盖
        int rowH = Math.max(16, Math.min(22, (this.height - 100) / rows));

        // ---- 布尔项（左列 6、右列 6，共 11 项 + 空位） ----
        BooleanOption[] booleans = {
                new BooleanOption("replaceVanillaStatsScreen", () -> BasClientConfig.REPLACE_VANILLA_STATS_SCREEN.get(),
                        v -> BasClientConfig.REPLACE_VANILLA_STATS_SCREEN.set(v)),
                new BooleanOption("sessionHudEnabled", () -> BasClientConfig.SESSION_HUD_ENABLED.get(),
                        v -> BasClientConfig.SESSION_HUD_ENABLED.set(v)),
                new BooleanOption("sessionHudShowRealTime", () -> BasClientConfig.SESSION_HUD_SHOW_REAL_TIME.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_REAL_TIME.set(v)),
                new BooleanOption("sessionHudShowGameTime", () -> BasClientConfig.SESSION_HUD_SHOW_GAME_TIME.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_GAME_TIME.set(v)),
                new BooleanOption("sessionHudShowPlaced", () -> BasClientConfig.SESSION_HUD_SHOW_PLACED.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_PLACED.set(v)),
                new BooleanOption("sessionHudShowMined", () -> BasClientConfig.SESSION_HUD_SHOW_MINED.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_MINED.set(v)),
                new BooleanOption("sessionHudShowPickedUp", () -> BasClientConfig.SESSION_HUD_SHOW_PICKED_UP.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_PICKED_UP.set(v)),
                new BooleanOption("sessionHudShowCrafted", () -> BasClientConfig.SESSION_HUD_SHOW_CRAFTED.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_CRAFTED.set(v)),
                new BooleanOption("sessionHudShowDeaths", () -> BasClientConfig.SESSION_HUD_SHOW_DEATHS.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_DEATHS.set(v)),
                new BooleanOption("sessionHudShowMobKills", () -> BasClientConfig.SESSION_HUD_SHOW_MOB_KILLS.get(),
                        v -> BasClientConfig.SESSION_HUD_SHOW_MOB_KILLS.set(v)),
                new BooleanOption("aggregateAllProfilesByDefault", () -> BasClientConfig.AGGREGATE_ALL_PROFILES_BY_DEFAULT.get(),
                        v -> BasClientConfig.AGGREGATE_ALL_PROFILES_BY_DEFAULT.set(v)),
        };
        int y = 34;
        int colW = Math.min(245, this.width / 2 - 10);
        int x1 = this.width / 2 - colW - 5;
        int x2 = this.width / 2 + 5;
        for (int i = 0; i < booleans.length; i++) {
            BooleanOption opt = booleans[i];
            int col = i % 2;
            int row = i / 2;
            int bx = col == 0 ? x1 : x2;
            int by = y + row * rowH;
            AbstractWidget btn = Button.builder(opt.label(), b -> {
                opt.toggle();
                b.setMessage(opt.label());
            }).bounds(bx, by, colW, 20).build();
            this.addRenderableWidget(btn);
        }

        // ---- 数值与枚举项（左右各 2） ----
        int rowMid = y + 6 * rowH;
        this.addRenderableWidget(cycleButton("saveIntervalSeconds", SAVE_INTERVAL_PRESETS,
                BasClientConfig.SAVE_INTERVAL_SECONDS.get(),
                v -> BasClientConfig.SAVE_INTERVAL_SECONDS.set(v), x1, rowMid, colW));
        this.addRenderableWidget(cycleButton("syncIntervalSeconds", SYNC_INTERVAL_PRESETS,
                BasClientConfig.SYNC_INTERVAL_SECONDS.get(),
                v -> BasClientConfig.SYNC_INTERVAL_SECONDS.set(v), x2, rowMid, colW));

        int rowMid2 = y + 7 * rowH;
        this.addRenderableWidget(cycleButton("sessionHudBackgroundOpacity", OPACITY_PRESETS,
                BasClientConfig.SESSION_HUD_BACKGROUND_OPACITY.get(),
                v -> BasClientConfig.SESSION_HUD_BACKGROUND_OPACITY.set(v), x1, rowMid2, colW));
        // 枚举：HudCorner 循环（中文显示与 NeoForge 版 TranslatableEnum 一致）
        BasClientConfig.HudCorner[] corners = BasClientConfig.HudCorner.values();
        int initial = 0;
        BasClientConfig.HudCorner cur = BasClientConfig.SESSION_HUD_CORNER.get();
        for (int i = 0; i < corners.length; i++) {
            if (corners[i] == cur) {
                initial = i;
                break;
            }
        }
        Button cornerBtn = Button.builder(cornerLabel(corners[initial]), b -> {
            int next = (nextIndex(corners, cur) + 1) % corners.length;
            BasClientConfig.HudCorner nextCorner = corners[next];
            BasClientConfig.SESSION_HUD_CORNER.set(nextCorner);
            b.setMessage(cornerLabel(nextCorner));
        }).bounds(x2, rowMid2, colW, 20).build();
        this.addRenderableWidget(cornerBtn);

        // ---- 目录覆盖（整行：标签 + 输入框） ----
        int rowDir = y + 8 * rowH;
        int labelW = Math.min(120, colW / 2);
        this.addRenderableWidget(Button.builder(Component.translatable(
                        "blackannins_statistics.configuration.dataDirectoryOverride").append(":"),
                        b -> { })
                .bounds(x1, rowDir, labelW, 20).build());
        int boxX = x1 + labelW + 4;
        int boxRight = x2 + colW;
        dirBox = new EditBox(this.font, boxX, rowDir + 1, boxRight - boxX, 18,
                Component.translatable("blackannins_statistics.configuration.dataDirectoryOverride"));
        dirBox.setValue(BasClientConfig.DATA_DIRECTORY_OVERRIDE.get());
        dirBox.setMaxLength(512);
        this.addRenderableWidget(dirBox);

        // ---- 底部：完成（保存目录覆盖并返回） ----
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    private int nextIndex(BasClientConfig.HudCorner[] corners, BasClientConfig.HudCorner current) {
        for (int i = 0; i < corners.length; i++) {
            if (corners[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private Component cornerLabel(BasClientConfig.HudCorner corner) {
        return Component.translatable("blackannins_statistics.configuration.sessionHudCorner")
                .append(": ").append(corner.translatedName());
    }

    /** 数值循环按钮：显示"名称: 当前值"，点击切到下一个预设。 */
    private Button cycleButton(String key, int[] presets, int current, java.util.function.IntConsumer setter,
                               int x, int y, int w) {
        int initial = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                initial = i;
                break;
            }
        }
        final int[] idx = {initial};
        Button btn = Button.builder(cycleLabel(key, presets[idx[0]]), b -> {
            idx[0] = (idx[0] + 1) % presets.length;
            setter.accept(presets[idx[0]]);
            b.setMessage(cycleLabel(key, presets[idx[0]]));
        }).bounds(x, y, w, 20).build();
        return btn;
    }

    private Component cycleLabel(String key, int value) {
        return Component.translatable("blackannins_statistics.configuration." + key)
                .append(": ").append(String.valueOf(value));
    }

    @Override
    public void onClose() {
        if (dirBox != null) {
            String v = dirBox.getValue().trim();
            if (!v.equals(BasClientConfig.DATA_DIRECTORY_OVERRIDE.get())) {
                BasClientConfig.DATA_DIRECTORY_OVERRIDE.set(v);
            }
        }
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** 布尔配置项的小结构：标签 + 读取 + 写入。 */
    private record BooleanOption(String key, java.util.function.BooleanSupplier getter,
                                 java.util.function.Consumer<Boolean> setter) {
        Component label() {
            return Component.translatable("blackannins_statistics.configuration." + key)
                    .append(": ").append(getter.getAsBoolean()
                            ? Component.translatable("gui.bas.config.on")
                            : Component.translatable("gui.bas.config.off"));
        }

        void toggle() {
            setter.accept(!getter.getAsBoolean());
        }
    }
}
