package com.blackannin.statistics.client;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.session.SessionStats;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.stat.StatSnapshot;
import com.blackannin.statistics.ui.UiFormat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * "本次游玩"小窗：显示当前会话的实时增量统计（时长、放置、破坏、拾取、合成、死亡、击杀）。
 * 数据源为会话基线差值与放置确认计数，只读展示、不影响主体的累计数据；
 * 会话结束（离开世界/关闭客户端）后自动归零。按 F9（可在控件设置修改）切换显示。
 */
@EventBusSubscriber(modid = BlackAnninsStatistics.MODID, value = Dist.CLIENT)
public final class SessionHud {
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.blackannins_statistics.toggle_session_hud",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.blackannins_statistics");

    /** 是否额外显示在游戏画面上（F9 切换，默认关闭：仅界面打开时显示）。 */
    private static boolean showInGame = false;

    private SessionHud() {}

    @net.neoforged.bus.api.SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    /** 诊断：配置加载/重载（本类挂在 MOD 总线，可正确收到 FML 配置事件）。 */
    @net.neoforged.bus.api.SubscribeEvent
    static void onConfigLoading(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(BlackAnninsStatistics.MODID)) {
            BlackAnninsStatistics.LOGGER.info("[BAS] 配置已加载");
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onConfigReloading(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(BlackAnninsStatistics.MODID)) {
            BlackAnninsStatistics.LOGGER.info("[BAS] 配置已重载（修改应立即生效）");
        }
    }

    
    /** 消费切换按键（由 ClientTick 驱动）：开启后小窗也会显示在游戏画面上。 */
    public static void consumeToggle() {
        while (TOGGLE_KEY.consumeClick()) {
            showInGame = !showInGame;
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
    static void render(GuiGraphics g, boolean inGame) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
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
        long mined = stats.vanillaDeltaSum("minecraft:mined");
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
        g.drawString(font, title, x + pad, y + pad, 0xFFFFFF55);
        int rowY = y + pad + lineH;
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            g.drawString(font, row[0], x + pad, rowY, 0xFFE0E0E0);
            g.drawString(font, row[1], x + width - pad - font.width(row[1]), rowY, 0xFFFFFFFF);
            rowY += lineH;
            // 时长区与计数区之间画分隔线（前两行是时长）
            if (i == 1) {
                int sepY = rowY - 2;
                g.fill(x + 2, sepY, x + width - 2, sepY + 1, 0x60AAAAAA);
            }
        }
    }
}
