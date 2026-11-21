package com.blackannin.statistics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.ui.StatisticsHubScreen;
import com.blackannin.statistics.ui.VanillaStatsGate;

/**
 * 拦截原版统计界面：等价 NeoForge 版的 ScreenEvent.Opening 事件桥。
 * 在 Minecraft#setScreen 入口处把原版 StatsScreen 替换为模组统计中心（配置可关）；
 * 模组 UI 主动打开原版统计时通过 {@link VanillaStatsGate} 一次性放行，避免死循环。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSetScreenMixin {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("HEAD"), cancellable = true)
    private void bas$replaceVanillaStatsScreen(Screen screen, CallbackInfo ci) {
        // 门控顺序与 NeoForge 版 ScreenEvent.Opening 处理器逐条对应：
        // 配置开关 → 旁路标记 → 目标界面类型（保证旁路标记在非 StatsScreen 打开时同样被消费一次）。
        if (!BasClientConfig.REPLACE_VANILLA_STATS_SCREEN.get()) {
            return;
        }
        if (VanillaStatsGate.isBypassed()) {
            VanillaStatsGate.consumeBypass();
            return;
        }
        if (!(screen instanceof StatsScreen)) {
            return;
        }
        ci.cancel();
        Minecraft self = (Minecraft) (Object) this;
        self.setScreen(new StatisticsHubScreen(self.screen));
    }
}
