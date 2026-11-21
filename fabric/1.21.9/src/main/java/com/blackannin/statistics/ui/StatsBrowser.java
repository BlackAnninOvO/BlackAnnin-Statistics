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
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * 由宿主 Screen 持有并通过 {@link Host} 提供受保护成员的访问桥，
 * 宿主在自己的 init() 中调用 {@link #init(int, int)} 完成装配。
 */
public final class StatsBrowser {
    private static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    private static final int ROW_WIDTH = 380;
    private static final int COL_WIDTH = 58;
    private static final int ICON_SIZE = 18;

    /** 宿主 Screen 访问桥（addRenderableWidget/removeWidget/font 是 protected）。 */
    public interface Host {
        void addWidget(AbstractWidget widget);

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

    /** 置顶索引（跨游戏端共享，随分类页签使用对应分类键；与档案信息同存 profiles.json）。 */
    @Nullable
    private ProfileIndex pins;

    @Nullable
    private BasList activeList;
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
        sortDescending = category != Category.GENERAL; // 数值列默认降序更直观，通用页默认名称升序
        rebuildListWidget();
    }

    /** 数据可能已更新（刷新按钮/切换档案）：重建列表条目。 */
    public void refresh() {
        refreshEntries();
    }

    private void rebuildListWidget() {
        if (activeList != null) {
            int top = activeList.getY();
            int bottom = top + activeList.getHeight();
            host.removeWidget(activeList);
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
        activeList = new BasList(host.minecraft(), host.screenWidth(), bottomY - topY, topY,
                category == Category.GENERAL ? 16 : 22, cols);
        host.addWidget(activeList);
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

    /**
     * 名称解析回退：模组注册但未提供语言条目的内容（会显示成 "block.xxx.yyy" 原始键名）
     * 回退为可读的人性化名称。
     */
    private Component resolvableName(Component original, String regId) {
        if (original.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && !net.minecraft.client.resources.language.I18n.exists(tc.getKey())) {
            return Component.literal(UiFormat.humanizeStatId(regId));
        }
        return original;
    }

    private List<Row> buildGeneralRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        // 通用页同时展示原版 custom 指标与本模组本地扩展指标（如现实游玩时长）
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
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
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
            // 无 BlockItem 的方块（asItem()==AIR）没有物品形态图标，改用方块本身的 3D 外观
            Item item = block.asItem();
            ItemStack icon = item == Items.AIR ? null : new ItemStack(item);
            rows.add(new Row(id, icon, icon == null ? block : null, name, new long[]{mined, placed},
                    new String[]{UiFormat.num(mined), UiFormat.num(placed)}));
        }
        return rows;
    }

    private List<Row> buildItemRows(StatSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
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
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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
        // 置顶条目恒排最前，其余按当前排序规则
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
        /** 行主键（注册表 id / 统计目标 id），作为置顶索引键；无键行不参与置顶。 */
        @Nullable
        private final String rowKey;
        @Nullable
        private final ItemStack icon;
        /** icon 为空时的方块形态图标（无 BlockItem 的方块）。 */
        @Nullable
        private final Block blockIcon;
        private final Component name;
        private final long[] values;
        private final String[] display;

        Row(@Nullable String rowKey, @Nullable ItemStack icon, Component name, long[] values, String[] display) {
            this(rowKey, icon, null, name, values, display);
        }

        Row(@Nullable String rowKey, @Nullable ItemStack icon, @Nullable Block blockIcon,
            Component name, long[] values, String[] display) {
            this.rowKey = rowKey;
            this.icon = icon;
            this.blockIcon = blockIcon;
            this.name = name;
            this.values = values;
            this.display = display;
        }

        private boolean isPinned() {
            return rowKey != null && pins != null && pins.isPinned(category.name(), rowKey);
        }

        /** 行在列表中的序号（1.21.9 的 renderContent 不再传入 index，斑马纹配色改由加入列表时记录）。 */
        private int renderIndex;

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // 1.21.9：Entry 渲染抽象方法改为 renderContent(GuiGraphics, mouseX, mouseY, hovered, partialTick)，
            // 行位置/尺寸由列表预先写入条目自身（getX/getY/getWidth/getHeight）
            int left = this.getX();
            int top = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            Font f = host.font();
            int color = renderIndex % 2 == 0 ? 0xFFFFFFFF : 0xFFD0D0D0;
            int textY = top + height / 2 - 9 / 2;
            int nameX = left + 2;
            int iconY = top + height / 2 - ICON_SIZE / 2;
            if (icon != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, left, iconY, ICON_SIZE, ICON_SIZE);
                g.renderFakeItem(icon, left + 1, iconY + 1);
                nameX = left + ICON_SIZE + 4;
            } else if (blockIcon != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, left, iconY, ICON_SIZE, ICON_SIZE);
                // 1.21.6+ GUI 改为提交式渲染（GuiRenderState/GuiRenderer），screen.render 期间
                // 无即时 GL 矩阵上下文，原先基于 PoseStack 的方块 3D 图标无法继续实现；
                // 兜底：仅保留空槽位背景精灵图，不再渲染 3D 外观，名称缩进保持与其他行对齐（行为差异见移植报告）。
                nameX = left + ICON_SIZE + 4;
            }
            if (isPinned()) {
                g.drawString(f, "★", nameX, textY, 0xFFFFD700);
                nameX += f.width("★") + 2;
            }
            int nameWidth = valueColumnX(display.length - 1 > 0 ? display.length - 1 : 0, left, width) - nameX - 6;
            Component shown = Component.literal(
                    f.plainSubstrByWidth(name.getString(), Math.max(nameWidth, 20)));
            g.drawString(f, shown, nameX, textY, color);
            for (int i = 0; i < display.length; i++) {
                String v = display[i];
                // 表头 columns[0] 在最左列，数据 display[0] 须同样落在最左列：
                // valueColumnX 以"距右列数"定位，因此用 length-1-i 反转索引
                int cx = valueColumnX(display.length - 1 - i, left, width);
                g.drawString(f, v, cx - f.width(v) - 2, textY, v.equals("0") ? 0xFF707070 : color);
            }
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select",
                    Component.empty().append(name).append(" ").append(display.length > 0 ? display[0] : ""));
        }
    }

    // ---- 列表控件 ----

    private final class BasList extends ObjectSelectionList<Row> {
        final Column[] columns;
        private final int headerHeight;
        private final int rowHeight;

        BasList(Minecraft mc, int width, int height, int top, int itemHeight, Column[] columns) {
            // 1.21.9：ObjectSelectionList 仅保留 5 参构造器，表头占位不再由父类管理（见 renderWidget）
            super(mc, width, height, top, itemHeight);
            this.columns = columns;
            this.headerHeight = columns.length > 0 ? 14 : 0;
            this.rowHeight = itemHeight;
        }

        void setEntries(List<Row> rows) {
            clearEntries();
            // 1.21.9：renderContent 不再传入行序号，加入列表时记录（斑马纹配色用，语义与原 render(index,…) 一致）
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).renderIndex = i;
                addEntry(rows.get(i));
            }
        }

        int rowTop(Row row) {
            return getRowTop(children().indexOf(row));
        }

        private Row rowAtScreenY(double mouseY) {
            int count = children().size();
            if (count == 0) {
                return null;
            }
            int i = (int) Math.floor((mouseY - this.getY() - headerHeight + this.scrollAmount()) / rowHeight);
            if (i < 0 || i >= count) {
                return null;
            }
            return children().get(i);
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, host.screenWidth() - 40);
        }

        /**
         * 1.21.9 移除了列表表头机制（1.21.5 的 setRenderHeader/renderHeader）：
         * 正文整体下移 headerHeight 后按父类逻辑渲染（scissor 矩形随 pose 平移，
         * 条目定位/命中测试/滚动条与渲染坐标保持同一空间），表头固定绘制在列表顶部。
         * 行为差异：原表头随内容滚动移出视口，现固定于滚动区上方、列标签始终可见。
         */
        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (headerHeight <= 0) {
                super.renderWidget(g, mouseX, mouseY, partialTick);
                return;
            }
            // 限制滚动区不越出控件底界（父类 scissor 经平移后底边会超出 headerHeight）
            g.enableScissor(this.getX(), this.getY() + headerHeight, this.getRight(), this.getBottom());
            g.pose().pushMatrix();
            g.pose().translate(0, headerHeight);
            super.renderWidget(g, mouseX, mouseY - headerHeight, partialTick);
            g.pose().popMatrix();
            g.disableScissor();
            renderHeader(g, getRowLeft(), this.getY() + 4);
        }

        private void renderHeader(GuiGraphics g, int x, int y) {
            Font f = host.font();
            for (int i = 0; i < columns.length; i++) {
                int fromRight = columns.length - 1 - i;
                String label = Component.translatable(columns[i].headerKey()).getString();
                boolean active = sortColumn == i;
                String text = label + (active ? (sortDescending ? " ↓" : " ↑") : "");
                int w = f.width(text);
                int cx = valueColumnX(fromRight, getRowLeft(), getRowWidth());
                g.drawString(f, text, cx - w - 2, y, active ? 0xFFFFFFFF : 0xFFA0A0A0);
            }
            String nameLabel = Component.translatable("gui.bas.col.name").getString();
            String nameText = nameLabel + (sortColumn < 0 ? (sortDescending ? " ↓" : " ↑") : "");
            g.drawString(f, nameText, x + 2, y, sortColumn < 0 ? 0xFFFFFFFF : 0xFFA0A0A0);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean selected) {
            // 1.21.9：点击事件参数由 (double, double, int) 改为 (MouseButtonEvent, boolean)
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            // 右键：切换点击行的置顶状态（由屏幕 y 反查行，兼容滚动）
            if (button == 1 && pins != null) {
                Row row = rowAtScreenY(mouseY);
                if (row != null && row.rowKey != null) {
                    pins.togglePin(category.name(), row.rowKey, StoragePaths.get().profilesFile());
                    refreshEntries();
                    return true;
                }
            }
            // 表头点击排序
            if (button == 0 && columns.length > 0 && mouseY >= this.getY() && mouseY < this.getY() + headerHeight) {
                int rowLeft = getRowLeft();
                int rowWidth = getRowWidth();
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
            }
            // 正文：渲染时整体下移了 headerHeight，行命中测试按同一平移还原坐标
            return super.mouseClicked(new MouseButtonEvent(mouseX, mouseY - headerHeight,
                    new MouseButtonInfo(button, event.modifiers())), selected);
        }
    }
}
