package com.payment;

/**
 * One spreadsheet selected for an import batch and the receipt issuance
 * period that applies to its rows.
 */
public record ImportFileSelection(
    String filePath,
    String receiptAcademicYear,
    ChargeAcademicTerm receiptTerm
) {
    public ImportFileSelection {
        filePath = filePath != null ? filePath.trim() : null;
        ReceiptKey normalizedScope = new ReceiptKey(1, receiptAcademicYear, receiptTerm);
        receiptAcademicYear = normalizedScope.academicYear();
        receiptTerm = normalizedScope.term();
    }

    public ReceiptKey getReceiptKey(int receiptNumber) {
        return new ReceiptKey(receiptNumber, receiptAcademicYear, receiptTerm);
    }
}
