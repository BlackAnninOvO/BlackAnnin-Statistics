package com.blackannin.statistics.stat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单个数据文件（位置 × 档案）的元信息。全部为可序列化的纯数据。
 */
public final class StoreMeta {
    /** 文件格式版本，未来结构变更时用于迁移。 */
    public int format = 1;

    public String kind;          // LocationKind.dirName
    public String locationId;    // 存档文件夹名 / 服务器 host_port（目录安全化后）
    public String displayName;   // 展示名：存档名或服务器标签名
    public String address;       // 多人服务器地址（单人为 null）

    public String profileId;     // 玩家 UUID
    public final Set<String> profileNames = new LinkedHashSet<>(); // 曾用名（识别改名）

    public long firstSeen;       // epoch millis
    public long lastSeen;        // epoch millis
    public int sessions;         // 进入次数

    /** 检测到来源（服务器）统计被重置：本地保留的历史将大于来源累计值。 */
    public boolean serverStatReset;

    public void addName(String name) {
        if (name != null && !name.isBlank()) {
            profileNames.add(name);
        }
    }

    public List<String> namesList() {
        return new ArrayList<>(profileNames);
    }

    public String primaryName() {
        return profileNames.isEmpty() ? profileId : profileNames.iterator().next();
    }
}
