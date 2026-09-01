package com.payment;

/**
 * Identifies a receipt within the academic period in which the receipt number
 * was issued. Receipt scope is deliberately separate from fee attribution:
 * one receipt may contain charges assigned to other academic terms.
 */
public record ReceiptKey(int receiptNumber, String academicYear, ChargeAcademicTerm term) {

    public ReceiptKey {
        academicYear = academicYear != null ? academicYear.trim() : null;
        if (academicYear != null && academicYear.isBlank()) {
            academicYear = null;
        }
        term = term != null ? term : ChargeAcademicTerm.UNASSIGNED;
    }

    public static ReceiptKey from(Payment payment) {
        return new ReceiptKey(
            payment.getReceiptNumber(),
            payment.getReceiptAcademicYear(),
            payment.getReceiptTerm()
        );
    }

    public boolean hasDefinedScope() {
        return receiptNumber > 0
            && academicYear != null
            && !academicYear.isBlank()
            && isConcreteTerm(term);
    }

    public String displayScope() {
        String year = academicYear != null && !academicYear.isBlank() ? academicYear : "Unassigned AY";
        return year + " / " + term.getLabel();
    }

    public static boolean isConcreteTerm(ChargeAcademicTerm value) {
        return value == ChargeAcademicTerm.FIRST_SEM
            || value == ChargeAcademicTerm.SECOND_SEM
            || value == ChargeAcademicTerm.SUMMER;
    }
}
