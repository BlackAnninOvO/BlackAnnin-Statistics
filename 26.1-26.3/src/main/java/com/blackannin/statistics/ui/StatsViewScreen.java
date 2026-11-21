package com.blackannin.statistics.ui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.stat.StatSnapshot;

/**
 * 单个范围的统计视图（某个存档 / 某个服务器 / 单人全部 / 多人全部）。
 * 内嵌 {@link StatsBrowser}，支持档案切换与手动刷新。
 */
public final class StatsViewScreen extends Screen implements StatsBrowser.Host {
    private final Screen parent;
    private final String scopeTitle;
    private final Supplier<StatSnapshot> data;
    private StatsBrowser browser;

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 30;

    public StatsViewScreen(Screen parent, String scopeTitle, Supplier<StatSnapshot> data) {
        super(Component.translatable("gui.bas.title"));
        this.parent = parent;
        this.scopeTitle = scopeTitle;
        this.data = data;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        browser = new StatsBrowser(this, data);
        browser.init(HEADER_H, this.height - FOOTER_H);

        this.addRenderableWidget(Button.builder(profileButtonLabel(), b ->
                        this.minecraft.gui.setScreen(new ProfileSelectorScreen(this)))
                .bounds(this.width - 150, 8, 142, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.bas.refresh"), b -> browser.refresh())
                .bounds(this.width - 150, 30, 142, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(cx - 75, this.height - 26, 150, 20).build());
    }

    // ---- StatsBrowser.Host ----

    @Override
    public void addWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
    }

    @Override
    public <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> void addListWidget(T widget) {
        this.addRenderableWidget(widget);
    }

    @Override
    public void removeWidget(GuiEventListener widget) {
        super.removeWidget(widget);
    }

    @Override
    public Minecraft minecraft() {
        return this.minecraft;
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public int screenWidth() {
        return this.width;
    }

    @Override
    public int screenHeight() {
        return this.height;
    }

    private Component profileButtonLabel() {
        String id = UiState.selectedProfileId();
        return id == null
                ? Component.translatable("gui.bas.profile.all")
                : Component.translatable("gui.bas.profile.selected", shortProfileName(id));
    }

    private String shortProfileName(String profileId) {
        if (this.minecraft.player != null
                && this.minecraft.player.getUUID().toString().equals(profileId)) {
            return this.minecraft.getUser().getName();
        }
        return profileId.substring(0, Math.min(8, profileId.length()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
        g.centeredText(this.font, scopeTitle, this.width / 2, 22, 0xFFC0C0C0);
        // 置顶操作提示（独立显示于右下角空白处）
        String hint = Component.translatable("gui.bas.pin.hint").getString();
        g.text(this.font, hint, this.width - this.font.width(hint) - 6, this.height - 14, 0xFF808080);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
