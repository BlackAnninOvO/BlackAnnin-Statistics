package com.blackannin.statistics.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.storage.ProfileIndex;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 统计数据浏览器：分类（通用/方块/物品/生物）+ 搜索 + 列排序。
 * 1.21.9+：列表条目渲染改用 renderContent（内容坐标由列表提供）；
 * 列表内置表头机制被移除，改为独立的 ColumnHeaderBar 挂件（跨版本稳定）。
 */
public final class StatsBrowser {
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final int ROW_WIDTH = 380;
    private static final int COL_WIDTH = 58;
    private static final int ICON_SIZE = 18;
    private static final int HEADER_H = 14;

    /** 宿主 Screen 访问桥（addRenderableWidget/removeWidget/font 是 protected）。 */
    public interface Host {
        void addWidget(AbstractWidget widget);

        <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> void addListWidget(T widget);

        void removeWidget(GuiEventListener widget);

        Minecraft minecraft();

        Font font();

        int screenWidth();

        int screenHeight();
    }

    public enum Category { GENERAL, BLOCKS, ITEMS, MOBS }

    /** 一列：表头翻译键 + 数据来源统计类型 id。 */
    private record Column(String headerKey, String typeId) {}

    private static final Column[] BLOCK_COLS = {
            new Column("gui.bas.col.mined", "minecraft:mined"),
            new Column("gui.bas.col.placed", "blackannins_statistics:placed"),
    };
    private static final Column[] ITEM_COLS = {
            new Column("gui.bas.col.crafted", "minecraft:crafted"),
            new Column("gui.bas.col.used", "minecraft:used"),
            new Column("gui.bas.col.broken", "minecraft:broken"),
            new Column("gui.bas.col.picked_up", "minecraft:picked_up"),
            new Column("gui.bas.col.dropped", "minecraft:dropped"),
    };
    private static final Column[] MOB_COLS = {
            new Column("gui.bas.col.killed", "minecraft:killed"),
            new Column("gui.bas.col.killed_by", "minecraft:killed_by"),
    };

    private final Host host;
    private final Supplier<StatSnapshot> data;

    private Category category = Category.GENERAL;
    private String searchText = "";
    private int sortColumn = -1;      // -1 = 名称列
    private boolean sortDescending = true;

    @Nullable
    private ProfileIndex pins;

    @Nullable
    private BasList activeList;
    @Nullable
    private ColumnHeaderBar headerBar;
    @Nullable
    private EditBox searchBox;

    public StatsBrowser(Host host, Supplier<StatSnapshot> data) {
        this.host = host;
        this.data = data;
    }

    public Category category() {
        return category;
    }

