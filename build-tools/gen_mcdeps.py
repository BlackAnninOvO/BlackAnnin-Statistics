#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从 Mojang 官方 version JSON 生成 net.neoforged:minecraft-dependencies:<mcver> 的 POM。

背景：maven.neoforged.net 在本机网络被 DNS 污染 + SNI 阻断，jsdu/BMCLAPI 镜像均未同步
minecraft-dependencies（neoform .module 以 endorseStrictVersions 硬依赖它）。
本工具用 piston-meta 的官方 version JSON（可达）重建 POM。

规则（与 1.21.1 手工重建的已知正确 POM 对齐并校验）：
- 仅收录 downloads.artifact 存在的库（classifier-only 的 linux/mac natives 剔除）
- 主工件 -> <scope>compile</scope>
- natives-windows classifier 工件 -> <scope>runtime</scope>（runClient 需要）
- java-objc-bridge 按官方 JSON 原样收录

用法：python -I gen_mcdeps.py <mcver> <输出pom路径> [--validate <基准pom>]
"""
import json
import sys
import urllib.request
import re
import xml.etree.ElementTree as ET

MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
NS = "{http://maven.apache.org/POM/4.0.0}"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "gen-mcdeps/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def version_json_url(mcver):
    manifest = json.loads(fetch(MANIFEST_URL))
    for v in manifest["versions"]:
        if v["id"] == mcver:
            return v["url"]
    raise SystemExit(f"MC version not found in manifest: {mcver}")


def parse_gav(name):
    parts = name.split(":")
    g, a, v = parts[0], parts[1], parts[2]
    classifier = parts[3] if len(parts) > 3 else None
    return g, a, v, classifier


def main():
    mcver = sys.argv[1]
    out_path = sys.argv[2]
    validate_against = None
    if len(sys.argv) > 4 and sys.argv[3] == "--validate":
        validate_against = sys.argv[4]

    vj = json.loads(fetch(version_json_url(mcver)))
    java_major = vj.get("javaVersion", {}).get("majorVersion")
    print(f"[{mcver}] javaVersion.majorVersion = {java_major}")

    compile_deps = []
    runtime_native_deps = []
    skipped_classifier_only = []
    for lib in vj.get("libraries", []):
        name = lib.get("name")
        if not name:
            continue
        g, a, v, cls = parse_gav(name)
        downloads = lib.get("downloads", {})
        artifact = downloads.get("artifact")
        if artifact is None or not artifact.get("path"):
            # classifier-only 条目（如 linux/mac natives）：剔除
            skipped_classifier_only.append(name)
            continue
        path = artifact["path"]
        if cls is not None:
            # classifier 条目只保留精确的 windows 主 natives（arm64/x86 变体与 linux/mac 一律剔除）
            if cls == "natives-windows":
                runtime_native_deps.append((g, a, v, cls))
            else:
                skipped_classifier_only.append(name)
            continue
        compile_deps.append((g, a, v, cls))

    def emit(dep):
        g, a, v, cls = dep
        s = f"    <dependency>\n      <groupId>{g}</groupId>\n      <artifactId>{a}</artifactId>\n      <version>{v}</version>\n"
        if cls:
            s += f"      <classifier>{cls}</classifier>\n"
        s += "      <scope>compile</scope>\n    </dependency>\n"
        return s

    def emit_runtime(dep):
        g, a, v, cls = dep
        s = f"    <dependency>\n      <groupId>{g}</groupId>\n      <artifactId>{a}</artifactId>\n      <version>{v}</version>\n"
        if cls:
            s += f"      <classifier>{cls}</classifier>\n"
        s += "      <scope>runtime</scope>\n    </dependency>\n"
        return s

    compile_deps.sort()
    runtime_native_deps.sort()
    # 保障 java-objc-bridge 在 macOS 客户端加载链里的存在（官方 JSON 各版本都有）
    body = "".join(emit(d) for d in compile_deps) + "".join(emit_runtime(d) for d in runtime_native_deps)

    pom = f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- 本地重建工件：上游 maven.neoforged.net 在本机网络不可达，各镜像均未同步此 POM。
     依赖清单取自 Mojang 官方 version JSON（piston-meta，权威来源），
     由 _tools/gen_mcdeps.py 生成（规则与 1.21.1 已验证 POM 一致：
     linux/mac natives 剔除；保留 Windows natives classifier 供 runClient 使用）。 -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>net.neoforged</groupId>
  <artifactId>minecraft-dependencies</artifactId>
  <version>{mcver}</version>
  <packaging>pom</packaging>
  <dependencies>
{body}  </dependencies>
</project>
"""
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(pom)
    print(f"[{mcver}] compile deps: {len(compile_deps)}, natives-windows: {len(runtime_native_deps)}, "
          f"skipped classifier-only: {len(skipped_classifier_only)}")
    if skipped_classifier_only:
        for s in skipped_classifier_only[:12]:
            print("  skip:", s)
        if len(skipped_classifier_only) > 12:
            print(f"  ... and {len(skipped_classifier_only)-12} more")

    if validate_against:
        def dep_set(pom_path):
            root = ET.parse(pom_path).getroot()
            deps = set()
            for d in root.iter(NS + "dependency"):
                g = d.findtext(NS + "groupId")
                a = d.findtext(NS + "artifactId")
                v = d.findtext(NS + "version")
                c = d.findtext(NS + "classifier")
                sc = d.findtext(NS + "scope")
                deps.add((g, a, v, c or "", sc or ""))
            return deps

        want = dep_set(validate_against)
        got = dep_set(out_path)
        missing = want - got
        extra = got - want
        print(f"[validate] baseline={len(want)} generated={len(got)} missing={len(missing)} extra={len(extra)}")
        for m in sorted(missing):
            print("  MISSING:", m)
        for e in sorted(extra):
            print("  EXTRA:", e)
        if not missing and not extra:
            print(f"[validate] PASS — 1.21.1 生成结果与已知正确 POM 完全一致")


if __name__ == "__main__":
    main()
