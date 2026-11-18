package com.blackannin.statistics.ui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import com.blackannin.statistics.config.BasClientConfig;

/**
 * UI 会话共享状态：当前选择的档案（null = 所有档案合计）。
 * 存活于游戏运行期，所有界面共享。
 */
public final class UiState {
    @Nullable
    private static String selectedProfileId;

    private UiState() {}

    /** 初始化默认选择：配置为默认合计时为 null，否则当前玩家 UUID。 */
    public static void ensureInitialized() {
        if (selectedProfileId == null && !BasClientConfig.AGGREGATE_ALL_PROFILES_BY_DEFAULT.get()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                selectedProfileId = player.getUUID().toString();
            }
        }
    }

    @Nullable
    public static String selectedProfileId() {
        ensureInitialized();
        return selectedProfileId;
    }

    public static void setSelectedProfileId(@Nullable String profileId) {
        selectedProfileId = profileId;
    }
}
