package com.blackannin.statistics;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import com.blackannin.statistics.client.ClientEvents;
import com.blackannin.statistics.client.SessionHud;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.storage.StoragePaths;
import com.blackannin.statistics.ui.BasConfigScreen;

/**
 * 客户端装配。Forge 1.20.1 没有 NeoForge 的 dist 专用第二 @Mod 入口，
 * 改为挂在 MOD 总线、且 Dist.CLIENT 限定的事件订阅类：
 * 专用服务器上本类根本不会被加载，因此不会触发任何客户端逻辑。
 *
 * <p>与 NeoForge 版的差异：
 * ① 配置界面为自带的 {@link BasConfigScreen}（Forge 无内置 ConfigurationScreen，
 *    通过 ConfigScreenHandler 扩展点挂到模组列表的"配置"按钮）；
 * ② 游戏退出兜底改用 JVM shutdown hook（Forge 1.20.1 无 GameShuttingDownEvent 等价事件时序，
 *    hook 语义与其一致：仅进程退出时执行一次）。</p>
 */
@Mod.EventBusSubscriber(modid = BlackAnninsStatistics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BlackAnninsStatisticsClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // 游戏画面 HUD 层：Forge 58+ 提供 AddGuiOverlayLayersEvent；Forge 56/57 缺失时优雅降级（仅界面显示）。
        // 必须在 mod loading 线程注册（ModLoadingContext 活跃），不能放进 enqueueWork。
        try {
            Class.forName("net.minecraftforge.client.event.AddGuiOverlayLayersEvent", false,
                    BlackAnninsStatisticsClient.class.getClassLoader());
            SessionHud.registerHudLayer(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModBusGroup());
        } catch (ClassNotFoundException e) {
            BlackAnninsStatistics.LOGGER.info("此 Forge 版本无 post-GUI 挂点（MC 1.21.6/1.21.7），小窗仅在界面打开时显示");
        }
        event.enqueueWork(() -> {
            StoragePaths.init(
                    BasClientConfig.DATA_DIRECTORY_OVERRIDE.get(),
                    FMLEnvironment.production,
                    FMLPaths.GAMEDIR.get());
            // 配置界面已在入口构造器经 MinecraftForge.registerConfigScreen 注册
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
            // 游戏退出兜底：等待未完成的写入（JVM 退出钩子在进程结束前完成执行）
            Runtime.getRuntime().addShutdownHook(new Thread(() -> com.blackannin.statistics.session.SessionTracker.get().onShutdown(),
                    "blackannins-statistics-shutdown"));
            BlackAnninsStatistics.LOGGER.info("BlackAnnin's Statistics 客户端就绪 (production={})", FMLEnvironment.production);
        });
    }
}
