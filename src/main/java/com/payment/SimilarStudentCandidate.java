package com.payment;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a detected pair of students with similar names.
 */
public class SimilarStudentCandidate {

    public enum Confidence {
        HIGH("High", ThemeConstant.NEON_GREEN),
        MEDIUM("Medium", ThemeConstant.NEON_AMBER),
        LOW("Low", ThemeConstant.TEXT_SECONDARY);

        private final String label;
        private final String colorHex;

        Confidence(String label, String colorHex) {
            this.label = label;
            this.colorHex = colorHex;
        }

        public String getLabel() { return label; }
        public String getColorHex() { return colorHex; }
    }

    private final Student studentA;
    private final Student studentB;
    private final double similarityScore;
    private final List<String> matchReasons;
    private final boolean sameProgram;
    private final boolean exactNormalized;
    private final Confidence confidence;

    public SimilarStudentCandidate(Student studentA, Student studentB, double similarityScore, List<String> matchReasons) {
        this.studentA = studentA;
        this.studentB = studentB;
        this.similarityScore = similarityScore;
        this.matchReasons = matchReasons != null ? new ArrayList<>(matchReasons) : new ArrayList<>();

        String progA = studentA.getProgram() != null ? studentA.getProgram().trim().toLowerCase() : "";
        String progB = studentB.getProgram() != null ? studentB.getProgram().trim().toLowerCase() : "";
        this.sameProgram = !progA.isEmpty() && !progB.isEmpty() && progA.equals(progB);

        String normA = studentA.getNormalizedName() != null ? studentA.getNormalizedName() : "";
        String normB = studentB.getNormalizedName() != null ? studentB.getNormalizedName() : "";
        this.exactNormalized = !normA.isEmpty() && normA.equals(normB);

        if (similarityScore >= 0.88 || exactNormalized) {
            this.confidence = Confidence.HIGH;
        } else if (similarityScore >= 0.75) {
            this.confidence = Confidence.MEDIUM;
        } else {
            this.confidence = Confidence.LOW;
        }
    }

    public Student getStudentA() { return studentA; }
    public Student getStudentB() { return studentB; }
    public double getSimilarityScore() { return similarityScore; }
    public int getSimilarityPercentage() { return (int) Math.round(similarityScore * 100); }
    public List<String> getMatchReasons() { return matchReasons; }
    public boolean isSameProgram() { return sameProgram; }
    public boolean isExactNormalized() { return exactNormalized; }
    public Confidence getConfidence() { return confidence; }

    /**
     * Unique stable key for this pair regardless of order (e.g. "STU-000001:STU-000002").
     */
    public String getPairKey() {
        return makePairKey(studentA.getStudentCode(), studentB.getStudentCode());
    }

    public static String makePairKey(String code1, String code2) {
        if (code1 == null) code1 = "";
        if (code2 == null) code2 = "";
        return code1.compareTo(code2) <= 0 ? (code1 + ":" + code2) : (code2 + ":" + code1);
    }

    /**
     * Determine recommended primary student (target of merge)
     * Heuristics:
     * 1. Student with more payment records.
     * 2. If equal, student with longer/more detailed name.
     * 3. If equal, student created earlier.
     */
    public Student getRecommendedTarget() {
        int paymentsA = studentA.getPaymentCount();
        int paymentsB = studentB.getPaymentCount();

        if (paymentsA > paymentsB) return studentA;
        if (paymentsB > paymentsA) return studentB;

        int lenA = studentA.getName() != null ? studentA.getName().length() : 0;
        int lenB = studentB.getName() != null ? studentB.getName().length() : 0;
        if (lenA > lenB) return studentA;
        if (lenB > lenA) return studentB;

        if (studentA.getCreatedAt() != null && studentB.getCreatedAt() != null) {
            return studentA.getCreatedAt().isBefore(studentB.getCreatedAt()) ? studentA : studentB;
        }

        return studentB;
    }

    /**
     * Determine recommended source student (record to be merged into primary)
     */
    public Student getRecommendedSource() {
        return getRecommendedTarget() == studentA ? studentB : studentA;
    }

    /**
     * Summary text for tooltips and tables.
     */
    public String getReasonSummary() {
        if (matchReasons.isEmpty()) {
            return String.format("%d%% match", getSimilarityPercentage());
        }
        return String.join(" • ", matchReasons);
    }

    private static class ThemeConstant {
        static final String NEON_GREEN = "#00FF9D";
        static final String NEON_AMBER = "#F59E0B";
        static final String TEXT_SECONDARY = "#94A3B8";
    }
}
