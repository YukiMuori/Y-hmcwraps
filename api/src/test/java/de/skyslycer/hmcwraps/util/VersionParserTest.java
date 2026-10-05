package de.skyslycer.hmcwraps.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VersionParserTest {

    @Test
    void parsesLegacyVersionStrings() {
        assertArrayEquals(new int[]{1, 21, 4}, VersionParser.parse("1.21.4-R0.1-SNAPSHOT"));
        assertArrayEquals(new int[]{1, 20, 4}, VersionParser.parse("1.20.4-pre1"));
        assertArrayEquals(new int[]{1, 21, 0}, VersionParser.parse("1.21"));
    }

    @Test
    void parsesNewSchemeVersionStrings() {
        assertArrayEquals(new int[]{26, 1, 2}, VersionParser.parse("26.1.2"));
        assertArrayEquals(new int[]{26, 2, 0}, VersionParser.parse("26.2"));
    }

    @Test
    void ignoresNonNumericComponents() {
        assertArrayEquals(new int[]{26, 2, 0}, VersionParser.parse("26.2.build.123-alpha"));
        assertArrayEquals(new int[]{26, 2, 0}, VersionParser.parse("26.2.build-alpha"));
    }

    @Test
    void fallsBackToZeroForUnparseableInput() {
        assertArrayEquals(new int[]{26, 0, 0}, VersionParser.parse("26"));
        assertArrayEquals(new int[]{1, 21, 4}, VersionParser.parse("v1.21.4"));
        assertArrayEquals(new int[]{0, 0, 0}, VersionParser.parse("unknown"));
        assertArrayEquals(new int[]{0, 0, 0}, VersionParser.parse(""));
        assertArrayEquals(new int[]{0, 0, 0}, VersionParser.parse(null));
    }

}
