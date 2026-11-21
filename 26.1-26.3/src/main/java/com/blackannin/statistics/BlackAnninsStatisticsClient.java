package com.blackannin.statistics;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import com.blackannin.statistics.client.ClientEvents;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.storage.StoragePaths;
import com.blackannin.statistics.ui.BasConfigScreen;

/**
 * 客户端装配（1.21.9+/26.x 注册模型：所有事件在各自总线上显式监听；
 * 本类仅由入口构造器在客户端 dist 下经方法引用加载，专用服务器不触碰）。
 */
public final class BlackAnninsStatisticsClient {

    static void onClientSetup(FMLClientSetupEvent event,
                              net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        com.blackannin.statistics.client.SessionHud.registerModBusListeners(modBus);
        ClientEvents.register();
        // 游戏退出兜底：等待未完成的写入（JVM 退出钩子在进程结束前完成执行）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> SessionTracker.get().onShutdown(),
                "blackannins-statistics-shutdown"));
        event.enqueueWork(() -> {
            StoragePaths.init(
                    BasClientConfig.DATA_DIRECTORY_OVERRIDE.get(),
                    FMLEnvironment.production,
                    FMLPaths.GAMEDIR.get());
            BlackAnninsStatistics.LOGGER.info("BlackAnnin's Statistics 客户端就绪 (production={})", FMLEnvironment.production);
        });
    }

    private BlackAnninsStatisticsClient() {}
}
