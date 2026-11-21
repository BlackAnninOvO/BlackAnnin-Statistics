package com.blackannin.statistics.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.query.StatsRepository;
import com.blackannin.statistics.stat.LocationKind;
import com.blackannin.statistics.storage.ProfileIndex;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 档案选择器：多 ID 支持。列出数据目录中出现过的全部玩家档案，
 * 可切换查看视角（单个档案 / 所有档案合计），也可删除某档案的全部数据。
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

        int rowTop(Row row) {
            return getRowTop(children().indexOf(row));
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
            int left = getX();
            int top = getY();
            int width = getWidth();
            int height = getHeight();
            if (isAggregateRow()) {
                g.text(font, Component.translatable("gui.bas.profile.all")
                        .withStyle(ChatFormatting.AQUA), left + 2, top + height / 2 - 9 / 2, 0xFFFFFFFF);
                if (UiState.selectedProfileId() == null) {
                    g.text(font, "✓", left + width - 12, top + height / 2 - 9 / 2, 0xFFFFFFFF);
                }
                return;
            }
            boolean selected = data.uuid.equals(UiState.selectedProfileId());
            g.text(font, Component.literal(data.displayName + (selected ? " ✓" : "")),
                    left + 2, top + 4, selected ? 0xFF55FFFF : 0xFFFFFFFF);
            Component sub = Component.translatable("gui.bas.profile.sub",
                    UiFormat.time(data.playTimeTicks), UiFormat.dateTime(data.lastSeen),
                    data.uuid.substring(0, 8));
            g.text(font, sub, left + 2, top + height - 12, 0xFF909090);
            String del = Component.translatable("gui.bas.manage.delete").getString();
            int w = font.width(del);
            g.text(font, del, left + width - w - 4, top + height - 12,
                    mouseX >= left + width - w - 8 && mouseY >= top + height - 14 ? 0xFFFF6060 : 0xFFA04040);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (event.button() != 0) {
                return super.mouseClicked(event, doubleClick);
            }
            // 注意：Entry 自带 list 字段会遮蔽外部字段，必须显式限定
            ProfileList theList = ProfileSelectorScreen.this.list;
            int index = theList.children().indexOf(this);
            if (index < 0) {
                return false;
            }
            int rowTop = theList.rowTop(this);
            if (mouseY < rowTop || mouseY > rowTop + 36) {
                return false;
            }
            if (isAggregateRow()) {
                UiState.setSelectedProfileId(null);
                backToCaller();
                return true;
            }
            String del = Component.translatable("gui.bas.manage.delete").getString();
            int w = font.width(del);
            int rightEdge = theList.getRowLeft() + theList.getRowWidth();
            if (mouseX >= rightEdge - w - 8) {
                final String uuid = data.uuid;
                final String name = data.displayName;
                minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        deleteProfile(uuid);
                    }
                    minecraft.gui.setScreen(ProfileSelectorScreen.this);
                    rebuildWidgets();
                }, Component.translatable("gui.bas.profile.delete.confirm", name),
                        Component.translatable("gui.bas.profile.delete.detail"),
                        Component.translatable("gui.bas.manage.delete_yes"),
                        Component.translatable("gui.cancel")));
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
