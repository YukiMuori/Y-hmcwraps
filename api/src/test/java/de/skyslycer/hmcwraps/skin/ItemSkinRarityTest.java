package de.skyslycer.hmcwraps.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemSkinRarityTest {
    @Test
    void normalizesRarityIdAndRetainsConfiguredPriorityAndTranslationKey() {
        ItemSkinRarity rarity = new ItemSkinRarity(" LEGENDARY ", 50, "rarities.legendary");
        assertEquals("legendary", rarity.id());
        assertEquals(50, rarity.priority());
        assertEquals("rarities.legendary", rarity.displayNameKey());
    }

    @Test
    void rejectsBlankRarityIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new ItemSkinRarity("  ", 0, "rarities.common"));
    }
}
