<div align="center">

# BlackAnnin's Statistics

**在 Minecraft 中完整记录并查看你的游玩数据 —— 按存档、按服务器，跨客户端累计**

**Full per-world & per-server statistics for Minecraft — accumulated across client and version changes.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.6-blue)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.6.x-orange)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Side](https://img.shields.io/badge/Side-Client%20Only-important)

[English](#english) | 中文说明在下方

</div>

---

## 为什么做这个模组

原版的统计界面有两个我一直不满意的地方：**只能看当前这一次的数据**，换个存档、换个服务器就看不到累计了；而且方块放置这种数据压根没有统计。

这个模组解决的就是这些：**你在每个存档、每个服务器里的数据会被分别记录，并汇总到同一个统计中心**。换大版本、换启动器、换电脑上的客户端都没关系——数据存在一个固定位置，会一直接着累计。

<!-- 建议在这里插入截图：统计中心各页签 + 本次游玩小窗
![统计中心](https://你的图床链接/hub.png)
-->

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

1. 安装 [NeoForge](https://neoforged.net/) 1.21.6；
2. 把 `blackannins_statistics-neoforge-1.0.0.jar` 放入 `.minecraft/mods`（或你启动器对应的 mods 目录）；
3. 进入游戏即可，**服务端不需要安装**，原版服务器和第三方服务器都能用。

## 快速上手

- **打开统计中心**：Esc 菜单 → "统计信息"（已被本模组接管）；或标题屏右下角的"统计中心"按钮；
- **切换分类**：全局 / 单人游戏 / 多人游戏，点任意存档或服务器下钻；
- **切分页查看**：通用 / 方块 / 物品 / 生物四类，支持搜索与点列头排序；
- **置顶**：右键任意条目；
- **切换档案**：右上角档案按钮（多账号 / 离线名各自独立）；
- **本次游玩小窗**：打开任意界面（Esc / 统计中心等）即显示；按 **F9** 可让它在游戏画面常显；
- **客户端命令**：`/bas open`（打开统计中心）、`/bas status`（会话状态）、`/bas vanilla`（原版统计界面）等。

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

默认数据目录为 `C:\Users\ASUS\AppData\Roaming\.minecraft\blackannins_statistics\`，可在配置中修改；固定路径不可写时自动回退到游戏运行目录。

</details>

## 常见问题

<details>
<summary><b>换了客户端 / 换了游戏版本，数据还在吗？</b></summary>

在。数据保存在固定位置，与启动器、客户端安装目录无关——这就是本模组的核心设计。只要统计的是同一个账号，所有端的数据都会汇入同一份档案。
</details>

<details>
<summary><b>服务器需要装这个模组吗？</b></summary>

不需要。本模组是纯客户端模组，原版服务器和第三方服务器都能用。把它装进服务器也不会产生任何行为。
</details>

<details>
<summary><b>"游戏内时间"和"现实时长"为什么差很多？</b></summary>

两者口径不同，故意分开显示：

- **游戏内时长**来自服务器原版统计，从统计口径的起点开始累计（比你装模组更早的时间也在内），服务器开着挂机它也会走；
- **现实时长**从装上本模组之后才开始记录。

所以"游戏内 133 小时 / 现实 52 小时"是正常现象，两者对比反而能看出模组覆盖了多久的历史。
</details>

<details>
<summary><b>放置方块的数量是怎么统计的？</b></summary>

原版没有"方块放置"这项统计。本模组在客户端记录放置意图，并确认方块真实落在世界里后才计数——所以创造/生存、单人多人都有效，且只统计你自己放置的方块。FTB Ultimine 连锁放置的每个方块也都会分别计入。
</details>

<details>
<summary><b>使用 FTB Ultimine 连锁会有影响吗？</b></summary>

没有。连锁挖掘的每个方块都通过服务端正常破坏，计入破坏统计；连锁放置的每个方块也会分别计入放置统计。
</details>

<details>
<summary><b>数据文件坏了 / 想重置数据怎么办？</b></summary>

损坏文件会被自动隔离到 `backups/` 目录，不影响游戏运行。想重置某个存档或服务器的数据，打开统计中心 → 管理 → 删除对应条目即可。
</details>

## 配置

全部配置可在 **模组列表 → BlackAnnin's Statistics → 配置** 中修改（支持中文界面），也可直接编辑 `config/blackannins_statistics-client.toml`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 替换原版统计界面 | 开 | 打开"统计信息"时显示本模组统计中心 |
| 自动保存间隔（秒） | 60 | 游玩中自动保存的周期 |
| 多人统计同步间隔（秒） | 30 | 向服务器请求统计数据的周期 |
| 数据目录覆盖 | 空 | 数据存储的绝对路径，留空使用默认 |
| 全局页默认合计所有档案 | 关 | 全局页是否显示全部档案之和 |
| 启用本次游玩小窗 | 开 | "本次游玩"小窗总开关 |
| 小窗所在角落 | 左上 | 小窗锚定位置（左上/右上/左下/右下） |
| 小窗背景不透明度 | 56 | 0 = 完全透明，100 = 不透明 |
| 小窗显示现实时长 | 开 | 会话现实时长一行 |
| 小窗显示游戏内时长 | 开 | 会话游戏内时长一行 |
| 小窗显示放置方块 | 开 | 会话放置计数 |
| 小窗显示破坏方块 | 开 | 会话破坏计数 |
| 小窗显示拾取物品 | 开 | 会话拾取计数 |
| 小窗显示合成物品 | 开 | 会话合成计数 |
| 小窗显示死亡次数 | 开 | 会话死亡计数 |
| 小窗显示击杀生物 | 开 | 会话击杀计数 |

## 构建

需要 JDK 21：

```bash
git clone <本仓库>
cd blackanninsstatistics-neoforge-1.21.6-1.21.8
gradlew build        # 产物在 build/libs/
gradlew test         # 运行单元测试
gradlew runClient    # 启动开发客户端
```

---

<a id="english"></a>

# BlackAnnin's Statistics (English)

A client-side statistics mod for Minecraft 1.21.6 (NeoForge) that fixes what the vanilla statistics screen never did: **per-world and per-server records, combined into one hub, kept across client and version changes**.

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

1. Install [NeoForge](https://neoforged.net/) 1.21.6;
2. Drop `blackannins_statistics-neoforge-1.0.0.jar` into your `mods` folder;
3. That's it — **nothing to install server-side**, works on vanilla and third-party servers alike.

## Getting started

- **Stats hub**: Esc → "Statistics" (taken over by this mod), or the hub button on the title screen;
- **Tabs**: Global / Singleplayer / Multiplayer, drill down into any world or server;
- **Categories**: General / Blocks / Items / Mobs, with search and sortable columns;
- **Pin**: right-click any entry;
- **Profiles**: button at the top right (each account / offline name is tracked separately);
- **Session HUD**: shows whenever a screen is open; press **F9** to keep it on-screen during gameplay;
- **Client commands**: `/bas open`, `/bas status`, `/bas vanilla`, and more.

## Data & privacy

- All data is stored as **plain JSON** in your local `.minecraft/blackannins_statistics/` folder — **no uploads, no network, no telemetry**;
- Files can be edited or backed up by hand; corrupted files are quarantined into `backups/` automatically;
- The in-game Manage tab deletes per location / per profile / everything (all with confirmation dialogs).

<details>
<summary><b>Data layout</b> (click to expand)</summary>

```
<data folder>/
├── profiles.json                        unified index: profiles + pinned entries
├── backups/                             corrupted-file quarantine
└── data/
    ├── singleplayer/<world>/<player UUID>.json
    └── multiplayer/<server address>/<player UUID>.json
```

The default folder is `C:\Users\ASUS\AppData\Roaming\.minecraft\blackannins_statistics\`, override it in the config; it falls back to the game directory if the fixed path is unavailable.

</details>

## FAQ

<details>
<summary><b>Is my data kept when I switch clients or game versions?</b></summary>

Yes — data lives in a fixed folder, independent of your launcher or client installation. That is the core design of this mod: everything you play under the same account flows into the same profile.
</details>

<details>
<summary><b>Does the server need this mod?</b></summary>

No. It is purely client-side — vanilla and third-party servers both work, and installing it on a server changes nothing.
</details>

<details>
<summary><b>Why are "in-game time" and "real time" so different?</b></summary>

They use different scales on purpose:

- **In-game time** comes from server-side vanilla stats and starts from the stat baseline (it includes time from before you installed the mod, and it keeps running while the server is up, even AFK);
- **Real time** only starts counting once the mod is installed.

"In-game 133 h / real 52 h" is therefore normal — the gap shows how much history predates the mod.
</details>

<details>
<summary><b>How are placed blocks counted?</b></summary>

Vanilla has no "blocks placed" stat. This mod records placement intent on the client and counts a block only after it really appears in the world — so it works in creative & survival, singleplayer & multiplayer, and only counts blocks **you** place. Every block from FTB Ultimine chain placing is counted individually.
</details>

<details>
<summary><b>Does FTB Ultimine chain mining interfere?</b></summary>

No. Every block mined through chain breaking is destroyed through the normal server flow and lands in the mined stats; every block from chain placing is counted in the placed stats.
</details>

<details>
<summary><b>Data files corrupted / want to reset?</b></summary>

Corrupted files are quarantined into `backups/` automatically without affecting the game. To reset data for a world or server, open the hub → Manage → delete the entry.
</details>

## Configuration

Everything lives in **Mods → BlackAnnin's Statistics → Config** (localized), or edit `config/blackannins_statistics-client.toml` directly:

| Option | Default | Description |
|---|---|---|
| Replace vanilla statistics screen | On | The "Statistics" button opens this mod's hub |
| Autosave interval (seconds) | 60 | Periodic save while playing |
| Multiplayer stats sync interval (seconds) | 30 | How often stats are requested from the server |
| Data directory override | empty | Absolute path for stored data, empty = default |
| Aggregate all profiles on Global tab | Off | Show the sum of all profiles by default |
| Enable this-session HUD | On | Master toggle for the session HUD |
| HUD corner | Top Left | Where the HUD is anchored (TL/TR/BL/BR) |
| HUD background opacity | 56 | 0 = fully transparent, 100 = opaque |
| HUD: show real time | On | Real-time line |
| HUD: show in-game time | On | In-game time line |
| HUD: show placed blocks | On | Session placed count |
| HUD: show mined blocks | On | Session mined count |
| HUD: show picked up items | On | Session pickup count |
| HUD: show crafted items | On | Session craft count |
| HUD: show deaths | On | Session deaths |
| HUD: show mob kills | On | Session mob kills |

## Building

Requires JDK 21:

```bash
git clone <this repo>
cd blackanninsstatistics-neoforge-1.21.6-1.21.8
gradlew build        # jar lands in build/libs/
gradlew test         # unit tests
gradlew runClient    # dev client
```

## License

Released under the [MIT License](LICENSE). Based on the [NeoForged MDK](https://github.com/NeoForged/MDK) template.
