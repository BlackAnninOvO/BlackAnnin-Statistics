package com.blackannin.statistics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import com.blackannin.statistics.client.ClientEvents;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.storage.StoragePaths;

/**
 * 客户端入口：装配存储目录、事件桥与配置。本类不会在专用服务器上加载
 * （fabric.mod.json 声明 environment=client，{@link ClientModInitializer} 仅物理客户端调用）。
 */
public final class BlackAnninsStatisticsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        boolean production = !FabricLoader.getInstance().isDevelopmentEnvironment();
        // 客户端配置：加载 config/blackannins_statistics.json（等价 NeoForge 的 ModConfig 注册/加载）
        BasClientConfig.load();
        StoragePaths.init(
                BasClientConfig.DATA_DIRECTORY_OVERRIDE.get(),
                production,
                FabricLoader.getInstance().getGameDir());
        ClientEvents.init();
        BlackAnninsStatistics.LOGGER.info("BlackAnnin's Statistics 客户端就绪 (production={})", production);
    }
}
