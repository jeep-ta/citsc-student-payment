package com.payment;

/**
 * Normalizes student names for matching purposes.
 *
 * Rules:
 * - Trim leading/trailing whitespace
 * - Collapse internal repeated whitespace to single space
 * - Lowercase for case-insensitive comparison
 * - Unicode NFKC normalization (handles full-width chars, etc.)
 *
 * The original name is always preserved; only normalizedName is used for matching.
 */
public final class NameNormalizer {

    private NameNormalizer() {}

    /**
     * Normalize a student name for comparison/matching.
     *
     * @param name Original name (may be null)
     * @return Normalized name, or null if input is null/empty
     */
    public static String normalize(String name) {
        if (name == null) return null;

        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "";

        // Unicode normalization (handles full-width, compatibility chars)
        String normalized = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);

        // Collapse repeated whitespace to single space
        normalized = normalized.replaceAll("\\s+", " ");

        // Lowercase for case-insensitive matching
        return normalized.toLowerCase();
    }

    /**
     * Check if two names match after normalization.
     *
     * @param name1 First name
     * @param name2 Second name
     * @return true if normalized forms are equal
     */
    public static boolean matches(String name1, String name2) {
        String n1 = normalize(name1);
        String n2 = normalize(name2);
        if (n1 == null || n2 == null) return false;
        return n1.equals(n2);
    }
}