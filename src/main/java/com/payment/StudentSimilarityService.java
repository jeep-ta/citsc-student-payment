package com.payment;

import com.payment.database.DatabaseManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting students with similar names across the database.
 */
public class StudentSimilarityService {

    public static final double DEFAULT_THRESHOLD = 0.80;
    public static final double HIGH_THRESHOLD = 0.88;
    public static final double BROAD_THRESHOLD = 0.70;

    private final DatabaseManager db;

    public StudentSimilarityService() {
        this.db = DatabaseManager.getInstance();
    }

    public StudentSimilarityService(DatabaseManager db) {
        this.db = db;
    }

    private Set<String> getDismissedKeys() {
        return db != null ? db.getDismissedSimilarityPairKeys() : Collections.emptySet();
    }

    /**
     * Detect pairs of students with similar names.
     *
     * @param students List of all active students
     * @param minThreshold Minimum similarity threshold (e.g. 0.80)
     * @param sameProgramOnly If true, only pairs with the exact same program are returned
     * @param includeDismissed If true, previously dismissed pairs are included
     * @return List of candidates sorted by similarity score descending
     */
    public List<SimilarStudentCandidate> findSimilarStudents(
            List<Student> students,
            double minThreshold,
            boolean sameProgramOnly,
            boolean includeDismissed) {

        if (students == null || students.size() < 2) {
            return Collections.emptyList();
        }

        Set<String> dismissedKeys = (includeDismissed || db == null)
            ? Collections.emptySet()
            : getDismissedKeys();

        List<SimilarStudentCandidate> candidates = new ArrayList<>();

        int n = students.size();
        for (int i = 0; i < n; i++) {
            Student s1 = students.get(i);
            String name1 = s1.getName();
            if (name1 == null || name1.trim().isEmpty()) continue;

            for (int j = i + 1; j < n; j++) {
                Student s2 = students.get(j);
                String name2 = s2.getName();
                if (name2 == null || name2.trim().isEmpty()) continue;

                if (s1.getStudentCode() != null && s1.getStudentCode().equalsIgnoreCase(s2.getStudentCode())) {
                    continue;
                }

                String pairKey = SimilarStudentCandidate.makePairKey(s1.getStudentCode(), s2.getStudentCode());
                if (!includeDismissed && dismissedKeys.contains(pairKey)) {
                    continue;
                }

                String prog1 = s1.getProgram() != null ? s1.getProgram().trim() : "";
                String prog2 = s2.getProgram() != null ? s2.getProgram().trim() : "";
                boolean sameProgram = !prog1.isEmpty() && !prog2.isEmpty() && prog1.equalsIgnoreCase(prog2);

                if (sameProgramOnly && !sameProgram) {
                    continue;
                }

                // Fast length filter before full compare
                if (Math.abs(name1.length() - name2.length()) > 8 && !sameProgram) {
                    continue;
                }

                NameSimilarity.SimilarityResult result = NameSimilarity.compare(name1, name2);
                double finalScore = result.score();
                List<String> reasons = new ArrayList<>(result.reasons());

                // Contextual boost: same program adds confidence
                if (sameProgram && finalScore >= 0.70 && finalScore < 0.98) {
                    finalScore = Math.min(0.99, finalScore + 0.04);
                    reasons.add("Same program (" + s1.getFormattedProgramName() + ")");
                } else if (!prog1.isEmpty() && !prog2.isEmpty() && !sameProgram && finalScore >= 0.85) {
                    reasons.add("Different programs: " + prog1 + " vs " + prog2);
                }

                if (finalScore >= minThreshold) {
                    candidates.add(new SimilarStudentCandidate(s1, s2, finalScore, reasons));
                }
            }
        }

        // Sort by similarity descending
        candidates.sort((c1, c2) -> Double.compare(c2.getSimilarityScore(), c1.getSimilarityScore()));
        return candidates;
    }

    /**
     * Find candidates similar to a specific student with fast pre-filtering.
     */
    public List<SimilarStudentCandidate> findSimilarForStudent(
            Student target,
            List<Student> allStudents,
            double minThreshold) {

        if (target == null || allStudents == null || target.getName() == null || target.getName().isBlank()) {
            return Collections.emptyList();
        }

        Set<String> dismissedKeys = getDismissedKeys();
        List<SimilarStudentCandidate> candidates = new ArrayList<>();
        String targetClean = NameSimilarity.cleanName(target.getName());
        int targetLen = targetClean.length();
        if (targetLen == 0) return Collections.emptyList();
        List<String> targetTokens = NameSimilarity.extractTokens(target.getName());
        String targetProg = target.getProgram() != null ? target.getProgram().trim() : "";

        for (Student s : allStudents) {
            if (s.getStudentCode() != null && s.getStudentCode().equalsIgnoreCase(target.getStudentCode())) {
                continue;
            }
            String sName = s.getName();
            if (sName == null || sName.isBlank()) continue;

            String pairKey = SimilarStudentCandidate.makePairKey(target.getStudentCode(), s.getStudentCode());
            if (dismissedKeys.contains(pairKey)) {
                continue;
            }

            // Quick pre-filter: length difference check
            int sLen = sName.length();
            if (Math.abs(targetLen - sLen) > 8) {
                // If length difference is large, only compare if they share at least one word token of length >= 3
                boolean sharesToken = false;
                for (String tToken : targetTokens) {
                    if (tToken.length() >= 3 && sName.toLowerCase().contains(tToken)) {
                        sharesToken = true;
                        break;
                    }
                }
                if (!sharesToken) continue;
            }

            NameSimilarity.SimilarityResult result = NameSimilarity.compare(target.getName(), sName);
            double finalScore = result.score();
            List<String> reasons = new ArrayList<>(result.reasons());

            String prog2 = s.getProgram() != null ? s.getProgram().trim() : "";
            boolean sameProgram = !targetProg.isEmpty() && !prog2.isEmpty() && targetProg.equalsIgnoreCase(prog2);

            if (sameProgram && finalScore >= 0.70 && finalScore < 0.98) {
                finalScore = Math.min(0.99, finalScore + 0.04);
                reasons.add("Same program (" + target.getFormattedProgramName() + ")");
            }

            if (finalScore >= minThreshold) {
                candidates.add(new SimilarStudentCandidate(target, s, finalScore, reasons));
            }
        }

        candidates.sort((c1, c2) -> Double.compare(c2.getSimilarityScore(), c1.getSimilarityScore()));
        return candidates;
    }
}
