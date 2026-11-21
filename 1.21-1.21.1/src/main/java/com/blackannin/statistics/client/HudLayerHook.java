package com.blackannin.statistics.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * HUD 层注册钩子。独立顶层类：仅在运行时检测到 AddGuiOverlayLayersEvent 时才被加载
 * （Forge 51/MC 1.21.0 没有该事件类，静态扫描带该参数的方法会 NoClassDefFoundError）。
 * 仅 Forge 52+（MC 1.21.1+）走此路径获得"游戏画面常显"能力。
 */
public final class HudLayerHook {

    @SubscribeEvent
    void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add((g, partialTick) -> {
            if (Minecraft.getInstance().screen == null) {
                SessionHud.render(g, true);
            }
        });
    }
}
