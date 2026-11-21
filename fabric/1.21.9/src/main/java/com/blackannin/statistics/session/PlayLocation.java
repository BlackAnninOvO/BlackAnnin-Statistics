package com.blackannin.statistics.session;

import com.blackannin.statistics.stat.LocationKind;

/**
 * 一个"游玩位置"：单人的一个存档，或多人的一个服务器。
 * id 用于存储目录名（已做文件系统安全化），displayName/address 用于展示。
 */
public record PlayLocation(LocationKind kind, String id, String displayName, String address) {

    /** 生成文件系统安全的目录名：去非法字符、限长、保底回退。 */
    public static String sanitizeId(String raw) {
        String s = raw == null ? "" : raw.trim();
        s = s.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
        s = s.replaceAll("[\\s.]+$", "");           // Windows 目录不能以空格/点结尾
        s = s.replaceAll("^\\s+", "");
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        if (s.isEmpty()) {
            s = "unknown";
        }
        return s;
    }

    public static PlayLocation singleplayer(String worldFolderName, String worldTitle) {
        return new PlayLocation(LocationKind.SINGLEPLAYER, sanitizeId(worldFolderName),
                worldTitle == null || worldTitle.isBlank() ? worldFolderName : worldTitle, null);
    }

    public static PlayLocation multiplayer(String serverIp, String serverLabel) {
        String id = sanitizeId(serverIp == null || serverIp.isBlank() ? "unknown_server" : serverIp);
        String display = serverLabel == null || serverLabel.isBlank() ? serverIp : serverLabel;
        return new PlayLocation(LocationKind.MULTIPLAYER, id, display, serverIp);
    }

    public String locationKey() {
        return kind.dirName() + ":" + id;
    }
}
