package com.blackannin.statistics.client;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.blackannin.statistics.BlackAnninsStatistics;
import com.blackannin.statistics.command.BasCommands;
import com.blackannin.statistics.config.BasClientConfig;
import com.blackannin.statistics.session.SessionTracker;
import com.blackannin.statistics.ui.StatisticsHubScreen;
import com.blackannin.statistics.ui.VanillaStatsGate;

/**
 * 游戏事件 → 模组逻辑的接线（Forge 1.20.1 游戏总线；全部为客户端侧事件，服务端零监听）。
 * 与 NeoForge 版差异：ClientTickEvent.Post 在 Forge 1.20.1 上对应
 * {@link TickEvent.ClientTickEvent} 的 {@code Phase.END}。
 */
public final class ClientEvents {
    private ClientEvents() {}

    /**
     * 待确认的方块放置：右键瞬间记录意图（目标位置 + 手持方块 + 当时状态），
     * 之后的客户端 tick 中确认该位置真的变成了目标方块（单人来自客户端预测，
     * 多人来自服务器确认回包），超时未确认则视为放置失败丢弃。
     * 注意：玩家放置方块不触发 Forge 的 BlockEvent.EntityPlaceEvent（该事件仅覆盖末影人等实体放置），
     * 原版统计系统亦无"放置"数据，故采用本方案，单人/多人通用。
     */
    private static final Map<BlockPos, PendingPlacement> PENDING_PLACEMENTS = new LinkedHashMap<>();
    private static final int PLACEMENT_CONFIRM_TICKS = 40;
    private static long clientTickCounter;

    private record PendingPlacement(Block block, BlockState before, long expireTick) {}

    /** 每 tick 驱动会话跟踪与放置确认（内部按秒级节流，tick 本身只做空检查级别的判断）。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        SessionTracker.get().tick();
        clientTickCounter++;
        processPendingPlacements();
        SessionHud.consumeToggle();
    }

    /** 记录放置意图（仅本机客户端侧交互；集成服务器的 ServerPlayer 交互由此天然排除）。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!(event.getEntity() instanceof LocalPlayer localPlayer) || localPlayer != mc.player) {
                return;
            }
            // 从玩家实体读手持（跨版本安全的写法；1.20.1 事件本身携带物品，但不依赖它）
            ItemStack stack = localPlayer.getItemInHand(event.getHand());
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return;
            }
            Level level = event.getLevel();
            BlockPlaceContext context = new BlockPlaceContext(
                    event.getEntity(), event.getHand(), stack, event.getHitVec());
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
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(pending.block());
                if (key != null) {
                    SessionTracker.get().onBlockPlaced(key.toString());
                }
            }
        }
    }

    /** 离开世界/断开服务器：立即采集并等待落盘，丢弃未确认的放置。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PENDING_PLACEMENTS.clear();
        SessionTracker.get().onLeave();
    }

    /** 拦截原版统计界面：替换为模组统计中心。 */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!BasClientConfig.REPLACE_VANILLA_STATS_SCREEN.get()) {
            return;
        }
        if (VanillaStatsGate.isBypassed()) {
            VanillaStatsGate.consumeBypass();
            return;
        }
        if (event.getNewScreen() instanceof StatsScreen) {
            event.setNewScreen(new StatisticsHubScreen(event.getCurrentScreen()));
        }
    }

    /** 标题屏右下角添加"统计中心"入口（无世界状态也可查看历史数据）。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        Button button = Button.builder(
                net.minecraft.network.chat.Component.translatable("gui.bas.title_button"),
                b -> Minecraft.getInstance().setScreen(new StatisticsHubScreen(title)))
                .bounds(title.width - 72, title.height - 26, 64, 20).build();
        event.addListener(button);
    }

    /** 客户端调试/运维命令 /bas。 */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BasCommands.register(event.getDispatcher());
    }

    /**
     * "本次游玩"小窗（界面路径）：界面（ESC 菜单/统计页等）渲染完成后绘制，
     * 此时背景模糊已完成，小窗保持清晰。
     * 部分 mod 的 Screen 渲染后会残留 pose 变换，重置为绝对坐标，
     * 保证小窗位置固定在屏幕左上角（与游戏内渲染路径一致）。
     */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        var g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().setIdentity();
        SessionHud.render(g, false);
        g.pose().popPose();
    }
}
