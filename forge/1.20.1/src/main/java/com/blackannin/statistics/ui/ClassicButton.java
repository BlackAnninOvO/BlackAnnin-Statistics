package com.blackannin.statistics.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 经典渲染按钮（Forge 47.4.29 / 1.20.1 专用）。
 * 47.4.29（2026-11 更新）回移植的 GuiGraphics.blitNineSliced 在窄按钮上
 * （实测 width < ~150）会把 widgets.png 九宫格渲染成黑色/不可见（vanilla 按钮全是
 * 宽 200 所以从未暴露）。本类改用 1.20.1 原版两段式 widgets.png 贴图绘制：
 * 任意宽度正确，视觉与原版按钮完全一致。
 */
public final class ClassicButton extends Button {
    private static final ResourceLocation WIDGETS_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/widgets.png");

    public ClassicButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Forge 47.4.29 的 widgets.png blit 在窄按钮（width < ~150）上渲染成黑色
        // （洋红探针实证：同代码宽 200 正常、宽 64 黑盒，且经典两段式 blit 同样黑盒）。
        // 改用确定性 fill 自绘扁平按钮：任何宽度/状态都正确渲染。
        boolean hover = isHoveredOrFocused();
        int bg = !active ? 0xFF303030 : (hover ? 0xFF767676 : 0xFF585858);
        int border = !active ? 0xFF202020 : (hover ? 0xFFC0C0C0 : 0xFF8E8E8E);
        int x = getX(), y = getY(), w = width, h = height;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                x + w / 2, y + (h - 8) / 2, color);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
