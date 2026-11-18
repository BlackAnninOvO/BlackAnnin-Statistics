#!/bin/bash
# 一键重建某项目的 local-maven 本地兜底仓库（国内受限网络环境使用）。
#
# 背景：maven.neoforged.net 在国内被 DNS 污染 + SNI 阻断；jsdu 镜像缺失部分工件
# （minecraft-dependencies POM、jst 完整 jar、java-objc-bridge 等）。本脚本按版本
# 重建完整的 local-maven/。海外/CI 网络无需本脚本（官方源直连即可构建）。
#
# 用法（仓库根执行）：
#   bash build-tools/setup_local_maven.sh <项目目录，如 neoforge-ports/blackanninsstatistics-neoforge-1.21.3>
#
# 重建完成后建议在项目 gradle.properties 中加入 useJsduMirror=true（仅国内网络需要）。
set -u

# 定位仓库根（本脚本位于 build-tools/ 下）
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SELF_DIR/.." && pwd)"
PROJ="$1"
[ -z "$PROJ" ] && { echo "用法: bash build-tools/setup_local_maven.sh <项目目录>"; exit 1; }
cd "$REPO_ROOT" || exit 1
[ -d "$PROJ" ] || { echo "项目目录不存在: $PROJ"; exit 1; }

# 读取版本
MC=$(grep "^minecraft_version=" "$PROJ/gradle.properties" | cut -d= -f2 | tr -d '\r')
NF=$(grep "^neo_version=" "$PROJ/gradle.properties" | cut -d= -f2 | tr -d '\r')
echo "═══ setup local-maven: MC=$MC NeoForge=$NF ═══"
LM="$PROJ/local-maven"

# 1) minecraft-dependencies POM（从 Mojang 官方 version JSON 生成）
# 注意：Windows 的 python 不识别 Git Bash 的 /x/... 路径，统一转 Windows 路径
SELF_DIR_WIN=$(cygpath -w "$SELF_DIR")
mkdir -p "$LM/net/neoforged/minecraft-dependencies/$MC"
python -I "$SELF_DIR_WIN\\gen_mcdeps.py" "$MC" "$(cygpath -w "$LM/net/neoforged/minecraft-dependencies/$MC/minecraft-dependencies-$MC.pom")" || exit 1

# 2) neoforge 六件套（jsdu 镜像；moddev-config 自 userdev 提取）
bash "$SELF_DIR/fetch_neoforge_set.sh" "$NF" "$MC" "$LM" || echo "!! neoforge 六件套下载有失败项（见上方日志）"

# 3) jst-cli-bundle 完整版（jsdu 上截断损坏 → BMCLAPI；pom 用 seed）
JST_VER="2.0.11"
mkdir -p "$LM/net/neoforged/jst/jst-cli-bundle/$JST_VER"
if [ ! -s "$LM/net/neoforged/jst/jst-cli-bundle/$JST_VER/jst-cli-bundle-$JST_VER.jar" ]; then
  curl -sL --max-time 300 --retry 2 \
    -o "$LM/net/neoforged/jst/jst-cli-bundle/$JST_VER/jst-cli-bundle-$JST_VER.jar" \
    "https://bmclapi2.bangbang93.com/maven/net/neoforged/jst/jst-cli-bundle/$JST_VER/jst-cli-bundle-$JST_VER.jar" \
    && echo "OK jst-cli-bundle jar（BMCLAPI）"
fi
cp "$SELF_DIR/seed/jst-cli-bundle-$JST_VER.pom" "$LM/net/neoforged/jst/jst-cli-bundle/$JST_VER/" 2>/dev/null

# 4) java-objc-bridge 1.1（jar 来自 libraries.minecraft.net；pom 用 seed）
mkdir -p "$LM/ca/weblite/java-objc-bridge/1.1"
if [ ! -s "$LM/ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar" ]; then
  curl -sL --max-time 120 --retry 2 \
    -o "$LM/ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar" \
    "https://libraries.minecraft.net/ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar" \
    && echo "OK java-objc-bridge jar"
fi
cp "$SELF_DIR/seed/java-objc-bridge-1.1.pom" "$LM/ca/weblite/java-objc-bridge/1.1/" 2>/dev/null

# 5) 校验
echo "═══ 产物清单 ═══"
find "$LM" -type f | sed "s|$LM/||" | sort

# 6) 提示镜像开关（国内网络需要）
GP="$PROJ/gradle.properties"
if ! grep -q "useJsduMirror" "$GP"; then
  printf '\n# 国内受限网络：启用 jsdu 镜像重写（海外/CI 删除本行即走官方源）\nuseJsduMirror=true\n' >> "$GP"
  echo "已在 $GP 启用 useJsduMirror=true（国内网络构建需要）"
fi
echo "═══ 完成。现在可以: cd $PROJ && ./gradlew build ═══"
