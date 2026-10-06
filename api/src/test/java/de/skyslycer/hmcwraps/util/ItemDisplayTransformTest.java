package de.skyslycer.hmcwraps.util;

import de.skyslycer.hmcwraps.preview.floating.PreviewOrientation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDisplayTransformTest {

    @Test
    void swordsUseTheVerticalFloatingPreviewTransform() {
        assertTrue(PreviewOrientation.isVerticalItemDisplay("DIAMOND_SWORD"));
        assertTrue(PreviewOrientation.isVerticalItemDisplay("NETHERITE_SWORD"));
        assertFalse(PreviewOrientation.isVerticalItemDisplay("DIAMOND_PICKAXE"));
        assertFalse(PreviewOrientation.isVerticalItemDisplay("PAPER"));
    }
}
