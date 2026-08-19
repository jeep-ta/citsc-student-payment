package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NameNormalizerTest {

    @Test
    void testTrimWhitespace() {
        assertEquals("juan dela cruz", NameNormalizer.normalize("  Juan Dela Cruz  "));
    }

    @Test
    void testCollapseRepeatedWhitespace() {
        assertEquals("juan dela cruz", NameNormalizer.normalize("Juan   Dela   Cruz"));
    }

    @Test
    void testLowercase() {
        assertEquals("juan dela cruz", NameNormalizer.normalize("JUAN DELA CRUZ"));
    }

    @Test
    void testCombined() {
        assertEquals("juan dela cruz", NameNormalizer.normalize("  JUAN   DELA   CRUZ  "));
    }

    @Test
    void testEmptyString() {
        assertEquals("", NameNormalizer.normalize(""));
    }

    @Test
    void testNull() {
        assertNull(NameNormalizer.normalize(null));
    }

    @Test
    void testWhitespaceOnly() {
        assertEquals("", NameNormalizer.normalize("   "));
    }

    @Test
    void testTabsAndNewlines() {
        assertEquals("juan dela cruz", NameNormalizer.normalize("Juan\t\tDela\n\nCruz"));
    }

    @Test
    void testUnicode() {
        // Full-width characters should be normalized
        String fullWidth = "Ｊｕａｎ　Ｄｅｌａ　Ｃｒｕｚ"; // full-width
        String normalized = NameNormalizer.normalize(fullWidth);
        assertEquals("juan dela cruz", normalized);
    }

    @Test
    void testAccents() {
        String withAccent = "Jöhn Döe";
        // NFKC should preserve accents but normalize
        String normalized = NameNormalizer.normalize(withAccent);
        assertEquals("jöhn döe", normalized);
    }

    @Test
    void testMatches() {
        assertTrue(NameNormalizer.matches("Juan Dela Cruz", "  JUAN   DELA   CRUZ  "));
        assertTrue(NameNormalizer.matches("Maria Garcia", "MARIA GARCIA"));
        assertFalse(NameNormalizer.matches("Juan Dela Cruz", "Pedro Lopez"));
        assertFalse(NameNormalizer.matches(null, "Test"));
        assertFalse(NameNormalizer.matches("Test", null));
    }

    @Test
    void testSpecialCharacters() {
        assertEquals("juan dela-cruz", NameNormalizer.normalize("Juan Dela-Cruz"));
        assertEquals("juan o'reilly", NameNormalizer.normalize("Juan O'Reilly"));
        assertEquals("juan.smith", NameNormalizer.normalize("Juan.Smith"));
    }
}