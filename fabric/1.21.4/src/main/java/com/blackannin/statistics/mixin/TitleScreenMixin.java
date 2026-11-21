package com.blackannin.statistics.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blackannin.statistics.ui.StatisticsHubScreen;

/**
 * 标题屏右下角添加"统计中心"入口（等价 NeoForge 版 ScreenEvent.Init.Post + addListener）。
 * Mixin 类继承 Screen：编译期获得 protected addRenderableWidget 的合法调用上下文，
 * 字节码中的普通方法调用随重映射管线自动转换（不依赖 Invoker 名称重映射）。
 * init 在每次界面打开/窗口尺寸变化时重新执行，按钮随之重建，与 NeoForge 行为一致。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private void bas$addHubButton() {
        Button button = Button.builder(
                        Component.translatable("gui.bas.title_button"),
                        b -> Minecraft.getInstance().setScreen(new StatisticsHubScreen(this)))
                .bounds(this.width - 72, this.height - 26, 64, 20)
                .build();
        this.addRenderableWidget(button);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bas$onInit(CallbackInfo ci) {
        bas$addHubButton();
    }
}
