package com.blackannin.statistics.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.command.BasCommands;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.ui.StatisticsHubScreen;
import com.blackannin.statistics.ui.VanillaStatsGate;

/**
 * 游戏事件 → 模组逻辑的接线（全部为客户端侧事件，服务端零监听）。
 * Fabric 事件源（等价 NeoForge 版）：
 * <ul>
 *   <li>ClientTickEvent.Post          → ClientTickEvents.END_CLIENT_TICK</li>
 *   <li>PlayerInteractEvent.RightClickBlock → UseBlockCallback（客户端侧同义触发）</li>
 *   <li>ClientPlayerNetworkEvent.LoggingOut → ClientPlayConnectionEvents.DISCONNECT</li>
 *   <li>ScreenEvent.Opening（拦截原版统计界面）→ Minecraft#setScreen 的 Mixin（见 mixin/MinecraftSetScreenMixin）</li>
 *   <li>ScreenEvent.Init.Post（标题屏按钮）  → ScreenEvents.AFTER_INIT + addRenderableWidget 访问器</li>
 *   <li>RegisterClientCommandsEvent    → ClientCommandRegistrationCallback</li>
 *   <li>RenderGuiEvent.Post            → HudRenderCallback</li>
 *   <li>ScreenEvent.Render.Post        → ScreenEvents.AFTER_RENDER</li>
 *   <li>GameShuttingDownEvent          → ClientLifecycleEvents.CLIENT_STOPPING</li>
 * </ul>
 */
public final class ClientEvents {
    private ClientEvents() {}

    /**
     * 待确认的方块放置：右键瞬间记录意图（目标位置 + 手持方块 + 当时状态），
     * 之后的客户端 tick 中确认该位置真的变成了目标方块（单人来自客户端预测，
     * 多人来自服务器确认回包），超时未确认则视为放置失败丢弃。
     * 注意：玩家放置方块不触发原版的实体放置事件（该事件仅覆盖末影人等实体放置），
     * 原版统计系统亦无"放置"数据，故采用本方案，单人/多人通用。
     */
    private static final Map<BlockPos, PendingPlacement> PENDING_PLACEMENTS = new LinkedHashMap<>();
    private static final int PLACEMENT_CONFIRM_TICKS = 40;
    private static long clientTickCounter;

    private record PendingPlacement(Block block, BlockState before, long expireTick) {}
    /**
     * 待确认的方块破坏：创造模式下原版不授予 minecraft:mined 统计
     * （ServerPlayerGameMode#destroyBlock 在 isCreative() 时直接返回，不调用 Block#playerDestroy），
     * 与放置采集同款采用客户端确认式采集：左键开始破坏瞬间记录意图（目标位置 + 当时状态），
     * 之后的客户端 tick 中确认该位置状态发生了变化，超时未确认则丢弃。
     * 生存/冒险模式不启用（原版统计负责），杜绝双计。
     */
    private static final Map<BlockPos, PendingBreak> PENDING_BREAKS = new LinkedHashMap<>();
    private static final int BREAK_CONFIRM_TICKS = 40;

    private record PendingBreak(Block block, BlockState before, long expireTick) {}

