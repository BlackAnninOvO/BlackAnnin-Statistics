package com.blackannin.statistics.stat;

/** 位置类别：单人游戏（存档） / 多人游戏（服务器）。 */
public enum LocationKind {
    SINGLEPLAYER("singleplayer"),
    MULTIPLAYER("multiplayer");

    private final String dirName;

    LocationKind(String dirName) {
        this.dirName = dirName;
    }

    /** 存储目录名，同时是 JSON meta.kind 字段值。 */
    public String dirName() {
        return dirName;
    }

    public static LocationKind fromDirName(String dirName) {
        for (LocationKind kind : values()) {
            if (kind.dirName.equals(dirName)) {
                return kind;
            }
        }
        return null;
    }
}
