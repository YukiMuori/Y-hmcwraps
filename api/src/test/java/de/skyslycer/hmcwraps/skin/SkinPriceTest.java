package de.skyslycer.hmcwraps.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkinPriceTest {
    @Test
    void normalizesProviderButPreservesCustomCurrencyId() {
        SkinPrice price = new SkinPrice(" Excellent_Economy ", " coins ", 5000D);
        assertEquals("excellent_economy", price.provider());
        assertEquals("coins", price.currency());
        assertEquals(5000D, price.amount());
    }

    @Test
    void rejectsInvalidAmountsAndMissingProviderData() {
        assertThrows(IllegalArgumentException.class, () -> new SkinPrice("", "coins", 1D));
        assertThrows(IllegalArgumentException.class, () -> new SkinPrice("vault", "", 1D));
        assertThrows(IllegalArgumentException.class, () -> new SkinPrice("vault", "money", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SkinPrice("vault", "money", -1D));
    }
}
