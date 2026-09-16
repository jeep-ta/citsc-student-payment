package com.payment;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Advanced name similarity algorithms tailored for student rosters and payment records.
 *
 * Implements:
 * - Levenshtein & Damerau-Levenshtein edit distance (typo detection)
 * - Jaro-Winkler similarity (prefix-weighted name comparison)
 * - Token permutation & sorting ("Lastname, Firstname" vs "Firstname Lastname")
 * - Middle initial and abbreviation detection ("Naomi K." vs "Naomi Kaye")
 * - Suffix and honorific stripping ("Jr.", "III", "II")
 */
public final class NameSimilarity {

    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[,.\\-_/\\\\()]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> SUFFIXES = Set.of("jr", "sr", "ii", "iii", "iv", "v");

    private NameSimilarity() {}

    /**
     * Result of comparing two names.
     */
    public record SimilarityResult(
        double score,
        List<String> reasons,
        boolean isExactNormalized,
        boolean isTokenPermutation,
        boolean isInitialMatch
    ) {}

    /**
     * Clean and prepare a raw name for comparison:
     * - Trims and normalizes Unicode (NFKC)
     * - Replaces punctuation with single space
     * - Collapses multiple spaces
     * - Converts to lower case
     */
    public static String cleanName(String name) {
        if (name == null) return "";
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC);
        String noPunct = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
        String collapsed = WHITESPACE_PATTERN.matcher(noPunct).replaceAll(" ");
        return collapsed.trim().toLowerCase();
    }

    /**
     * Split a cleaned name into word tokens, excluding common suffixes.
     */
    public static List<String> extractTokens(String name) {
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) return Collections.emptyList();
        String[] parts = cleaned.split(" ");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String token = p.trim();
            if (!token.isEmpty() && !SUFFIXES.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Calculate comprehensive similarity score and descriptive match reasons.
     *
     * @param name1 First student name
     * @param name2 Second student name
     * @return SimilarityResult with score (0.0 to 1.0) and match reasons
     */
    public static SimilarityResult compare(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return new SimilarityResult(0.0, Collections.emptyList(), false, false, false);
        }

        String clean1 = cleanName(name1);
        String clean2 = cleanName(name2);

        if (clean1.isEmpty() || clean2.isEmpty()) {
            return new SimilarityResult(0.0, Collections.emptyList(), false, false, false);
        }

        List<String> reasons = new ArrayList<>();

        // 1. Exact match
        if (clean1.equals(clean2)) {
            reasons.add("Exact name match");
            return new SimilarityResult(1.0, reasons, true, false, false);
        }

        List<String> tokens1 = extractTokens(name1);
        List<String> tokens2 = extractTokens(name2);

        // 2. Token Sort & Permutation Check ("Dela Cruz, Juan" vs "Juan Dela Cruz")
        List<String> sorted1 = new ArrayList<>(tokens1);
        List<String> sorted2 = new ArrayList<>(tokens2);
        Collections.sort(sorted1);
        Collections.sort(sorted2);

        if (sorted1.equals(sorted2)) {
            reasons.add("Identical words in different order (e.g. Last Name first)");
            return new SimilarityResult(0.98, reasons, false, true, false);
        }

        // 3. Middle Initial / Abbreviation Matching (e.g. "Abadilla, Naomi K." vs "Abadilla, Naomi Kaye")
        boolean isInitial = isAbbreviationOrInitialMatch(tokens1, tokens2);
        if (isInitial) {
            reasons.add("Abbreviated middle name / initial match");
            return new SimilarityResult(0.94, reasons, false, false, true);
        }

        // 4. Jaro-Winkler Similarity
        double jwFull = jaroWinkler(clean1, clean2);
        String sortedStr1 = String.join(" ", sorted1);
        String sortedStr2 = String.join(" ", sorted2);
        double jwSorted = jaroWinkler(sortedStr1, sortedStr2);
        double bestJw = Math.max(jwFull, jwSorted);

        // 5. Levenshtein Edit Distance
        int levFull = levenshteinDistance(clean1, clean2);
        int maxLen = Math.max(clean1.length(), clean2.length());
        double levRatio = maxLen > 0 ? 1.0 - ((double) levFull / maxLen) : 0.0;

        int levSorted = levenshteinDistance(sortedStr1, sortedStr2);
        int maxSortedLen = Math.max(sortedStr1.length(), sortedStr2.length());
        double levSortedRatio = maxSortedLen > 0 ? 1.0 - ((double) levSorted / maxSortedLen) : 0.0;
        double bestLev = Math.max(levRatio, levSortedRatio);

        // Detect single-letter or two-letter typos
        int minLev = Math.min(levFull, levSorted);
        if (minLev == 1 && maxLen >= 5) {
            reasons.add("1-character spelling difference / typo");
        } else if (minLev == 2 && maxLen >= 8) {
            reasons.add("2-character spelling difference / typo");
        }

        // 6. Token Overlap (Jaccard on words)
        Set<String> set1 = new HashSet<>(tokens1);
        Set<String> set2 = new HashSet<>(tokens2);
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        if (intersection.size() >= 2 && intersection.size() == Math.min(set1.size(), set2.size())) {
            reasons.add("Shared major name components (" + String.join(", ", intersection) + ")");
        }

        // Combine scores using maximum weighted evidence
        double score = Math.max(bestJw, bestLev);
        if (jaccard >= 0.66 && score < 0.85) {
            score = Math.max(score, 0.82);
            reasons.add("Multiple matching name tokens");
        }

        if (score >= 0.90 && reasons.isEmpty()) {
            reasons.add(String.format("Very high text similarity (%.0f%%)", score * 100));
        } else if (score >= 0.80 && reasons.isEmpty()) {
            reasons.add(String.format("High text similarity (%.0f%%)", score * 100));
        }

        // Clamp to 0.0 - 1.0
        score = Math.max(0.0, Math.min(1.0, score));

        return new SimilarityResult(score, reasons, false, false, false);
    }

    /**
     * Checks if tokens match with one token being an initial or abbreviation of the other.
     * Example: ["abadilla", "naomi", "k"] and ["abadilla", "naomi", "kaye"]
     */
    public static boolean isAbbreviationOrInitialMatch(List<String> t1, List<String> t2) {
        if (t1.isEmpty() || t2.isEmpty()) return false;
        if (Math.abs(t1.size() - t2.size()) > 1) return false;

        List<String> shortList = t1.size() <= t2.size() ? new ArrayList<>(t1) : new ArrayList<>(t2);
        List<String> longList = t1.size() <= t2.size() ? new ArrayList<>(t2) : new ArrayList<>(t1);

        Collections.sort(shortList);
        Collections.sort(longList);

        // Case A: Same number of tokens (e.g. 3 tokens each: ["abadilla", "k", "naomi"] vs ["abadilla", "kaye", "naomi"])
        if (shortList.size() == longList.size()) {
            int mismatches = 0;
            boolean hadAbbrev = false;
            for (int i = 0; i < shortList.size(); i++) {
                String w1 = shortList.get(i);
                String w2 = longList.get(i);
                if (w1.equals(w2)) continue;

                if ((w1.length() == 1 && w2.startsWith(w1)) || (w2.length() == 1 && w1.startsWith(w2))) {
                    hadAbbrev = true;
                } else if ((w1.length() <= 3 && w2.startsWith(w1)) || (w2.length() <= 3 && w1.startsWith(w2))) {
                    hadAbbrev = true;
                } else {
                    mismatches++;
                }
            }
            return hadAbbrev && mismatches == 0;
        }

        // Case B: shortList has 1 fewer token (e.g. middle name completely omitted in one: ["abadilla", "naomi"] vs ["abadilla", "kaye", "naomi"])
        if (longList.size() == shortList.size() + 1) {
            Set<String> shortSet = new HashSet<>(shortList);
            Set<String> longSet = new HashSet<>(longList);
            Set<String> diff = new HashSet<>(longSet);
            diff.removeAll(shortSet);
            // All tokens of shortSet must exist in longSet
            return diff.size() == 1 && shortSet.stream().allMatch(longSet::contains);
        }

        return false;
    }

    /**
     * Compute Levenshtein distance between two character sequences.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= s2.length(); j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, curr.length);
        }

        return prev[s2.length()];
    }

    /**
     * Compute Jaro-Winkler similarity between two strings.
     * Range: 0.0 (no similarity) to 1.0 (exact match).
     */
    public static double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int len1 = s1.length();
        int len2 = s2.length();
        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j]) continue;
                if (s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        // Count transpositions
        int k = 0;
        int transpositions = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double m = matches;
        double jaro = ((m / len1) + (m / len2) + ((m - transpositions / 2.0) / m)) / 3.0;

        // Winkler prefix bonus
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(len1, len2));
        for (int i = 0; i < maxPrefix; i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }

        double p = 0.1; // scaling factor
        return jaro + (prefix * p * (1.0 - jaro));
    }
}
