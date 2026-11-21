package com.blackannin.statistics.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.query.StatsRepository;
import com.blackannin.statistics.stat.LocationKind;
import com.blackannin.statistics.storage.ProfileIndex;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 档案选择器：多 ID 支持。列出数据目录中出现过的全部玩家档案，
 * 可切换查看视角（单个档案 / 所有档案合计），也可删除某档案的全部数据。
 * 26.x：条目渲染走 extractContent，点击走 MouseButtonEvent；ConfirmScreen 三参构造。
 */
public final class ProfileSelectorScreen extends Screen {
    private final Screen parent;
    private final StatsRepository repo = new StatsRepository();
    private ProfileList list;

    private record ProfileRow(String uuid, String displayName, String names, long lastSeen, long playTimeTicks) {}

    private static final int LIST_TOP = 40;
    private static final int FOOTER_H = 30;

    public ProfileSelectorScreen(Screen parent) {
        super(Component.translatable("gui.bas.profile.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ProfileIndex profileIndex = ProfileIndex.load(StoragePaths.get().profilesFile());
        List<ProfileRow> rows = new ArrayList<>();
        for (String uuid : repo.listProfileIds()) {
            ProfileIndex.Entry entry = profileIndex.view()
                    .computeIfAbsent(uuid, k -> new ProfileIndex.Entry());
            String names = String.join(", ", entry.names);
            long playTime = repo.aggregate(null, uuid).get("minecraft:custom", "minecraft:play_time");
            String display = entry.names.isEmpty() ? uuid.substring(0, 8) : entry.names.iterator().next();
            rows.add(new ProfileRow(uuid, display, names, entry.lastSeen, playTime));
        }
        rows.sort(Comparator.comparingLong(ProfileRow::lastSeen).reversed());

        list = new ProfileList(minecraft, this.width, this.height - LIST_TOP - FOOTER_H, LIST_TOP);
        // null = "所有档案合计"固定首行
        list.add(new Row(null));
        for (ProfileRow r : rows) {
            list.add(new Row(r));
        }
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    final class ProfileList extends ObjectSelectionList<Row> {
        ProfileList(Minecraft mc, int width, int height, int top) {
            super(mc, width, height, top, 36);
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, this.width - 40);
        }
    }

    final class Row extends ObjectSelectionList.Entry<Row> {
        @Nullable
        private final ProfileRow data;

        Row(@Nullable ProfileRow data) {
            this.data = data;
        }

        private boolean isAggregateRow() {
            return data == null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int y = getContentY();
            int h = getContentHeight();
            int w = getContentWidth();
            if (isAggregateRow()) {
                g.text(font, Component.translatable("gui.bas.profile.all")
                        .withStyle(ChatFormatting.AQUA), x + 2, y + h / 2 - 9 / 2, 0xFFFFFFFF);
                if (UiState.selectedProfileId() == null) {
                    g.text(font, "✓", x + w - 12, y + h / 2 - 9 / 2, 0xFFFFFFFF);
                }
                return;
            }
            boolean selected = data.uuid.equals(UiState.selectedProfileId());
            g.text(font, Component.literal(data.displayName + (selected ? " ✓" : "")),
                    x + 2, y + 4, selected ? 0xFF55FFFF : 0xFFFFFFFF);
            Component sub = Component.translatable("gui.bas.profile.sub",
                    UiFormat.time(data.playTimeTicks), UiFormat.dateTime(data.lastSeen),
                    data.uuid.substring(0, 8));
            g.text(font, sub, x + 2, y + h - 12, 0xFF909090);
            String del = Component.translatable("gui.bas.manage.delete").getString();
            int dw = font.width(del);
            g.text(font, del, x + w - dw - 4, y + h - 12,
                    mouseX >= x + w - dw - 8 && mouseY >= y + h - 14 ? 0xFFFF6060 : 0xFFA04040);
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
            if (isAggregateRow()) {
                UiState.setSelectedProfileId(null);
                backToCaller();
                return true;
            }
            String del = Component.translatable("gui.bas.manage.delete").getString();
            int dw = font.width(del);
            if (mouseX >= x + w - dw - 8) {
                final String uuid = data.uuid;
                final String name = data.displayName;
                minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        deleteProfile(uuid);
                    }
                    minecraft.gui.setScreen(ProfileSelectorScreen.this);
                    rebuildWidgets();
                }, Component.translatable("gui.bas.profile.delete.confirm", name),
                        Component.translatable("gui.bas.profile.delete.detail")));
                return true;
            }
            UiState.setSelectedProfileId(data.uuid);
            backToCaller();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select",
                    isAggregateRow() ? Component.translatable("gui.bas.profile.all")
                            : Component.literal(data.displayName));
        }
    }

    private void deleteProfile(String uuid) {
        for (LocationKind kind : LocationKind.values()) {
            for (var s : repo.listLocations(kind)) {
                repo.deleteProfileData(kind, s.id(), uuid);
            }
        }
        ProfileIndex profileIndex = ProfileIndex.load(StoragePaths.get().profilesFile());
        profileIndex.remove(uuid);
        profileIndex.save(StoragePaths.get().profilesFile());
    }

    /** 返回调用方界面（setScreen 会重新执行 init，自然反映新的档案选择）。 */
    private void backToCaller() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
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