    /**
     * 键位分类：1.21.9 起分类由 String 翻译键改为 {@link KeyMapping$Category} 对象注册制
     * （Category#register(Identifier) 写入原版排序表，控件设置中按模组命名空间分组显示）。
     * 分类标签翻译键随之变为 id 的语言键："key.category.blackannins_statistics.main"
     * （原 "key.categories.blackannins_statistics" 的文案在语言文件中以新键延续生效）。
     */
    static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("blackannins_statistics", "main"));

    /**
     * "本次游玩"小窗切换键（F9，可在控件设置修改）。
     * NeoForge 版的 IN_GAME 冲突上下文在 Fabric 上以"界面打开时不切换"门控模拟
     * （见 {@link SessionHud#consumeToggle}），行为一致：仅游戏画面内生效。
     */
    static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.blackannins_statistics.toggle_session_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KEY_CATEGORY);

    /** Fabric 事件注册入口（等价 NeoForge 的 @SubscribeEvent 静态接线）。 */
    public static void init() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        UseBlockCallback.EVENT.register(ClientEvents::onRightClickBlock);
        AttackBlockCallback.EVENT.register(ClientEvents::onLeftClickBlock);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onLoggingOut());
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                // NeoForge 的 ScreenEvent.Render.Post 是全局事件；Fabric 的渲染事件按屏幕实例
                // 注册，故在每个屏幕初始化时挂接（等价语义：任意界面渲染完成后绘制小窗）。
                // 标题屏"统计中心"按钮由 TitleScreenMixin 处理（等价 ScreenEvent.Init.Post）。
                ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, tickDelta) ->
                        onScreenRenderPost(graphics)));
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> onRenderHud(graphics));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onGameShuttingDown());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                BasCommands.register(dispatcher));
    }

    /** 每 tick 驱动会话跟踪与放置确认（内部按秒级节流，tick 本身只做空检查级别的判断）。 */
    private static void onClientTick() {
        SessionTracker.get().tick();
        clientTickCounter++;
        processPendingPlacements();
        processPendingBreaks();
        SessionHud.consumeToggle();
    }

    /**
     * 记录放置意图（仅本机客户端侧交互；集成服务器的 ServerPlayer 交互由此天然排除：
     * UseBlockCallback 在服务端触发时 player 为 ServerPlayer，不满足 instanceof LocalPlayer）。
     */
    private static InteractionResult onRightClickBlock(Player player, Level level,
                                                       InteractionHand hand, BlockHitResult hitResult) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!(player instanceof LocalPlayer localPlayer) || localPlayer != mc.player) {
                return InteractionResult.PASS;
            }
            // 与 NeoForge 版一致：从玩家实体读取手持物品（上下文里的物品可能被其他事件修改）
            ItemStack stack = localPlayer.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return InteractionResult.PASS;
            }
            BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hitResult);
            BlockPos pos = context.getClickedPos();
            BlockState before = level.getBlockState(pos);
            PENDING_PLACEMENTS.put(pos.immutable(),
                    new PendingPlacement(blockItem.getBlock(), before, clientTickCounter + PLACEMENT_CONFIRM_TICKS));
            // 诊断日志：定位放置采集链路（复现问题后可降为 DEBUG）
            BlackAnninsStatistics.LOGGER.info("[BAS] 放置意图: {} @ {} (before={})",
                    BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()), pos, before.getBlock());
        } catch (Exception e) {
            // 防御：个别模组构造的异常 hitResult 不应中断计数链，也不影响游戏
            BlackAnninsStatistics.LOGGER.warn("[BAS] 放置意图记录异常（已跳过该次）", e);
        }
        return InteractionResult.PASS; // 恒不拦截，只旁观采集
    }

    /** 确认放置：目标位置变为目标方块且与记录时状态不同（排除对已有同类方块的无效点击）。 */
    private static void processPendingPlacements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING_PLACEMENTS.clear();
            return;
        }
        if (PENDING_PLACEMENTS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, PendingPlacement>> iterator = PENDING_PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PendingPlacement> entry = iterator.next();
            PendingPlacement pending = entry.getValue();
            if (clientTickCounter > pending.expireTick()) {
                BlockState current = mc.level.getBlockState(entry.getKey());
                BlackAnninsStatistics.LOGGER.info("[BAS] 放置未确认(过期): {} @ {} 当前状态={}",
                        BuiltInRegistries.BLOCK.getKey(pending.block()), entry.getKey(), current.getBlock());
                iterator.remove();
                continue;
            }
            BlockState current = mc.level.getBlockState(entry.getKey());
            if (current.is(pending.block()) && !current.equals(pending.before())) {
                iterator.remove();
                Identifier key = BuiltInRegistries.BLOCK.getKey(pending.block());
                if (key != null) {
                    SessionTracker.get().onBlockPlaced(key.toString());
                }
            }
        }
    }

    /**
     * 记录破坏意图（仅本机客户端侧交互；仅创造模式启用——原版统计在创造下不计数，
     * 生存/冒险由原版统计负责，避免双计；集成服务器的 ServerPlayer 由此天然排除）。
     */
    private static InteractionResult onLeftClickBlock(Player player, Level level,
                                                      InteractionHand hand, BlockPos pos,
                                                      net.minecraft.core.Direction direction) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!(player instanceof LocalPlayer localPlayer) || localPlayer != mc.player) {
                return InteractionResult.PASS;
            }
            if (!localPlayer.isCreative()) {
                return InteractionResult.PASS; // 生存/冒险：原版统计负责
            }
            BlockState before = level.getBlockState(pos);
            Block block = before.getBlock();
            PENDING_BREAKS.put(pos.immutable(),
                    new PendingBreak(block, before, clientTickCounter + BREAK_CONFIRM_TICKS));
            // 诊断日志：定位破坏采集链路（复现问题后可降为 DEBUG）
            BlackAnninsStatistics.LOGGER.info("[BAS] 破坏意图: {} @ {} (before={})",
                    BuiltInRegistries.BLOCK.getKey(block), pos, block);
        } catch (Exception e) {
            // 防御：个别模组构造的异常交互不应中断计数链，也不影响游戏
            BlackAnninsStatistics.LOGGER.warn("[BAS] 破坏意图记录异常（已跳过该次）", e);
        }
        return InteractionResult.PASS; // 恒不拦截，只旁观采集
    }

    /** 确认破坏：目标位置状态与记录时不同（方块被移除或替换）即计数。 */
    private static void processPendingBreaks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING_BREAKS.clear();
            return;
        }
        if (PENDING_BREAKS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, PendingBreak>> iterator = PENDING_BREAKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PendingBreak> entry = iterator.next();
            PendingBreak pending = entry.getValue();
            if (clientTickCounter > pending.expireTick()) {
                iterator.remove();
                continue;
            }
            BlockState current = mc.level.getBlockState(entry.getKey());
            if (!current.equals(pending.before())) {
                iterator.remove();
                Identifier key = BuiltInRegistries.BLOCK.getKey(pending.block());
                if (key != null) {
                    SessionTracker.get().onBlockMined(key.toString());
                }
            }
        }
    }

    /** 离开世界/断开服务器：立即采集并等待落盘，丢弃未确认的放置。 */
    private static void onLoggingOut() {
        PENDING_PLACEMENTS.clear();
        SessionTracker.get().onLeave();
    }

    /**
     * "本次游玩"小窗（游戏画面路径）：仅在无界面时渲染，且需 F9 开启常显。
     * 界面打开时改由 ScreenEvents.AFTER_RENDER 在模糊之后渲染，不受界面模糊影响。
     */
    private static void onRenderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        if (Minecraft.getInstance().screen == null) {
            SessionHud.render(graphics, true);
        }
    }

    /**
     * "本次游玩"小窗（界面路径）：界面（ESC 菜单/统计页等）渲染完成后绘制，
     * 此时背景模糊已完成，小窗保持清晰。
     * 部分 mod 的 Screen 渲染后会残留 pose 变换，重置为绝对坐标，
     * 保证小窗位置固定在屏幕左上角（与游戏内渲染路径一致）。
     */
    private static void onScreenRenderPost(net.minecraft.client.gui.GuiGraphics graphics) {
        // 1.21.6+ GuiGraphics.pose() 返回 org.joml.Matrix3x2fStack（原 PoseStack）：
        // pushPose/setIdentity/popPose 对应改为 pushMatrix/identity/popMatrix
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        SessionHud.render(graphics, false);
        graphics.pose().popMatrix();
    }

    /** 游戏退出兜底：等待未完成的写入。 */
    private static void onGameShuttingDown() {
        SessionTracker.get().onShutdown();
    }
}
