package com.blackannin.statistics.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.session.SessionStats;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.ui.UiFormat;

/**
 * "本次游玩"小窗：显示当前会话的实时增量统计（时长、放置、破坏、拾取、合成、死亡、击杀）。
 * 数据源为会话基线差值与放置确认计数，只读展示、不影响主体的累计数据；
 * 会话结束（离开世界/关闭客户端）后自动归零。按 F9（可在控件设置修改）切换显示。
 * 渲染逻辑与 NeoForge 版逐行一致，仅按键注册方式与配置事件来源不同（Fabric）。
 */
public final class SessionHud {

    /** 是否额外显示在游戏画面上（F9 切换，默认关闭：仅界面打开时显示）。 */
    private static boolean showInGame = false;

    private SessionHud() {}

    /**
     * 消费切换按键（由 ClientTick 驱动）。
     * NeoForge 版按键为 IN_GAME 冲突上下文（界面打开时不触发）；
     * Fabric 无冲突上下文机制，此处以"界面打开时只消费不切换"完全复刻该行为，
     * 避免按下的次数在关闭界面后集中触发。
     */
    public static void consumeToggle() {
        // 26.2：Minecraft#screen 字段移除，屏幕状态移入 Gui（gui.screen()）
        boolean screenOpen = Minecraft.getInstance().gui.screen() != null;
        while (ClientEvents.TOGGLE_KEY.consumeClick()) {
            if (!screenOpen) {
                showInGame = !showInGame;
            }
        }
    }

    /** F9 开关状态（游戏画面常显，默认关闭：仅界面打开时显示）。 */
    static boolean showInGame() {
        return showInGame;
    }

    /**
     * 独立渲染入口（两条路径，互斥不重复）：
     * inGame=true = 正常游戏画面（需 F9 开启常显）；
     * inGame=false = 界面打开时在模糊之后渲染（不受模糊影响）。
     */
    static void render(GuiGraphicsExtractor g, boolean inGame) {
        Minecraft mc = Minecraft.getInstance();
        // 26.2：Options#hideGui 字段移除，HUD 隐藏状态移入 Gui.hud（Hud#isHidden()）
        if (mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }
        if (inGame && !showInGame) {
            return;
        }
        if (!BasClientConfig.SESSION_HUD_ENABLED.get()) {
            return;
        }
        SessionStats stats = SessionTracker.get().sessionStats();
        if (!stats.isActive()) {
            return;
        }
        long now2 = System.currentTimeMillis();
        StatSnapshot delta = stats.vanillaDelta(); // 触发 500ms 缓存刷新（死亡/击杀仍需逐项读取）
        long placed = SessionTracker.get().sessionPlacedTotal();
        // 破坏 = 原版统计增量（生存/冒险） + 创造模式客户端确认增量（生存下后者恒为 0）
        long mined = stats.vanillaDeltaSum("minecraft:mined") + SessionTracker.get().sessionMinedTotal();
        long pickedUp = stats.vanillaDeltaSum("minecraft:picked_up");
        long crafted = stats.vanillaDeltaSum("minecraft:crafted");
        long deaths = delta.get("minecraft:custom", "minecraft:deaths");
        long mobKills = delta.get("minecraft:custom", "minecraft:mob_kills");

        var font = mc.font;
        String realTime = UiFormat.durationMillis(stats.realMillis(now2));
        String gameTime = UiFormat.time(delta.get("minecraft:custom", "minecraft:play_time"));
        // 行内容按配置开关过滤（每项可单独关闭）
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        if (BasClientConfig.SESSION_HUD_SHOW_REAL_TIME.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.real").getString(), realTime});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_GAME_TIME.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.game").getString(), gameTime});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_PLACED.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.placed").getString(), String.valueOf(placed)});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_MINED.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.mined").getString(), String.valueOf(mined)});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_PICKED_UP.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.picked_up").getString(), String.valueOf(pickedUp)});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_CRAFTED.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.crafted").getString(), String.valueOf(crafted)});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_DEATHS.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.deaths").getString(), String.valueOf(deaths)});
        }
        if (BasClientConfig.SESSION_HUD_SHOW_MOB_KILLS.get()) {
            rows.add(new String[]{Component.translatable("gui.bas.session.mob_kills").getString(), String.valueOf(mobKills)});
        }
        String title = Component.translatable("gui.bas.session.title").getString();

        int lineH = 9;
        int pad = 3;
        int leftW = 0;
        for (String[] row : rows) {
            leftW = Math.max(leftW, font.width(row[0]));
        }
        int rightW = 0;
        for (String[] row : rows) {
            rightW = Math.max(rightW, font.width(row[1]));
        }
        int width = Math.max(font.width(title) + 4, pad * 3 + leftW + 8 + rightW);
        int height = pad * 2 + lineH * (rows.size() + 1);

        // 按配置锚定到屏幕四角落之一
        var corner = BasClientConfig.SESSION_HUD_CORNER.get();
        boolean right = corner == BasClientConfig.HudCorner.TOP_RIGHT || corner == BasClientConfig.HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == BasClientConfig.HudCorner.BOTTOM_LEFT || corner == BasClientConfig.HudCorner.BOTTOM_RIGHT;
        int x = right ? g.guiWidth() - width - 4 : 4;
        int y = bottom ? g.guiHeight() - height - 4 : 4;
        int opacity = java.lang.Math.floorMod(BasClientConfig.SESSION_HUD_BACKGROUND_OPACITY.get(), 101);
        if (opacity > 0) {
            int alpha = (int) (opacity / 100.0 * 255) << 24;
            g.fill(x, y, x + width, y + height, alpha);
        }
        // 26.1：GuiGraphics 更名 GuiGraphicsExtractor，drawString 家族更名为 text 家族
        g.text(font, title, x + pad, y + pad, 0xFFFFFF55);
        int rowY = y + pad + lineH;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            g.text(font, row[0], x + pad, rowY, 0xFFE0E0E0);
            g.text(font, row[1], x + width - pad - font.width(row[1]), rowY, 0xFFFFFFFF);
            rowY += lineH;
            // 时长区与计数区之间画分隔线（前两行是时长）
            if (i == 1) {
                int sepY = rowY - 2;
                g.fill(x + 2, sepY, x + width - 2, sepY + 1, 0x60AAAAAA);
            }
        }
    }
}
