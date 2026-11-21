package com.blackannin.statistics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import com.blackannin.statistics.config.BasClientConfig;

/**
 * 通用入口（Forge 1.20.1：FML 每个模组只允许一个 @Mod 类，且不支持构造器注入）。
 * 本模组全部功能都在客户端实现（见 {@link BlackAnninsStatisticsClient}）：
 * 装在专用服务器上时不会有任何行为——不监听、不采集、不保存玩家数据。
 * 仅注册 CLIENT 配置（专用服务器上 CLIENT 类型配置本就不会加载）。
 */
@Mod(BlackAnninsStatistics.MODID)
public class BlackAnninsStatistics {
    public static final String MODID = "blackannins_statistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlackAnninsStatistics() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BasClientConfig.SPEC);
    }
}
