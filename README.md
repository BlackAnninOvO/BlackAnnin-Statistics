<div align="center">

# BlackAnnin's Statistics — 多版本源码仓库

**一个仓库，收录 NeoForge / Forge / Fabric 三大加载器的全部支持版本源码 —— 1.20.1 至 26.3**

**One repository with the full source for every supported version on NeoForge / Forge / Fabric — 1.20.1 through 26.3.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%E2%86%92%2026.3-blue)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%20%E2%86%92%2026.3-orange)](https://neoforged.net/)
[![Forge](https://img.shields.io/badge/Forge-1.20.1%20%E2%86%92%2026.3-red)](https://files.minecraftforge.net/)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.1%20%E2%86%92%2026.3-yellow)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Side](https://img.shields.io/badge/Side-Client%20Only-important)

</div>

---

## 仓库结构

每个 Minecraft 版本线一个独立目录（各自的 Gradle 项目、各自的 README）：

**发布版本与源码目录对照**（同组内 API 一致，一个 jar 覆盖多版本；构建/发布宿主 = 组内最低版本目录）：

| 发布 jar 覆盖的 Minecraft | 构建目录（宿主） | NeoForge | Java |
|---|---|---|---|
**NeoForge**（`neoforge/` 目录）：

| 发布 jar 覆盖的 Minecraft | 构建目录（宿主） | NeoForge | Java |
|---|---|---|---|
| 1.21.1 | [`neoforge/1.21.1/`](neoforge/1.21.1/) | 21.1.x | 21 |
| 1.21.3 | [`neoforge/1.21.3/`](neoforge/1.21.3/) | 21.3.x | 21 |
| **1.21.4 – 1.21.5** | [`neoforge/1.21.4-5/`](neoforge/1.21.4-5/) | 21.4.x | 21 |
| **1.21.6 – 1.21.8** | [`neoforge/1.21.6-8/`](neoforge/1.21.6-8/) | 21.6/21.7/21.8 | 21 |
| **1.21.9 – 1.21.10** | [`neoforge/1.21.9-10/`](neoforge/1.21.9-10/) | 21.9/21.10 | 21 |
| 1.21.11 | [`neoforge/1.21.11/`](neoforge/1.21.11/) | 21.11.x | 21 |
| 26.1（覆盖 26.1.1/.2） | [`neoforge/26.1/`](neoforge/26.1/) | 26.1.x | 25 |
| **26.2 – 26.3** | [`neoforge/26.2-26.3/`](neoforge/26.2-26.3/) | 26.2/26.3 | 25 |

**Forge**（`forge/` 目录）：

| 发布 jar 覆盖的 Minecraft | 构建目录（宿主） | Forge | Java |
|---|---|---|---|
| 1.20.1 | [`forge/1.20.1/`](forge/1.20.1/) | 48.x | 17 |
| 1.20.2 – 1.20.4 | [`forge/1.20.2-4/`](forge/1.20.2-4/) | 48.x | 17 |
| 1.20.6 | [`forge/1.20.6/`](forge/1.20.6/) | 49.x | 17 |
| 1.21 – 1.21.1 | [`forge/1.21-1.21.1/`](forge/1.21-1.21.1/) | 52.x | 21 |
| 1.21.3 | [`forge/1.21.3/`](forge/1.21.3/) | 53.x | 21 |
| 1.21.4 – 1.21.5 | [`forge/1.21.4-5/`](forge/1.21.4-5/) | 54.x | 21 |
| 1.21.6 – 1.21.8 | [`forge/1.21.6-8/`](forge/1.21.6-8/) | 55.x | 21 |
| 1.21.9 – 1.21.11 | [`forge/1.21.9-11/`](forge/1.21.9-11/) | 56.x | 21 |
| **26.1 – 26.3** | [`forge/26.1-26.3/`](forge/26.1-26.3/) | 57.x | 25 |

**Fabric**（`fabric/` 目录）：

| 发布 jar 覆盖的 Minecraft | 构建目录（宿主） | Fabric Loader |
|---|---|---|
| 1.21.1 | [`fabric/1.21.1/`](fabric/1.21.1/) | 0.16.x |
| 1.21.11 | [`fabric/1.21.11/`](fabric/1.21.11/) | 0.17.x |
| 1.21.2 | [`fabric/1.21.2/`](fabric/1.21.2/) | 0.16.x |
| 1.21.4 | [`fabric/1.21.4/`](fabric/1.21.4/) | 0.16.x |
| 1.21.5 | [`fabric/1.21.5/`](fabric/1.21.5/) | 0.16.x |
| 1.21.6 | [`fabric/1.21.6/`](fabric/1.21.6/) | 0.16.x |
| 1.21.9 | [`fabric/1.21.9/`](fabric/1.21.9/) | 0.17.x |
| 26.1 | [`fabric/26.1/`](fabric/26.1/) | 0.17.x |
| 26.2 | [`fabric/26.2/`](fabric/26.2/) | 0.17.x |
| 26.3 | [`fabric/26.3/`](fabric/26.3/) | 0.17.x |

> 同组目录的源码完全一致（1.21.5/1.21.7/1.21.8/1.21.10/26.3 为隔离测试副本），发布 jar 从宿主目录构建，
> `mods.toml` 声明该组的 Minecraft 版本范围（如 1.21.6 的 jar 声明 `[1.21.6,1.21.9)`）。

> 1.21.2 无 NeoForge/Fabric 稳定版（MC 1.21.2 发布 1 天即被 1.21.3 取代），社区惯例跳过；Forge 侧 1.21.1 与 1.21.3 之间为独立适配。
> 各版本的功能、数据格式、mod id 与主版本完全一致——**跨版本游玩时统计数据自动汇入同一份数据目录**。
> 各版本的功能、数据格式、mod id 与主版本完全一致——**跨版本游玩时统计数据自动汇入同一份数据目录**。

## 构建某个版本

需要对应版本的 JDK（1.21.x → 21，26.x → 25）：

```bash
git clone <本仓库>
cd <版本目录>

# 海外 / CI 网络：直接构建（官方 Maven 源解析全部依赖）
./gradlew build        # 产物在 build/libs/
./gradlew test         # 单元测试

# 国内受限网络（maven.neoforged.net 被阻断时）：先重建本地兜底仓库
bash build-tools/setup_local_maven.sh <版本目录>
# 脚本会从镜像下载 NeoForge 工件、生成 minecraft-dependencies POM，
# 并在项目 gradle.properties 中写入 useJsduMirror=true（海外删除该行即可）
./gradlew build
```

## CI

`.github/workflows/build.yml` 对全部 13 个目录做矩阵构建（GitHub Actions 直连官方源，无需本地兜底仓库）。

## 数据与隐私

与各版本一致：明文 JSON、本地存储、**不上传、不联网、无遥测**。详见各版本目录内的 README。

## License

Released under the [MIT License](LICENSE). Based on the [NeoForged MDK](https://github.com/NeoForged/MDK) template.
