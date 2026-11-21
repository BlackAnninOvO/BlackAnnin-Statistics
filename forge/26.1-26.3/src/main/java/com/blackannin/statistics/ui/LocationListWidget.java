package com.blackannin.statistics.ui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 位置列表（存档/服务器）：两行式条目（名称 + 摘要），点击进入该位置统计页；
 * 管理模式下每行右侧渲染一个"删除"文字按钮。
 * 26.x：条目渲染走 extractContent，点击走 MouseButtonEvent。
 */
public final class LocationListWidget extends ObjectSelectionList<LocationListWidget.Entry> {
    private final net.minecraft.client.gui.Font listFont;

    public LocationListWidget(Minecraft minecraft, int width, int height, int top) {
        super(minecraft, width, height, top, 36);
        this.listFont = minecraft.font;
    }

    /** 公开的添加条目入口（addEntry 是 protected）。 */
    public void add(Entry entry) {
        addEntry(entry);
    }

    @Override
    public int getRowWidth() {
        return Math.min(380, this.width - 40);
    }

    public Entry createEntry(Component title, Component subtitle, @Nullable String address,
                             @Nullable Runnable openAction, @Nullable Runnable deleteAction) {
        return new Entry(title, subtitle, address, openAction, deleteAction);
    }

    public final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Component title;
        private final Component subtitle;
        @Nullable
        private final String address;
        @Nullable
        private final Runnable openAction;
        @Nullable
        private final Runnable deleteAction;

        private Entry(Component title, Component subtitle, @Nullable String address,
                      @Nullable Runnable openAction, @Nullable Runnable deleteAction) {
            this.title = title;
            this.subtitle = subtitle;
            this.address = address;
            this.openAction = openAction;
            this.deleteAction = deleteAction;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int h = getContentHeight();
            int w = getContentWidth();
            g.text(listFont, title, x + 2, y + 4, 0xFFFFFFFF);
            if (!subtitle.getString().isEmpty()) {
                g.text(listFont, subtitle, x + 2, y + h - 12, 0xFF909090);
            }
            if (address != null && !address.isBlank()) {
                String addr = "[" + address + "]";
                int aw = listFont.width(addr);
                g.text(listFont, addr, x + w - aw - deleteButtonWidth() - 8, y + 4, 0xFF707070);
            }
            if (deleteAction != null) {
                String del = Component.translatable("gui.bas.manage.delete").getString();
                int dw = listFont.width(del);
                int dx = x + w - dw - 4;
                int dy = y + h - 12;
                boolean hover = mouseX >= dx - 3 && mouseX <= x + w - 1 && mouseY >= dy - 2 && mouseY <= dy + 11;
                g.text(listFont, del, dx, dy, hover ? 0xFFFF6060 : 0xFFA04040);
            }
        }

        private int deleteButtonWidth() {
            return deleteAction == null ? 0
                    : listFont.width(Component.translatable("gui.bas.manage.delete").getString()) + 8;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return super.mouseClicked(event, doubleClick);
            }
            double mouseX = event.x();
            double mouseY = event.y();
            if (!isMouseOver(mouseX, mouseY)) {
                return false;
            }
            int x = getContentX();
            int w = getContentWidth();
            if (deleteAction != null) {
                String del = Component.translatable("gui.bas.manage.delete").getString();
                int dw = listFont.width(del);
                int dx = x + w - dw - 4;
                if (mouseX >= dx - 4) {
                    deleteAction.run();
                    return true;
                }
            }
            if (openAction != null) {
                openAction.run();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", title);
        }
    }
}