    /** 宿主 Screen.init() 调用。topY 为分类按钮行 y，bottomY 为列表底界。 */
    public void init(int topY, int bottomY) {
        int width = host.screenWidth();
        int x = width / 2 - (4 * 72 + 100) / 2;
        Category[] cats = Category.values();
        String[] keys = {"gui.bas.tab.general", "gui.bas.tab.blocks", "gui.bas.tab.items", "gui.bas.tab.mobs"};
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            host.addWidget(Button.builder(Component.translatable(keys[i]), b -> switchCategory(c))
                    .bounds(x, topY, 68, 20).build());
            x += 72;
        }
        searchBox = new EditBox(host.font(), x + 6, topY, 100, 20, Component.translatable("gui.bas.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        searchBox.setResponder(t -> {
            searchText = t.toLowerCase(Locale.ROOT);
            refreshEntries();
        });
        host.addWidget(searchBox);
        pins = ProfileIndex.load(StoragePaths.get().profilesFile());
        buildList(topY + 24, bottomY);
    }

    private void switchCategory(Category c) {
        category = c;
        sortColumn = -1;
        sortDescending = category != Category.GENERAL;
        rebuildListWidget();
    }

    /** 数据可能已更新（刷新按钮/切换档案）：重建列表条目。 */
    public void refresh() {
        refreshEntries();
    }

    private void rebuildListWidget() {
        if (headerBar != null) {
            host.removeWidget(headerBar);
            headerBar = null;
        }
        if (activeList != null) {
            int top = activeList.listTop();
            int bottom = top + activeList.listHeight();
            host.removeWidget(activeList);
            activeList = null;
            buildList(top, bottom);
        }
    }

    private void buildList(int topY, int bottomY) {
        Column[] cols = switch (category) {
            case GENERAL -> new Column[0];
            case BLOCKS -> BLOCK_COLS;
            case ITEMS -> ITEM_COLS;
            case MOBS -> MOB_COLS;
        };
        int listTop = topY;
        if (cols.length > 0) {
            // 1.21.9+ 列表内置表头被移除：表头画在列表上方的独立挂件里
            headerBar = new ColumnHeaderBar(topY, HEADER_H, cols);
            host.addWidget(headerBar);
            listTop = topY + HEADER_H;
        }
        activeList = new BasList(host.minecraft(), host.screenWidth(), bottomY - listTop, listTop,
                category == Category.GENERAL ? 16 : 22, cols);
        host.addListWidget(activeList);
        refreshEntries();
    }

    private void refreshEntries() {
        if (activeList == null) {
            return;
        }
        StatSnapshot snapshot;
        try {
            snapshot = data.get();
        } catch (Exception e) {
            BlackAnninsStatistics.LOGGER.error("读取统计数据失败", e);
            snapshot = new StatSnapshot();
        }
        List<Row> rows = switch (category) {
            case GENERAL -> buildGeneralRows(snapshot);
            case BLOCKS -> buildBlockRows(snapshot);
            case ITEMS -> buildItemRows(snapshot);
            case MOBS -> buildMobRows(snapshot);
        };
        sortRows(rows);
        activeList.setEntries(rows);
    }

    private boolean matches(Component name, String targetId) {
        if (searchText.isEmpty()) {
            return true;
        }
        return name.getString().toLowerCase(Locale.ROOT).contains(searchText)
                || targetId.contains(searchText);
    }

    /** 名称解析回退：模组注册但未提供语言条目的内容回退为可读的人性化名称。 */
    private Component resolvableName(Component original, String regId) {
        if (original.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && !net.minecraft.client.resources.language.I18n.exists(tc.getKey())) {
            return Component.literal(UiFormat.humanizeStatId(regId));
        }
        return original;
    }

    private List<Row> buildGeneralRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        java.util.LinkedHashMap<String, Long> merged = new java.util.LinkedHashMap<>();
        merged.putAll(snapshot.getType("minecraft:custom"));
        snapshot.getType("blackannins_statistics:custom").forEach(merged::put);
        merged.forEach((targetId, value) -> {
            Component name = UiFormat.customStatName(targetId);
            if (matches(name, targetId)) {
                rows.add(new Row(targetId, null, name, new long[]{value},
                        new String[]{UiFormat.custom(targetId, value)}));
            }
        });
        return rows;
    }

    private List<Row> buildBlockRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            String id = key.toString();
            long mined = snapshot.get("minecraft:mined", id);
            long placed = snapshot.get("blackannins_statistics:placed", id);
            if (mined == 0 && placed == 0) {
                continue;
            }
            Component name = resolvableName(block.getName(), id);
            if (!matches(name, id)) {
                continue;
            }
            // 1.21.9+ 无 GUI 内 3D 模型入口：无 BlockItem 的方块（火/岩浆等极少数）只显示名称
            Item item = block.asItem();
            ItemStack icon = item == Items.AIR ? null : new ItemStack(item);
            rows.add(new Row(id, icon, name, new long[]{mined, placed},
                    new String[]{UiFormat.num(mined), UiFormat.num(placed)}));
        }
        return rows;
    }

    private List<Row> buildItemRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            String id = key.toString();
            long[] values = new long[ITEM_COLS.length];
            boolean any = false;
            for (int i = 0; i < ITEM_COLS.length; i++) {
                values[i] = snapshot.get(ITEM_COLS[i].typeId(), id);
                any |= values[i] > 0;
            }
            if (!any) {
                continue;
            }
            Component name = resolvableName(item.getName(), id);
            if (!matches(name, id)) {
                continue;
            }
            String[] display = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                display[i] = UiFormat.num(values[i]);
            }
            rows.add(new Row(id, new ItemStack(item), name, values, display));
        }
        return rows;
    }

    private List<Row> buildMobRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String id = key.toString();
            long killed = snapshot.get("minecraft:killed", id);
            long killedBy = snapshot.get("minecraft:killed_by", id);
            if (killed == 0 && killedBy == 0) {
                continue;
            }
            Component name = resolvableName(type.getDescription(), id);
            if (!matches(name, id)) {
                continue;
            }
            rows.add(new Row(id, null, name, new long[]{killed, killedBy},
                    new String[]{UiFormat.num(killed), UiFormat.num(killedBy)}));
        }
        return rows;
    }

    private void sortRows(List<Row> rows) {
        Comparator<Row> primary;
        if (sortColumn < 0) {
            primary = Comparator.comparing(r -> r.name.getString(), String.CASE_INSENSITIVE_ORDER);
        } else {
            primary = Comparator.comparingLong((Row r) -> r.values[Math.min(sortColumn, r.values.length - 1)]);
        }
        if (sortDescending) {
            primary = primary.reversed();
        }
        Set<String> pinned = pins != null ? pins.pinnedKeys(category.name()) : java.util.Set.of();
        Comparator<Row> cmp = Comparator
                .comparingInt((Row r) -> r.rowKey != null && pinned.contains(r.rowKey) ? 0 : 1)
                .thenComparing(primary);
        rows.sort(cmp);
    }

    private void onHeaderClicked(int columnIndex) {
        if (sortColumn == columnIndex) {
            sortDescending = !sortDescending;
        } else {
            sortColumn = columnIndex;
            sortDescending = columnIndex >= 0;
        }
        refreshEntries();
    }

    /** 各列右边界 x（columnFromRight：0 = 最右列）。 */
    private int valueColumnX(int columnFromRight, int rowLeft, int rowWidth) {
        return rowLeft + rowWidth - 4 - columnFromRight * COL_WIDTH;
    }

    // ---- 行数据 ----

    final class Row extends ObjectSelectionList.Entry<Row> {
        @Nullable
        private final String rowKey;
        @Nullable
        private final ItemStack icon;
        private final Component name;
        private final long[] values;
        private final String[] display;

        Row(@Nullable String rowKey, @Nullable ItemStack icon, Component name, long[] values, String[] display) {
            this.rowKey = rowKey;
            this.icon = icon;
            this.name = name;
            this.values = values;
            this.display = display;
        }

        private boolean isPinned() {
            return rowKey != null && pins != null && pins.isPinned(category.name(), rowKey);
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font f = host.font();
            int x = getContentX();
            int y = getContentY();
            int h = getContentHeight();
            int w = getContentWidth();
            int idx = list != null ? list.children().indexOf(this) : 0;
            int color = idx % 2 == 0 ? 0xFFFFFFFF : 0xFFD0D0D0;
            int textY = y + h / 2 - 9 / 2;
            int nameX = x + 2;
            int iconY = y + h / 2 - ICON_SIZE / 2;
            if (icon != null) {
                blitSlotSprite(g, x, iconY);
                g.renderItem(icon, x + 1, iconY + 1);
                nameX = x + ICON_SIZE + 4;
            }
            if (isPinned()) {
                g.drawString(f, "★", nameX, textY, 0xFFFFD700);
                nameX += f.width("★") + 2;
            }
            int nameWidth = valueColumnX(display.length - 1 > 0 ? display.length - 1 : 0, x, w) - nameX - 6;
            Component shown = Component.literal(
                    f.plainSubstrByWidth(name.getString(), Math.max(nameWidth, 20)));
            g.drawString(f, shown, nameX, textY, color);
            for (int i = 0; i < display.length; i++) {
                String v = display[i];
                int cx = valueColumnX(display.length - 1 - i, x, w);
                g.drawString(f, v, cx - f.width(v) - 2, textY, v.equals("0") ? 0xFF707070 : color);
            }
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select",
                    Component.empty().append(name).append(" ").append(display.length > 0 ? display[0] : ""));
        }
    }

    /** 绘制槽位背景（1.21.9+：blitSprite 带 RenderPipeline 参数，RenderPipelines.GUI_TEXTURED）。 */
    private void blitSlotSprite(GuiGraphics g, int x, int y) {
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
    }

    // ---- 列表控件 ----

    private final class BasList extends ObjectSelectionList<Row> {
        final Column[] columns;
        private final int rowHeight;
        private final int listTop;
        private final int listHeight;

        BasList(Minecraft mc, int width, int height, int top, int itemHeight, Column[] columns) {
            super(mc, width, height, top, itemHeight);
            this.columns = columns;
            this.rowHeight = itemHeight;
            this.listTop = top;
            this.listHeight = height;
        }

        int listTop() {
            return listTop;
        }

        int listHeight() {
            return listHeight;
        }

        void setEntries(List<Row> rows) {
            clearEntries();
            rows.forEach(this::addEntry);
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, host.screenWidth() - 40);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            // 右键：切换点击行的置顶状态（列表按命中位置分发到本列表）
            if (event.button() == 1) {
                Row row = getEntryAtPosition(event.x(), event.y());
                if (row != null && row.rowKey != null && pins != null) {
                    pins.togglePin(category.name(), row.rowKey, StoragePaths.get().profilesFile());
                    refreshEntries();
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    /**
     * 列表头挂件（1.21.9+ 列表内置表头被移除）：绘制列标签 + 处理点击排序。
     * 位置在列表上方 14px 条带，几何对齐列表行（getRowLeft/getRowWidth）。
     */
    private final class ColumnHeaderBar extends AbstractWidget {
        private final Column[] columns;

        ColumnHeaderBar(int top, int height, Column[] columns) {
            super(0, top, host.screenWidth(), height, Component.empty());
            this.columns = columns;
        }

        private int listLeft() {
            return activeList != null ? activeList.getRowLeft() : 0;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }

        private int listWidth() {
            return activeList != null ? activeList.getRowWidth() : 0;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (activeList == null || columns.length == 0) {
                return;
            }
            Font f = host.font();
            int y = getY() + 3;
            for (int i = 0; i < columns.length; i++) {
                int fromRight = columns.length - 1 - i;
                String label = Component.translatable(columns[i].headerKey()).getString();
                boolean active = sortColumn == i;
                String text = label + (active ? (sortDescending ? " ↓" : " ↑") : "");
                int w = f.width(text);
                int cx = valueColumnX(fromRight, listLeft(), listWidth());
                g.drawString(f, text, cx - w - 2, y, active ? 0xFFFFFFFF : 0xFFA0A0A0);
            }
            String nameLabel = Component.translatable("gui.bas.col.name").getString();
            String nameText = nameLabel + (sortColumn < 0 ? (sortDescending ? " ↓" : " ↑") : "");
            g.drawString(f, nameText, listLeft() + 2, y, sortColumn < 0 ? 0xFFFFFFFF : 0xFFA0A0A0);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (activeList == null || columns.length == 0 || event.button() != 0) {
                return false;
            }
            double mouseX = event.x();
            double mouseY = event.y();
            if (mouseY < getY() || mouseY >= getY() + height) {
                return false;
            }
            int rowLeft = listLeft();
            int rowWidth = listWidth();
            for (int i = 0; i < columns.length; i++) {
                int fromRight = columns.length - 1 - i;
                int cx = valueColumnX(fromRight, rowLeft, rowWidth);
                if (mouseX >= cx - COL_WIDTH && mouseX < cx) {
                    onHeaderClicked(i);
                    return true;
                }
            }
            if (mouseX >= rowLeft && mouseX < rowLeft + rowWidth - columns.length * COL_WIDTH) {
                onHeaderClicked(-1);
                return true;
            }
            return false;
        }
    }
}
