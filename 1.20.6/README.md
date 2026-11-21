<div align="center">

# BlackAnnin's Statistics — Forge 1.20.6

**在 Minecraft 中完整记录并查看你的游玩数据 —— 按存档、按服务器，跨客户端累计**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.6-blue)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-50.2.15-orange)](https://files.minecraftforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Side](https://img.shields.io/badge/Side-Client%20Only-important)

[English](#english) | 中文说明在下方

</div>

---

**目标**：Minecraft 1.20.6（50.2.15，Forge 50.x（官方无 1.20.5 构建）） · Java 21 · 纯客户端模组
**基准**：从主版本（1.21.1 NeoForge）移植，功能、数据格式与主版本完全一致。

## 为什么做这个模组

原版的统计界面有两个我一直不满意的地方：**只能看当前这一次的数据**，换个存档、换个服务器就看不到累计了；而且方块放置这种数据压根没有统计。

这个模组解决的就是这些：**你在每个存档、每个服务器里的数据会被分别记录，并汇总到同一个统计中心**。换大版本、换启动器、换电脑上的客户端都没关系——数据存在一个固定位置，会一直接着累计。

## 功能一览

- **全量统计** — 游玩时长、方块破坏/放置、生物击杀/被杀、物品合成/使用/损坏/拾取/丢弃、死亡、跳跃、各类移动距离……所有原版统计项；其他模组加入的方块、物品、生物**自动纳入**统计与显示（带图标）。
- **三级视图** — 全局 / 单人游戏（每个存档）/ 多人游戏（每个服务器）；可下钻到任意一个世界，也可查看合计。
- **多档案** — 不同账号 / 离线名的数据分开记录，可切换查看单个档案或全部合计。
- **方块放置统计** — 原版没有这项数据，由客户端自行统计（只算你自己放置的方块，创造/生存、单人多人都有效）。
- **"本次游玩"小窗** — 实时显示当前会话的时长、放置、破坏、拾取、合成、死亡、击杀，离开世界自动清零。
- **统计置顶** — 右键任意条目即可置顶（金星标记），置顶跨存档、跨服务器、跨游戏端共享。
- **时间双口径** — 明确区分**现实时间**（真实墙钟）与**游戏内时间**（游戏刻），所有时间统计都带标注，不再混淆。
- **数据管理** — 明文 JSON 存储，可手动编辑；游戏内管理页支持按位置 / 按档案 / 全部删除（带二次确认）。

## 安装

1. 安装 Forge（Forge 50.x（官方无 1.20.5 构建））；
2. 把 `blackannins_statistics-forge-1.20.6-1.0.0.jar` 放入 `.minecraft/mods`（或你启动器对应的 mods 目录）；
3. 进入游戏即可，**服务端不需要安装**，原版服务器和第三方服务器都能用。

## 快速上手

- **打开统计中心**：Esc 菜单 → "统计信息"（已被本模组接管）；或标题屏右下角的"统计中心"按钮；
- **切换分类**：全局 / 单人游戏 / 多人游戏，点任意存档或服务器下钻；
- **切分页查看**：通用 / 方块 / 物品 / 生物四类，支持搜索与点列头排序；
- **置顶**：右键任意条目；
- **切换档案**：右上角档案按钮（多账号 / 离线名各自独立）；
- **本次游玩小窗**：打开任意界面（Esc / 统计中心等）即显示；按 **F9** 可让它在游戏画面常显；
- **客户端命令**：`/bas open`（打开统计中心）、`/bas status`（会话状态）、`/bas vanilla`（原版统计界面）等。

## 本版本的适配差异（与主版本对照）

- TickEvent 拆分：ClientTickEvent.Post 子类（无 Phase 判定）
- RenderGuiEvent 移除："本次游玩"游戏画面路径改用 AddGuiOverlayLayersEvent 注册 HUD 层
- renderBackground 四参（带鼠标坐标）

## 数据与隐私

- 所有数据以**明文 JSON** 保存在本地 `.minecraft/blackannins_statistics/` 目录，**不上传、不联网、无遥测**；
- 数据文件可直接手动编辑或备份；损坏文件会自动隔离到 `backups/`，不会影响游戏；
- 游戏内管理页提供图形化删除：按位置 / 按档案 / 全部（均带二次确认）。

<details>
<summary><b>数据目录与文件结构</b>（点击展开）</summary>

```
<数据目录>/
├── profiles.json                                统一索引：档案信息 + 置顶数据
├── backups/                                     损坏文件自动隔离
└── data/
    ├── singleplayer/<存档名>/<玩家UUID>.json
    └── multiplayer/<服务器地址>/<玩家UUID>.json
```

默认数据目录为 `C:\Users\<用户名>\AppData\Roaming\.minecraft\blackannins_statistics\`，可在配置中修改；固定路径不可写时自动回退到游戏运行目录。

</details>

<details>
<summary><b>常见问题</b>（点击展开）</summary>

<details>
<summary><b>换了客户端 / 换了游戏版本，数据还在吗？</b></summary>

在。数据保存在固定位置，与启动器、客户端安装目录无关——这就是本模组的核心设计。只要统计的是同一个账号，所有端的数据都会汇入同一份档案（本版本与其他 Forge / NeoForge / Fabric 版本共用同一数据目录）。
</details>

<details>
<summary><b>服务器需要装这个模组吗？</b></summary>

不需要。本模组是纯客户端模组，原版服务器和第三方服务器都能用。把它装进服务器也不会产生任何行为。
</details>

<details>
<summary><b>放置方块的数量是怎么统计的？</b></summary>

原版没有"方块放置"这项统计。本模组在客户端记录放置意图，并确认方块真实落在世界里后才计数——所以创造/生存、单人多人都有效，且只统计你自己放置的方块。FTB Ultimine 连锁放置的每个方块也都会分别计入。
</details>

<details>
<summary><b>数据文件坏了 / 想重置数据怎么办？</b></summary>

损坏文件会被自动隔离到 `backups/` 目录，不影响游戏运行。想重置某个存档或服务器的数据，打开统计中心 → 管理 → 删除对应条目即可。
</details>

</details>

## 构建

需要 JDK 21：

```bash
git clone <本仓库>
cd blackanninsstatistics-forge-1.20.6
gradlew build        # 产物在 build/libs/
gradlew test         # 运行单元测试
gradlew runClient    # 启动开发客户端
```

## 测试状态

- [x] `gradlew build` 全绿（编译 + 3 个核心层单元测试：PlayLocation / StatSnapshot / StoreIO）
- [x] 实机验证通过：进世界、数据目录、客户端就绪全对，零报错

---

<a id="english"></a>

# BlackAnnin's Statistics — Forge 1.20.6 (English)

A client-side statistics mod for Minecraft 1.20.6 (Forge) that fixes what the vanilla statistics screen never did: **per-world and per-server records, combined into one hub, kept across client and version changes**.

## Features

- **Complete statistics** — play time, blocks broken/placed, mobs killed/killed by, items crafted/used/broken/picked up/dropped, deaths, jumps, every distance stat; blocks, items and entities added by other mods are automatically included (with icons).
- **Three-level views** — Global / Singleplayer (per world) / Multiplayer (per server); drill down into any world, or view combined totals.
- **Multi-profile** — data from different accounts / offline names is tracked separately; view one profile or everything combined.
- **Placed-block statistics** — vanilla has no "blocks placed" stat; this mod counts them client-side (your own placements only, works in creative & survival, singleplayer & multiplayer).
- **"This session" HUD** — live per-session stats: time, placed, mined, picked up, crafted, deaths, kills. Shows automatically whenever a screen is open, toggle on-screen display with **F9**, resets when you leave a world. Corner, opacity and every line configurable.
- **Pin statistics** — right-click any entry to pin it (gold star); pins survive across worlds, servers and game instances.
- **Two time scales** — **real time** (wall clock) and **in-game time** (game ticks) are clearly labeled on every time stat.
- **Data management** — plain JSON you can edit by hand; in-game deletion per location / per profile / everything (with confirmation).

## Installation

1. Install Forge for Minecraft 1.20.6;
2. Drop `blackannins_statistics-forge-1.20.6-1.0.0.jar` into your `mods` folder;
3. That's it — **nothing to install server-side**, works on vanilla and third-party servers alike.

## Version-specific notes

- TickEvent split: ClientTickEvent.Post subclass (no phase check)
- RenderGuiEvent removed: the on-screen HUD path registers via AddGuiOverlayLayersEvent
- renderBackground takes four args (mouse coordinates)

## Data & privacy

- All data is stored as **plain JSON** in your local `.minecraft/blackannins_statistics/` folder — **no uploads, no network, no telemetry**;
- Files can be edited or backed up by hand; corrupted files are quarantined into `backups/` automatically;
- The in-game Manage tab deletes per location / per profile / everything (all with confirmation dialogs).

## Building

Requires JDK 21:

```bash
git clone <this repo>
cd blackanninsstatistics-forge-1.20.6
gradlew build        # jar lands in build/libs/
gradlew test         # unit tests
gradlew runClient    # dev client
```

## License

Released under the [MIT License](LICENSE). Based on the Forge MDK template.
