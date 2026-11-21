package com.blackannin.statistics.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.storage.StoragePaths;
import com.blackannin.statistics.ui.StatisticsHubScreen;
import com.blackannin.statistics.ui.VanillaStatsGate;

import static net.minecraft.commands.Commands.literal;

/**
 * 客户端命令 /bas：调试与快捷入口（仅本地执行，不发送到服务器）。
 */
public final class BasCommands {
    private BasCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("bas")
                .then(literal("open").executes(ctx -> {
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance()
                            .setScreen(new StatisticsHubScreen(Minecraft.getInstance().screen)));
                    return 1;
                }))
                .then(literal("vanilla").executes(ctx -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            VanillaStatsGate.bypassNextStatsScreen();
                            mc.setScreen(new StatsScreen(mc.screen, mc.player.getStats()));
                        }
                    });
                    return 1;
                }))
                .then(literal("resync").executes(ctx -> {
                    SessionTracker.get().captureAndFlush();
                    ctx.getSource().sendSuccess(() -> Component.literal("已采集并保存当前统计"), false);
                    return 1;
                }))
                .then(literal("testplace").executes(ctx -> {
                    // 诊断命令：绕过事件链直接注入一次放置计数，用于区分"采集问题"与"落盘/显示问题"
                    SessionTracker.get().onBlockPlaced("minecraft:stone");
                    ctx.getSource().sendSuccess(() -> Component.literal("已注入 1 次放置测试（石头），输入 /bas resync 落盘"), false);
                    return 1;
                }))
                .then(literal("dir").executes(ctx -> {
                    String dir = StoragePaths.get().root().toString();
                    ctx.getSource().sendSuccess(() -> Component.literal("数据目录: " + dir), false);
                    return 1;
                }))
                .then(literal("status").executes(ctx -> {
                    var tracker = SessionTracker.get();
                    var loc = tracker.currentLocation();
                    String msg = loc == null
                            ? "当前无会话（未在世界中）"
                            : "会话: " + loc.displayName() + " [" + loc.locationKey() + "]";
                    String finalMsg = msg;
                    ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
                    // 会话内放置计数明细（与统计中心显示值的差值 = 历史存量）
                    var placed = tracker.sessionPlacedView();
                    if (!placed.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("本次会话放置确认明细："), false);
                        long total = 0;
                        for (var e : placed.entrySet()) {
                            total += e.getValue();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "  " + e.getKey() + " × " + e.getValue()), false);
                        }
                        long finalTotal = total;
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "合计 " + finalTotal + "（统计中心显示值 = 该明细 + 历史存量，无其他来源）"), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal("本次会话尚无放置确认"), false);
                    }
                    return 1;
                })));
    }
}
