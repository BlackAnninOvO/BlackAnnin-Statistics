#!/bin/bash
# 下载指定版本的 neoforge 六件套到项目 local-maven
# 用法: fetch_neoforge_set.sh <NF版本> <MC版本> <项目local-maven路径>
set -u
NF="$1"; MC="$2"; DEST="$3"
JSDU="https://nexus.jsdu.cn/repository"
BMCL="https://bmclapi2.bangbang93.com/maven"
OUT="$DEST/net/neoforged/neoforge/$NF"
mkdir -p "$OUT"

fetch() { # <远程url> <输出文件>
  local url="$1" out="$2" tries=0
  while [ $tries -lt 4 ]; do
    curl -sL --max-time 300 --retry 2 -o "$out" -w "%{http_code}" "$url" > /tmp/fetch_code.txt
    code=$(cat /tmp/fetch_code.txt)
    size=$(stat -c %s "$out" 2>/dev/null || echo 0)
    if [ "$code" = "200" ] && [ "$size" -gt 100 ]; then
      echo "OK  $size  $out"; return 0
    fi
    tries=$((tries+1)); sleep 3
  done
  echo "FAIL($code,$size)  $out"; return 1
}

rc=0
for f in "pom" "module" "userdev.jar" "universal.jar" "sources.jar"; do
  # pom/module 的 Maven 文件名是 .pom/.module
  case "$f" in
    pom) name="neoforge-$NF.pom"; remote="neoforge-$NF.pom";;
    module) name="neoforge-$NF.module"; remote="neoforge-$NF.module";;
    *) name="neoforge-$NF-$f"; remote="$name";;
  esac
  if [ -s "$OUT/$name" ]; then echo "SKIP $name"; continue; fi
  rm -f "$OUT/neoforge-$NF-$f"  # 清理旧命名（历史 bug 遗留）
  fetch "$JSDU/net/neoforged/neoforge/$NF/$remote" "$OUT/$name" || rc=1
done
# moddev-config: jsdu 没有；BMCLAPI 不稳定（502）。
# 但已验证 userdev jar 内的 config.json 与官方 moddev-config.json 完全等价 —— 直接提取。
name="neoforge-$NF-moddev-config.json"
if [ -s "$OUT/$name" ] && ! grep -q "<html>" "$OUT/$name" 2>/dev/null; then echo "SKIP $name"; else
  userdev="$OUT/neoforge-$NF-userdev.jar"
  if [ -s "$userdev" ]; then
    tmpdir=$(mktemp -d)
    unzip -o -q -j "$userdev" "config.json" -d "$tmpdir" && mv "$tmpdir/config.json" "$OUT/$name" && echo "OK(extract) $name"
    rm -rf "$tmpdir"
  fi
  [ -s "$OUT/$name" ] || { fetch "$BMCL/net/neoforged/neoforge/$NF/$name" "$OUT/$name" || rc=1; }
  # 校验不是错误页
  if grep -q "<html>" "$OUT/$name" 2>/dev/null; then echo "FAIL(html) $name"; rc=1; fi
fi
exit $rc
