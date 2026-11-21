package com.blackannin.statistics.session;

import org.junit.jupiter.api.Test;

import com.blackannin.statistics.stat.LocationKind;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayLocationTest {

    @Test
    void sanitizesWindowsIllegalChars() {
        assertEquals("a_b_c", PlayLocation.sanitizeId("a:b?c"));
        assertEquals("新建存档", PlayLocation.sanitizeId("新建存档"));
        assertEquals("trailing", PlayLocation.sanitizeId("trailing... "));
        assertEquals("unknown", PlayLocation.sanitizeId("   "));
        assertEquals("unknown", PlayLocation.sanitizeId(null));
    }

    @Test
    void longIdsTruncated() {
        String longName = "x".repeat(200);
        assertEquals(64, PlayLocation.sanitizeId(longName).length());
    }

    @Test
    void multiplayerUsesAddressAsId() {
        PlayLocation loc = PlayLocation.multiplayer("mc.hypixel.net", "Hypixel");
        assertEquals(LocationKind.MULTIPLAYER, loc.kind());
        assertEquals("mc.hypixel.net", loc.id());
        assertEquals("Hypixel", loc.displayName());
        assertEquals("multiplayer:mc.hypixel.net", loc.locationKey());
    }
}
