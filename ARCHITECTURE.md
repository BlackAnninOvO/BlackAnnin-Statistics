# BlackAnnin's Statistics 架构说明

版本：1.21.1 / NeoForge 21.1.x / Java 21

## 分层总览

```
┌────────────────────────────────────────────────────────────┐
│ ui/         展示层：统计中心、位置列表、统计视图、档案选择、管理  │
├────────────────────────────────────────────────────────────┤
│ query/      查询层：StatsRepository（扫描/聚合/删除）           │
├────────────────────────────────────────────────────────────┤
│ session/    会话层：SessionTracker + ClientStatsBridge        │
├────────────────────────────────────────────────────────────┤
│ stat/       核心数据结构（纯 Java，无 MC 依赖，可移植/可单测）     │
│ storage/    存储层：StoreIO / StoragePaths / ProfileIndex     │
├────────────────────────────────────────────────────────────┤
│ client/ command/ config/  平台接线：事件、命令、配置             │
└────────────────────────────────────────────────────────────┘
```

`stat/`、`storage/` 不引用任何 Minecraft / NeoForge 类型（仅 Gson），
移植 Fabric 或其它版本时可直接搬运；平台差异集中在 `session/ClientStatsBridge`、
`client/ClientEvents`、`ui/` 与入口类。

## 核心机制：累计快照（Cumulative Snapshot）

每个"位置 × 档案"保存一份**原版统计的累计值**（与存档内 `stats/<uuid>.json`、
服务器统计界面数据同构），而不是增量流水：

- 合并采用**逐项取最大值**：历史只增不减；服务器重置统计后本地保留历史并在 meta 标记 `serverStatReset`。
- 全局 = 各位置累计求和：天然满足"新旧数据并存、可单独查看、可叠加"；玩家手工删除/编辑某个文件后聚合自动保持一致。
- 崩溃恢复：丢失的最后一个保存周期会在下次进入时由累计差值自动补上。
- 会话数、首末游玩时间等元信息在 meta 中维护。

## 数据采集（仅原版通道）

| 场景 | 方式 | 说明 |
|---|---|---|
| 单人/局域网 | `ServerPlayer.getStats()` 内存直读 | 集成服务器运行在本机，零 IO |
| 多人 | 发送原版 `REQUEST_STATS` 封包，读回 `LocalPlayer.getStats()` | 与原版统计界面打开时的封包完全一致；周期默认 30s + 进服 + 退出前 |

读取方式与原版统计界面一致：遍历注册表（BLOCK/ITEM/ENTITY_TYPE/CUSTOM_STAT）逐项
`getValue`，因此模组注册的内容自动覆盖，且无需访问计数器内部结构。
已知限制：若客户端当前注册表不含某统计目标（例如卸载了对应模组），该项在**新一轮采集**中
不可见（与原版统计文件解析行为一致），但已写入本地 JSON 的历史数据不受影响。

## 生命周期

```
进入世界 → tick 检测 → 解析位置(存档名/服务器地址) + 档案(UUID)
        → 载入或新建 LocationStore → sessions+1
周期(60s) → 读计数器 → detectServerReset → mergeMax → 异步原子写
退出世界/断线(GameShuttingDown/LoggingOut) → 最终采集 → flushAndWait(≤2s)
```

- 写入：后台单线程（daemon）executor，`.tmp` + `ATOMIC_MOVE`；损坏文件隔离到 `backups/`。
- 稳态开销：每 tick 一次空检查；每保存周期一次注册表遍历（毫秒级内存读）+ 一次小文件异步写。

## 目录布局

```
<数据目录>/                       默认 C:\Users\ASUS\AppData\Roaming\.minecraft\blackannins_statistics\
├── data/singleplayer/<存档名>/<UUID>.json
├── data/multiplayer/<host_端口>/<UUID>.json
├── profiles.json                UUID → 曾用名/最后游玩（仅为 UI 服务，可重建）
└── backups/                     损坏文件隔离
```

优先级：配置 `dataDirectoryOverride` > 固定默认路径（开发环境为 run/ 目录）> 游戏运行目录回退。

## UI

- 接管：`ScreenEvent.Opening` 把原版 `StatsScreen` 替换为 `StatisticsHubScreen`
  （配置可关；统计中心内"原版统计"按钮通过 `VanillaStatsGate` 一次性旁路防回环）。
- `StatsBrowser`（通用/方块/物品/生物 + 搜索 + 列排序）同时服务全局页与单位置视图页。
- `StatsRepository.aggregate*` 在打开 UI 时按需扫描聚合；当前会话位置会合并
  `SessionTracker.liveStoreForCurrentLocation()` 的实时数据。

## 兼容性说明

- NeoForge 1.21.1 起移除了 `displayTest` 机制：本模组不注册任何网络载荷，客户端可自由加入
  无本模组的原版服 / NeoForge 服；服务端安装本模组无任何行为。
- 无 mixin、无 AccessTransformer、无自定义封包、无注册表改动 —— 与其它模组无直接冲突面。

## 移植路线（M2/M3）

- 其它 MC 版本：核心层不变，替换 `ClientStatsBridge`（StatsScreen/封包路径在不同版本有差异）与事件类名。
- Fabric：`stat/`、`storage/` 原样搬运；重写事件接线（ScreenEvents/ClientTickEvents）、
  统计界面拦截（Fabric 下用对 PauseScreen 按钮的 mixin 或 ModMenu 入口）与入口类。
