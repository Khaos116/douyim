package com.zz.douyin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ModuleVersionTest {
    @Test
    public void versionTagJoinsParts() {
        assertEquals(
                "v1.5.1+17 20260923_234853",
                ModuleVersion.versionTag("1.5.1", 17, "20260923_234853"));
    }

    @Test
    public void versionTagToleratesBlanks() {
        assertEquals("v?+0 ?", ModuleVersion.versionTag(null, 0, null));
        assertEquals("v?+0 ?", ModuleVersion.versionTag("", 0, ""));
    }
}
