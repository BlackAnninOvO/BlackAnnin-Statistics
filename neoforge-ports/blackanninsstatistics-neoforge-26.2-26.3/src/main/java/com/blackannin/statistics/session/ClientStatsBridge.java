package com.blackannin.statistics.session;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;

import com.blackannin.statistics.stat.StatSnapshot;

/**
 * Minecraft 侧采集桥：把原版统计计数器转成 {@link StatSnapshot}，
 * 并提供多人统计同步请求（与原版统计界面打开时发送的是同一封原版封包）。
 *
 * <p>读取方式与原版统计界面一致：遍历注册表逐项 getValue，
 * 因此任何模组注册的方块/物品/生物自动被覆盖，且无需访问受保护的计数器内部 map。</p>
 */
public final class ClientStatsBridge {
    private ClientStatsBridge() {}

    /** 遍历注册表读取计数器全部非零项。单人读集成服务器计数器，多人读客户端计数器。 */
    public static StatSnapshot readCounter(StatsCounter counter) {
        StatSnapshot snapshot = new StatSnapshot();
        readRegistryStat(counter, Stats.BLOCK_MINED, BuiltInRegistries.BLOCK, snapshot);
        readRegistryStat(counter, Stats.ITEM_CRAFTED, BuiltInRegistries.ITEM, snapshot);
        readRegistryStat(counter, Stats.ITEM_USED, BuiltInRegistries.ITEM, snapshot);
        readRegistryStat(counter, Stats.ITEM_BROKEN, BuiltInRegistries.ITEM, snapshot);
        readRegistryStat(counter, Stats.ITEM_PICKED_UP, BuiltInRegistries.ITEM, snapshot);
        readRegistryStat(counter, Stats.ITEM_DROPPED, BuiltInRegistries.ITEM, snapshot);
        readRegistryStat(counter, Stats.ENTITY_KILLED, BuiltInRegistries.ENTITY_TYPE, snapshot);
        readRegistryStat(counter, Stats.ENTITY_KILLED_BY, BuiltInRegistries.ENTITY_TYPE, snapshot);
        readRegistryStat(counter, Stats.CUSTOM, BuiltInRegistries.CUSTOM_STAT, snapshot);
        return snapshot;
    }

    private static <T> void readRegistryStat(StatsCounter counter, StatType<T> type,
                                             Iterable<T> registry, StatSnapshot out) {
        String typeId = BuiltInRegistries.STAT_TYPE.getKey(type).toString();
        for (T value : registry) {
            int v = counter.getValue(type, value);
            if (v > 0) {
                Identifier key = type.getRegistry().getKey(value);
                if (key != null) {
                    out.put(typeId, key.toString(), v);
                }
            }
        }
    }

    /** 多人：请求服务器把统计发过来（原版统计界面 init 就是这封包）。 */
    public static void requestStatsSync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
    }

    /** 单人：读集成服务器上当前玩家的统计计数器（内存数据，零 IO）。 */
    public static StatsCounter singleplayerCounter() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer clientPlayer = mc.player;
        if (clientPlayer == null || mc.getSingleplayerServer() == null) {
            return null;
        }
        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(clientPlayer.getUUID());
        return serverPlayer == null ? null : serverPlayer.getStats();
    }

    /** 当前是否在单人（含局域网开放）世界。 */
    public static boolean isInSingleplayer() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    /** 解析当前位置：单人=存档文件夹名；多人=服务器地址。要求玩家已在世界中。 */
    public static PlayLocation resolveLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        if (isInSingleplayer()) {
            String dir = mc.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT)
                    .getParent().getFileName().toString();
            String title = mc.getSingleplayerServer().getWorldData() != null
                    ? mc.getSingleplayerServer().getWorldData().getLevelName() : dir;
            return PlayLocation.singleplayer(dir, title);
        }
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return PlayLocation.multiplayer(server.ip, server.name);
        }
        return PlayLocation.multiplayer("unknown_server", "unknown server");
    }
}
