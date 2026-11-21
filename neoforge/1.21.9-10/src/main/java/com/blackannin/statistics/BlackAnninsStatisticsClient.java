package com.blackannin.statistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import com.blackannin.statistics.client.ClientEvents;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 客户端入口：装配存储目录、事件桥与配置界面。本类不会在专用服务器上加载。
 */
@Mod(value = BlackAnninsStatistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlackAnninsStatistics.MODID, value = Dist.CLIENT)
public class BlackAnninsStatisticsClient {

    public BlackAnninsStatisticsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                StoragePaths.init(
                        BasClientConfig.DATA_DIRECTORY_OVERRIDE.get(),
                        FMLEnvironment.isProduction(),
                        net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()));
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        BlackAnninsStatistics.LOGGER.info("BlackAnnin's Statistics 客户端就绪 (production={})", FMLEnvironment.isProduction());
    }
}
