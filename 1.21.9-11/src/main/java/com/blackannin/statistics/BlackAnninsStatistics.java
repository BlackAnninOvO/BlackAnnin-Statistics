package com.blackannin.statistics;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.blackannin.statistics.config.BasClientConfig;

/**
 * 通用入口（Forge 1.21.6+：构造器注入 FMLJavaModLoadingContext，ModLoadingContext.get() 已废弃）。
 * 本模组全部功能都在客户端实现（见 {@link BlackAnninsStatisticsClient}）：
 * 装在专用服务器上时不会有任何行为——不监听、不采集、不保存玩家数据。
 * 仅注册 CLIENT 配置（专用服务器上 CLIENT 类型配置本就不会加载）。
 */
@Mod(BlackAnninsStatistics.MODID)
public class BlackAnninsStatistics {
    public static final String MODID = "blackannins_statistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlackAnninsStatistics(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, BasClientConfig.SPEC);
        // 1.21.9+/26.x：生命周期事件经 FMLClientSetupEvent.getBus(modBus) 监听；
        // 配置界面经 MinecraftForge.registerConfigScreen 注册（dist 守卫保证专用服务器不触碰客户端类）。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            var modBus = context.getModBusGroup();
            net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.getBus(modBus).addListener(event ->
                    com.blackannin.statistics.BlackAnninsStatisticsClient.onClientSetup(event, modBus));
            // HUD 层：AddGuiOverlayLayersEvent 在 Gui 构建早期触发（早于 client setup），
            // 必须在构造期注册监听，否则层永远不会被加入。
            net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS.addListener(event ->
                    com.blackannin.statistics.client.SessionHud.onAddGuiOverlayLayers(event));
            MinecraftForge.registerConfigScreen(com.blackannin.statistics.ui.BasConfigScreen::new);
        }
    }
}
