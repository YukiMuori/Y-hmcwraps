package de.skyslycer.hmcwraps.util;

import de.skyslycer.hmcwraps.preview.floating.PreviewOrientation;
import de.skyslycer.hmcwraps.serialization.preview.ItemDisplayTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDisplayTransformTest {

    @Test
    void swordsUseTheVerticalFloatingPreviewTransform() {
        assertTrue(PreviewOrientation.isVerticalItemDisplay("DIAMOND_SWORD"));
        assertTrue(PreviewOrientation.isVerticalItemDisplay("NETHERITE_SWORD"));
        assertFalse(PreviewOrientation.isVerticalItemDisplay("DIAMOND_PICKAXE"));
        assertFalse(PreviewOrientation.isVerticalItemDisplay("PAPER"));
        assertEquals(-Math.PI / 2D, PreviewOrientation.verticalRotationRadians(), 0.0001D);
    }

    @Test
    void defaultTransformPreservesUprightSwordAndNeutralItems() {
        ItemDisplayTransform transform = new ItemDisplayTransform();

        assertEquals(0.0, transform.getTranslation().getX());
        assertEquals(2.0, transform.getTranslation().getY());
        assertEquals(0.0, transform.getTranslation().getZ());
        assertTrue(transform.getItemRotation().isZero());
        assertEquals(90.0, transform.getSwordRotation().getX());
        assertEquals(0.0, transform.getSwordRotation().getY());
        assertEquals(-90.0, transform.getSwordRotation().getZ());
    }
}
