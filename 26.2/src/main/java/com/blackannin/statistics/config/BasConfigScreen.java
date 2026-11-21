package com.blackannin.statistics.config;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 模组设置界面：等价 NeoForge 版通过模组列表配置入口（ConfigurationScreen 扩展点）提供的功能。
 * 两页布局：通用 / 本次游玩小窗；所有修改即时生效，点击"完成"（或 ESC 返回）时持久化到
 * config/blackannins_statistics.json（等价 ModConfigEvent.Reloading 语义）。
 * 入口：/bas config 命令。
 */
public final class BasConfigScreen extends Screen {
    private static final int PAGE_GENERAL = 0;
    private static final int PAGE_HUD = 1;

    private final Screen parent;
    private int page = PAGE_GENERAL;

    /** 保存间隔的可选值序列（循环切换）。 */
    private static final List<Integer> SAVE_INTERVALS = List.of(10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600);
    private static final List<Integer> SYNC_INTERVALS = List.of(5, 10, 15, 30, 45, 60, 120, 300, 600);
    private static final List<Integer> OPACITY_STEPS = List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);

    private EditBox dataDirBox;

    public BasConfigScreen(Screen parent) {
        super(Component.translatable("blackannins_statistics.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        if (page == PAGE_GENERAL) {
            addToggle(cx, 40, BasClientConfig.REPLACE_VANILLA_STATS_SCREEN);
            addCycleInt(cx, 64, BasClientConfig.SAVE_INTERVAL_SECONDS, SAVE_INTERVALS);
            addCycleInt(cx, 88, BasClientConfig.SYNC_INTERVAL_SECONDS, SYNC_INTERVALS);
            // 数据目录覆盖：行内编辑框（需重启生效）
            this.dataDirBox = new EditBox(this.font, cx - 150, 110, 300, 20,
                    Component.translatable("blackannins_statistics.configuration.dataDirectoryOverride"));
            this.dataDirBox.setMaxLength(260);
            this.dataDirBox.setValue(BasClientConfig.DATA_DIRECTORY_OVERRIDE.get());
            this.dataDirBox.setResponder(BasClientConfig.DATA_DIRECTORY_OVERRIDE::set);
            this.addRenderableWidget(this.dataDirBox);
            addToggle(cx, 152, BasClientConfig.AGGREGATE_ALL_PROFILES_BY_DEFAULT);
        } else {
            addToggle(cx, 40, BasClientConfig.SESSION_HUD_ENABLED);
            // 半宽两列：不透明度 + 角落
            int halfLeft = cx - 150;
            int halfRight = cx + 10;
            this.addRenderableWidget(cycleIntButton(halfLeft, 64, 140,
                    BasClientConfig.SESSION_HUD_BACKGROUND_OPACITY, OPACITY_STEPS));
            this.addRenderableWidget(cycleEnumButton(halfRight, 64, 140,
                    BasClientConfig.SESSION_HUD_CORNER));
            // 八个显示开关：两列四行
            BasClientConfig.BoolValue[] shows = {
                    BasClientConfig.SESSION_HUD_SHOW_REAL_TIME, BasClientConfig.SESSION_HUD_SHOW_GAME_TIME,
                    BasClientConfig.SESSION_HUD_SHOW_PLACED, BasClientConfig.SESSION_HUD_SHOW_MINED,
                    BasClientConfig.SESSION_HUD_SHOW_PICKED_UP, BasClientConfig.SESSION_HUD_SHOW_CRAFTED,
                    BasClientConfig.SESSION_HUD_SHOW_DEATHS, BasClientConfig.SESSION_HUD_SHOW_MOB_KILLS};
            for (int i = 0; i < shows.length; i++) {
                int colX = (i % 2 == 0) ? cx - 160 : cx + 10;
                this.addRenderableWidget(toggleButton(colX, 92 + (i / 2) * 24, 150, shows[i]));
            }
        }

        // 底栏：切换页面 + 完成（完成时持久化）
        this.addRenderableWidget(Button.builder(
                        Component.translatable(page == PAGE_GENERAL ? "gui.bas.config.page.hud" : "gui.bas.config.page.general"),
                        b -> {
                            page = page == PAGE_GENERAL ? PAGE_HUD : PAGE_GENERAL;
                            rebuildWidgets();
                        })
                .bounds(cx - 155, this.height - 26, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 5, this.height - 26, 150, 20).build());
    }

    private void addToggle(int centerX, int y, BasClientConfig.BoolValue value) {
        this.addRenderableWidget(toggleButton(centerX - 155, y, 310, value));
    }

    private static Button toggleButton(int x, int y, int w, BasClientConfig.BoolValue value) {
        Button button = Button.builder(toggleLabel(value), btn -> {
            value.set(!value.get());
            btn.setMessage(toggleLabel(value));
        }).bounds(x, y, w, 20).build();
        return button;
    }

    private static Component toggleLabel(BasClientConfig.BoolValue value) {
        String onOffKey = value.get() ? "options.on" : "options.off";
        return Component.translatable(value.labelKey()).append(": ")
                .append(Component.translatable(onOffKey));
    }

    private void addCycleInt(int centerX, int y, BasClientConfig.IntValue value, List<Integer> steps) {
        this.addRenderableWidget(cycleIntButton(centerX - 155, y, 310, value, steps));
    }

    private static Button cycleIntButton(int x, int y, int w, BasClientConfig.IntValue value, List<Integer> steps) {
        Button button = Button.builder(cycleIntLabel(value), btn -> {
            int idx = steps.indexOf(value.get());
            idx = idx < 0 ? 0 : (idx + 1) % steps.size();
            value.set(steps.get(idx));
            btn.setMessage(cycleIntLabel(value));
        }).bounds(x, y, w, 20).build();
        return button;
    }

    private static Component cycleIntLabel(BasClientConfig.IntValue value) {
        return Component.translatable(value.labelKey()).append(": " + value.get());
    }

    private static Button cycleEnumButton(int x, int y, int w, BasClientConfig.EnumValue<BasClientConfig.HudCorner> value) {
        Button button = Button.builder(cycleEnumLabel(value), btn -> {
            value.set(value.next());
            btn.setMessage(cycleEnumLabel(value));
        }).bounds(x, y, w, 20).build();
        return button;
    }

    private static Component cycleEnumLabel(BasClientConfig.EnumValue<BasClientConfig.HudCorner> value) {
        return Component.translatable(value.labelKey()).append(": ").append(value.get().translatedName());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 26.1：Screen 渲染入口由 render(GuiGraphics, …) 改为 extractRenderState(GuiGraphicsExtractor, …)，
        // drawCenteredString 更名 centeredText
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (page == PAGE_GENERAL) {
            g.centeredText(this.font,
                    Component.translatable("gui.bas.config.data_dir_hint").getString(),
                    this.width / 2, 134, 0xFFA0A0A0);
            g.centeredText(this.font,
                    Component.translatable("gui.bas.config.restart_hint").getString(),
                    this.width / 2, 178, 0xFF808080);
        }
    }

    @Override
    public void onClose() {
        BasClientConfig.save();
        // 26.2：Minecraft#setScreen 移除，统一走 setScreenAndShow（内部经 Gui#setScreen）
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
