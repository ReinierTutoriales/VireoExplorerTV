package com.reiniertutoriales.vireoexplorertv
import org.junit.Test
import org.junit.Assert.*
class SmbNameTest {
    @Test fun names() {
        assertTrue(validSmbName("Movie 01.mkv"))
        for (name in listOf("", "..", "a/b", "a\\b", "a:b", "a?", "a.", "a ")) assertFalse(name, validSmbName(name))
    }
    @Test fun formatsUriAuthorityForIpv4HostnameAndIpv6() {
        assertEquals("192.168.1.50", formatSmbUriAuthority("192.168.1.50"))
        assertEquals("nas.local", formatSmbUriAuthority("nas.local"))
        assertEquals("[fe80::1]", formatSmbUriAuthority("fe80::1%wlan0"))
        assertEquals("[fd00::abcd]", formatSmbUriAuthority("[fd00::abcd]"))
    }
}
