package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinCosmeticWrapTest {
    @Test
    void blankConfiguredUuidCanBeReplacedWithGeneratedSkinUuid() {
        Wrap wrap = new Wrap();
        wrap.setUuid(" ");
        wrap.setUuid("yhm-skin:ruby_sword");

        assertEquals("yhm-skin:ruby_sword", wrap.getUuid());
    }

    @Test
    void nonBlankLegacyUuidRemainsStable() {
        Wrap wrap = new Wrap();
        wrap.setUuid("legacy-wrap");
        wrap.setUuid("replacement");

        assertEquals("legacy-wrap", wrap.getUuid());
    }
}
