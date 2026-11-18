package com.blackannin.statistics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;

import com.blackannin.statistics.config.BasClientConfig;

/**
 * 通用入口。本模组全部功能都在客户端实现（见 {@link BlackAnninsStatisticsClient}）：
 * 装在专用服务器上时不会有任何行为——不监听、不采集、不保存玩家数据。
 */
@Mod(BlackAnninsStatistics.MODID)
public class BlackAnninsStatistics {
    public static final String MODID = "blackannins_statistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlackAnninsStatistics(IEventBus modEventBus, ModContainer modContainer) {
        // 客户端配置在两种物理端都可加载，但只有客户端会实际读取使用
        modContainer.registerConfig(ModConfig.Type.CLIENT, BasClientConfig.SPEC);
    }
}
