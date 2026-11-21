package com.blackannin.statistics.ui;

import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.query.StatsRepository;
import com.blackannin.statistics.session.ClientStatsBridge;
import com.blackannin.statistics.stat.LocationKind;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 模组统计中心（接管原版"统计信息"入口）。
 * 四个分区：全局 / 单人游戏 / 多人游戏 / 管理；单人多人可下钻到每个存档或服务器。
 */
public final class StatisticsHubScreen extends Screen implements StatsBrowser.Host {
    public enum HubTab { GLOBAL, SINGLEPLAYER, MULTIPLAYER, MANAGE }

    private static final int HEADER_H = 30;
    private static final int TABS_H = 24;
    private static final int FOOTER_H = 30;

    private final Screen parent;
    private final StatsRepository repo = new StatsRepository();
    private HubTab tab = HubTab.GLOBAL;
    private StatsBrowser browser;

    public StatisticsHubScreen(Screen parent) {
        super(Component.translatable("gui.bas.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // 顶栏：标题 + 档案按钮
        this.addRenderableWidget(Button.builder(profileButtonLabel(), b ->
                        this.minecraft.setScreen(new ProfileSelectorScreen(this)))
                .bounds(this.width - 130, 8, 122, 20).build());

        // 分区标签
        HubTab[] tabs = HubTab.values();
        String[] keys = {"gui.bas.tab.global", "gui.bas.tab.singleplayer", "gui.bas.tab.multiplayer", "gui.bas.tab.manage"};
        int tabW = 100, gap = 4;
        int totalW = tabs.length * tabW + (tabs.length - 1) * gap;
        int x = cx - totalW / 2;
        for (int i = 0; i < tabs.length; i++) {
            HubTab t = tabs[i];
            Button btn = Button.builder(Component.translatable(keys[i]), b -> {
                if (tab != t) {
                    tab = t;
                    rebuildWidgets();
                }
            }).bounds(x, HEADER_H, tabW, 18).build();
            btn.active = tab != t;
            this.addRenderableWidget(btn);
            x += tabW + gap;
        }

        int contentTop = HEADER_H + TABS_H;
        int contentBottom = this.height - FOOTER_H;
        switch (tab) {
            case GLOBAL -> {
                browser = new StatsBrowser(this, () -> repo.aggregate(null, UiState.selectedProfileId()));
                browser.init(contentTop, contentBottom);
            }
            case SINGLEPLAYER -> addList(LocationKind.SINGLEPLAYER, contentTop, contentBottom);
            case MULTIPLAYER -> addList(LocationKind.MULTIPLAYER, contentTop, contentBottom);
            case MANAGE -> addManage(contentTop, contentBottom);
        }

        // 底栏：原版统计 + 完成
        this.addRenderableWidget(Button.builder(Component.translatable("gui.bas.vanilla"), b -> openVanillaStats())
                .bounds(cx - 155, this.height - 26, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx + 5, this.height - 26, 150, 20).build());
    }

    // ---- StatsBrowser.Host ----

    @Override
    public void addWidget(AbstractWidget widget) {
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
                : Component.translatable("gui.bas.profile.selected", profileDisplayName(id));
    }

    private String profileDisplayName(String profileId) {
        if (this.minecraft.player != null
                && this.minecraft.player.getUUID().toString().equals(profileId)) {
            return this.minecraft.getUser().getName();
        }
        for (LocationKind kind : LocationKind.values()) {
            for (var s : repo.listLocations(kind)) {
                for (var store : repo.loadLocationStores(kind, s.id(), profileId)) {
                    return store.meta.primaryName();
                }
            }
        }
        return profileId.substring(0, 8);
    }

    private void addList(LocationKind kind, int top, int bottom) {
        LocationListWidget list = new LocationListWidget(this.minecraft, this.width, bottom - top, top);
        list.add(list.createEntry(
                Component.translatable(kind == LocationKind.SINGLEPLAYER
                        ? "gui.bas.sp.all" : "gui.bas.mp.all"),
                Component.translatable("gui.bas.location.aggregate"), null,
                () -> openView(kind == LocationKind.SINGLEPLAYER
                        ? "gui.bas.sp.all" : "gui.bas.mp.all",
                        () -> repo.aggregate(kind, UiState.selectedProfileId())),
                null));
        for (StatsRepository.LocationSummary s : repo.listLocations(kind)) {
            String duration = s.realPlayTimeMillis() > 0
                    ? UiFormat.durationMillis(s.realPlayTimeMillis())
                    : UiFormat.time(s.playTimeTicks());
            Component sub = Component.translatable("gui.bas.location.sub",
                    UiFormat.dateTime(s.lastSeen()), s.sessions(), duration);
            if (s.serverStatReset()) {
                sub = sub.copy().append(" ")
                        .append(Component.translatable("gui.bas.location.reset").withStyle(ChatFormatting.GOLD));
            }
            list.add(list.createEntry(
                    Component.literal(s.displayName()), sub, s.address(),
                    () -> openViewTitle(s.displayName(),
                            () -> repo.aggregateLocation(kind, s.id(), UiState.selectedProfileId())),
                    null));
        }
        this.addRenderableWidget(list);
    }

    private void openView(String titleKey, Supplier<StatSnapshot> data) {
        openViewTitle(Component.translatable(titleKey).getString(), data);
    }

    private void openViewTitle(String title, Supplier<StatSnapshot> data) {
        this.minecraft.setScreen(new StatsViewScreen(this, title, data));
    }

    private void addManage(int top, int bottom) {
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.bas.manage.refresh"), b -> rebuildWidgets())
                .bounds(cx - 155, top + 46, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.bas.manage.clear_all"), b -> {
                    this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                        if (confirmed) {
                            repo.deleteAll();
                        }
                        this.minecraft.setScreen(this);
                        rebuildWidgets();
                    }, Component.translatable("gui.bas.manage.clear_all.confirm"),
                            Component.translatable("gui.bas.manage.clear_all.detail"),
                            Component.translatable("gui.bas.manage.delete_yes"),
                            Component.translatable("gui.cancel")));
                })
                .bounds(cx + 5, top + 46, 150, 20).build());

        LocationListWidget list = new LocationListWidget(this.minecraft, this.width, bottom - top - 72, top + 72);
        for (LocationKind kind : LocationKind.values()) {
            for (StatsRepository.LocationSummary s : repo.listLocations(kind)) {
                Component title = Component.literal(s.displayName())
                        .append(Component.literal("  "))
                        .append(Component.translatable(kind == LocationKind.SINGLEPLAYER
                                ? "gui.bas.kind.sp" : "gui.bas.kind.mp").withStyle(ChatFormatting.GRAY));
                String duration = s.realPlayTimeMillis() > 0
                        ? UiFormat.durationMillis(s.realPlayTimeMillis())
                        : UiFormat.time(s.playTimeTicks());
                Component sub = Component.translatable("gui.bas.location.sub",
                        UiFormat.dateTime(s.lastSeen()), s.sessions(), duration);
                Runnable delete = () -> this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        repo.deleteLocation(kind, s.id());
                    }
                    this.minecraft.setScreen(this);
                    rebuildWidgets();
                }, Component.translatable("gui.bas.manage.delete.confirm", s.displayName()),
                        Component.translatable("gui.bas.manage.delete.detail"),
                        Component.translatable("gui.bas.manage.delete_yes"),
                        Component.translatable("gui.cancel")));
                list.add(list.createEntry(title, sub, s.address(), null, delete));
            }
        }
        this.addRenderableWidget(list);
    }

    private void openVanillaStats() {
        Minecraft mc = this.minecraft;
        if (mc.player != null) {
            VanillaStatsGate.bypassNextStatsScreen();
            // 多人下先请求一次同步，保证原版界面有数据（原版界面 init 自己也会请求）
            if (!ClientStatsBridge.isInSingleplayer()) {
                ClientStatsBridge.requestStatsSync();
            }
            mc.setScreen(new StatsScreen(this, mc.player.getStats()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 26.1：Screen 渲染入口由 render(GuiGraphics, …) 改为 extractRenderState(GuiGraphicsExtractor, …)，
        // drawCenteredString/drawString 更名 centeredText/text
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        // 置顶操作提示（独立显示于右下角空白处）
        String pinHint = Component.translatable("gui.bas.pin.hint").getString();
        g.text(this.font, pinHint, this.width - this.font.width(pinHint) - 6, this.height - 14, 0xFF808080);
        if (tab == HubTab.MANAGE) {
            String dirLine = StoragePaths.get().isFallbackInUse()
                    ? Component.translatable("gui.bas.manage.dir_fallback", StoragePaths.get().root().toString()).getString()
                    : Component.translatable("gui.bas.manage.dir", StoragePaths.get().root().toString()).getString();
            g.centeredText(this.font, dirLine, this.width / 2, HEADER_H + TABS_H + 4, 0xFFA0A0A0);
            String hint = Component.translatable("gui.bas.manage.hint").getString();
            g.centeredText(this.font, hint, this.width / 2, HEADER_H + TABS_H + 24, 0xFF808080);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
