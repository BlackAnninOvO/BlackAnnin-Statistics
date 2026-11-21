package com.blackannin.statistics.ui;

/**
 * "查看原版统计"旁路标记：模组 UI 主动打开原版统计界面时置位，
 * 让 ScreenEvent.Opening 拦截器放行一次，避免死循环。
 */
public final class VanillaStatsGate {
    private static boolean bypassNext;

    private VanillaStatsGate() {}

    public static void bypassNextStatsScreen() {
        bypassNext = true;
    }

    public static boolean isBypassed() {
        return bypassNext;
    }

    public static void consumeBypass() {
        bypassNext = false;
    }
}
