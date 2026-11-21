package com.blackannin.statistics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * 通用常量与日志。本模组全部功能都在客户端实现（见 {@link BlackAnninsStatisticsClient}）：
 * 装在专用服务器上时不会有任何行为——不监听、不采集、不保存玩家数据。
 * Fabric 版通过 fabric.mod.json 的 {@code environment: "client"} 与
 * ClientModInitializer 入口（仅物理客户端调用）实现与 NeoForge 版同等的约束。
 */
public final class BlackAnninsStatistics {
    public static final String MODID = "blackannins_statistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    private BlackAnninsStatistics() {}
}
