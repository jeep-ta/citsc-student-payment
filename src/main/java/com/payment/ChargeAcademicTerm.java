package com.payment;

/**
 * Represents the academic term to which a payment or charge belongs.
 *
 * This is distinct from the payment/remittance date (when the money was actually received).
 * Students may pay a previous-term charge during the current term.
 *
 * - 1ST_SEM:    1st Semester
 * - 2ND_SEM:    2nd Semester
 * - SUMMER:     Summer / Midyear
 * - CURRENT:    Current Term
 * - PREVIOUS:   Previous Term
 * - UNASSIGNED: Unassigned
 */
public enum ChargeAcademicTerm {

    FIRST_SEM("1ST_SEM", "1st Semester"),
    SECOND_SEM("2ND_SEM", "2nd Semester"),
    SUMMER("SUMMER", "Summer"),
    CURRENT("CURRENT", "Current Term"),
    PREVIOUS("PREVIOUS", "Previous Term"),
    UNASSIGNED("UNASSIGNED", "Unassigned");

    public static final String DB_1ST_SEM = "1ST_SEM";
    public static final String DB_2ND_SEM = "2ND_SEM";
    public static final String DB_SUMMER = "SUMMER";
    public static final String DB_CURRENT = "CURRENT";
    public static final String DB_PREVIOUS = "PREVIOUS";
    public static final String DB_UNASSIGNED = "UNASSIGNED";

    private final String code;
    private final String label;

    ChargeAcademicTerm(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Convert a stored code (or free-form string) to a ChargeAcademicTerm.
     * Returns UNASSIGNED for null/unknown/empty values.
     */
    public static ChargeAcademicTerm fromCode(String code) {
        if (code == null || code.trim().isEmpty()) return UNASSIGNED;
        String normalized = code.trim().toUpperCase();
        if (normalized.equals(DB_1ST_SEM) || normalized.equals("1ST SEMESTER") || normalized.equals("1ST SEM") || normalized.equals("FIRST SEMESTER") || normalized.equals("FIRST_SEM")) {
            return FIRST_SEM;
        } else if (normalized.equals(DB_2ND_SEM) || normalized.equals("2ND SEMESTER") || normalized.equals("2ND SEM") || normalized.equals("SECOND SEMESTER") || normalized.equals("SECOND_SEM")) {
            return SECOND_SEM;
        } else if (normalized.equals(DB_SUMMER) || normalized.equals("SUMMER / MIDYEAR") || normalized.equals("MIDYEAR")) {
            return SUMMER;
        } else if (normalized.equals(DB_CURRENT) || normalized.equals("CURRENT TERM") || normalized.startsWith("CURR")) {
            return CURRENT;
        } else if (normalized.equals(DB_PREVIOUS) || normalized.equals("PREVIOUS TERM") || normalized.startsWith("PREV")) {
            return PREVIOUS;
        }
        return UNASSIGNED;
    }

    /**
     * Determine whether this term applies to a legacy/charged category or payment.
     */
    public static boolean isTermEligibleCategory(Double citNight, Double penalties) {
        return true; // Any payment record can now be assigned its own term
    }

    @Override
    public String toString() {
        return label;
    }
}
