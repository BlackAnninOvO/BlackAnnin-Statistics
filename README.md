<div align="center">

# BlackAnnin's Statistics — 多版本源码仓库

**一个仓库，包含 NeoForge 全部支持版本的完整源码 —— 1.21.1 至 26.3**

**One repository with the full source for every supported NeoForge version — 1.21.1 through 26.3.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%E2%86%92%2026.3-blue)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1%20%E2%86%92%2026.3-orange)](https://neoforged.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Side](https://img.shields.io/badge/Side-Client%20Only-important)

</div>

---

## 仓库结构

每个 Minecraft 版本线一个独立目录（各自的 Gradle 项目、各自的 README）：

| Minecraft | 目录 | NeoForge | Java |
|---|---|---|---|
| 1.21.1 | [`blackanninsstatistics-template-1.21.1/`](blackanninsstatistics-template-1.21.1/) | 21.1.x | 21 |
| 1.21.3 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.3/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.3/) | 21.3.x | 21 |
| 1.21.4 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.4/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.4/) | 21.4.x | 21 |
| 1.21.5 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.5/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.5/) | 21.5.x | 21 |
| 1.21.6 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.6/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.6/) | 21.6.x | 21 |
| 1.21.7 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.7/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.7/) | 21.7.x | 21 |
| 1.21.8 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.8/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.8/) | 21.8.x | 21 |
| 1.21.9 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.9/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.9/) | 21.9.x | 21 |
| 1.21.10 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.10/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.10/) | 21.10.x | 21 |
| 1.21.11 | [`neoforge-ports/blackanninsstatistics-neoforge-1.21.11/`](neoforge-ports/blackanninsstatistics-neoforge-1.21.11/) | 21.11.x | 21 |
| 26.1（覆盖 26.1.1/.2） | [`neoforge-ports/blackanninsstatistics-neoforge-26.1/`](neoforge-ports/blackanninsstatistics-neoforge-26.1/) | 26.1.x | 25 |
| 26.2 | [`neoforge-ports/blackanninsstatistics-neoforge-26.2/`](neoforge-ports/blackanninsstatistics-neoforge-26.2/) | 26.2.x | 25 |
| 26.3 | [`neoforge-ports/blackanninsstatistics-neoforge-26.3/`](neoforge-ports/blackanninsstatistics-neoforge-26.3/) | 26.3.x | 25 |

> 1.21.2 无 NeoForge 稳定版（MC 1.21.2 发布 1 天即被 1.21.3 取代），社区惯例跳过。
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
