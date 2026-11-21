package com.blackannin.statistics.ui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * 位置列表（存档/服务器）：两行式条目（名称 + 摘要），点击进入该位置统计页；
 * 管理模式下每行右侧渲染一个"删除"文字按钮。
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
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            g.drawString(listFont, title, left + 2, top + 4, 0xFFFFFFFF);
            if (!subtitle.getString().isEmpty()) {
                g.drawString(listFont, subtitle, left + 2, top + height - 12, 0xFF909090);
            }
            if (address != null && !address.isBlank()) {
                String addr = "[" + address + "]";
                int aw = listFont.width(addr);
                g.drawString(listFont, addr, left + width - aw - deleteButtonWidth() - 8, top + 4, 0xFF707070);
            }
            if (deleteAction != null) {
                String del = Component.translatable("gui.bas.manage.delete").getString();
                int w = listFont.width(del);
                int x = left + width - w - 4;
                int y = top + height - 12;
                boolean hover = mouseX >= x - 3 && mouseX <= left + width - 1 && mouseY >= y - 2 && mouseY <= y + 11;
                g.drawString(listFont, del, x, y, hover ? 0xFFFF6060 : 0xFFA04040);
            }
        }

        private int deleteButtonWidth() {
            return deleteAction == null ? 0
                    : listFont.width(Component.translatable("gui.bas.manage.delete").getString()) + 8;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // 子条目作为 children 收到的事件不带位置过滤，这里自行校验行范围
            int index = LocationListWidget.this.children().indexOf(this);
            if (index < 0) {
                return false;
            }
            int rowTop = LocationListWidget.this.getRowTop(index);
            if (mouseY < rowTop || mouseY > rowTop + itemHeight) {
                return false;
            }
            if (deleteAction != null) {
                String del = Component.translatable("gui.bas.manage.delete").getString();
                int w = listFont.width(del);
                int x = getRowLeft() + getRowWidth() - w - 4;
                if (mouseX >= x - 4) {
                    deleteAction.run();
                    return true;
                }
            }
            if (openAction != null) {
                openAction.run();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", title);
        }
    }
}
