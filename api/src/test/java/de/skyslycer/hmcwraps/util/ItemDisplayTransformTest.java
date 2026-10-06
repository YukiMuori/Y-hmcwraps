package de.skyslycer.hmcwraps.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDisplayTransformTest {

    @Test
    void swordsUseTheVerticalFloatingPreviewTransform() {
        assertTrue(VersionUtil.usesVerticalItemDisplayTransform("DIAMOND_SWORD"));
        assertTrue(VersionUtil.usesVerticalItemDisplayTransform("NETHERITE_SWORD"));
        assertFalse(VersionUtil.usesVerticalItemDisplayTransform("DIAMOND_PICKAXE"));
        assertFalse(VersionUtil.usesVerticalItemDisplayTransform("PAPER"));
    }
}
