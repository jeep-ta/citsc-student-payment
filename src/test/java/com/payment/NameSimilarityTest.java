package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NameSimilarityTest {

    @Test
    void testExactMatches() {
        var res1 = NameSimilarity.compare("Juan Dela Cruz", "Juan Dela Cruz");
        assertEquals(1.0, res1.score());
        assertTrue(res1.isExactNormalized());

        var res2 = NameSimilarity.compare("  JUAN DELA CRUZ  ", "juan dela cruz");
        assertEquals(1.0, res2.score());
        assertTrue(res2.isExactNormalized());
    }

    @Test
    void testTokenPermutation() {
        // "Dela Cruz, Juan" vs "Juan Dela Cruz"
        var res = NameSimilarity.compare("Dela Cruz, Juan", "Juan Dela Cruz");
        assertTrue(res.score() >= 0.95, "Permuted name should score >= 0.95");
        assertTrue(res.isTokenPermutation());
        assertFalse(res.isExactNormalized());
    }

    @Test
    void testMiddleInitialAbbreviation() {
        // "Abadilla, Naomi K." vs "Abadilla, Naomi Kaye"
        var res = NameSimilarity.compare("Abadilla, Naomi K.", "Abadilla, Naomi Kaye");
        assertTrue(res.score() >= 0.90, "Middle initial should score >= 0.90");
        assertTrue(res.isInitialMatch());
    }

    @Test
    void testTypoLevenshtein() {
        // 1 letter typo: "Canoy, Cean" vs "Canoy, Sean"
        var res = NameSimilarity.compare("Canoy, Cean", "Canoy, Sean");
        assertTrue(res.score() >= 0.85, "1-character typo should score >= 0.85");
        assertTrue(res.reasons().stream().anyMatch(r -> r.contains("1-character") || r.contains("typo")));
    }

    @Test
    void testDoubleLetterTypo() {
        // "Villanueva, Maria" vs "Vilanueva, Maria"
        var res = NameSimilarity.compare("Villanueva, Maria", "Vilanueva, Maria");
        assertTrue(res.score() >= 0.90, "Single omitted duplicate letter should score >= 0.90");
    }

    @Test
    void testDistinctNames() {
        // "Santos, Maria" vs "Reyes, Pedro"
        var res = NameSimilarity.compare("Santos, Maria", "Reyes, Pedro");
        assertTrue(res.score() < 0.60, "Completely different names should score < 0.60");
    }

    @Test
    void testSuffixHandling() {
        // "Cruz, Juan Jr." vs "Cruz, Juan"
        var res = NameSimilarity.compare("Cruz, Juan Jr.", "Cruz, Juan");
        assertTrue(res.score() >= 0.95, "Suffix differences should score >= 0.95");
    }
}
