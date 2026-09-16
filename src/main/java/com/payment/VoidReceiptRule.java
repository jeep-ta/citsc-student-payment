package com.payment;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule engine for detecting comments in receipt remarks indicating
 * voided receipts due to physical damages, duplication, spoilage, or issuance errors,
 * while strictly avoiding refund statements.
 */
public final class VoidReceiptRule {

    private VoidReceiptRule() {}

    // Regex patterns for refund statements (must NOT be treated as voided receipt due to damages/duplication)
    private static final List<Pattern> REFUND_PATTERNS = List.of(
        Pattern.compile("\\brefund(s|ed|ing)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\breimburse(s|d|ments?)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\breturn(ed)?\\s+of\\s+payment\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\breturned\\s+payment\\b", Pattern.CASE_INSENSITIVE)
    );

    // Regex patterns for voiding due to damages, duplications, errors, or cancellation
    private static final List<Pattern> VOID_PATTERNS = List.of(
        // Damage variations: damaged receipt, damage, damaged paper, etc.
        Pattern.compile("\\bdamag(e|es|ed|ing)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(paper|receipt)\\s+damaged\\b", Pattern.CASE_INSENSITIVE),

        // Duplication variations: duplication, duplicate, double entry, etc.
        Pattern.compile("\\bduplicat(e|ed|ion|ing)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bdouble\\s+(entry|entered|issue|issued|issuance)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\balready\\s+(entered|encoded|recorded|paid)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bduplicate\\s+copy\\b", Pattern.CASE_INSENSITIVE),

        // Void variations: void, voided, voiding, null and void, etc.
        Pattern.compile("\\bvoid(ed|ing|s)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnull\\s+and\\s+void\\b", Pattern.CASE_INSENSITIVE),

        // Cancellation variations: cancel, cancelled, canceled, cancellation, etc.
        Pattern.compile("\\bcancel(l)?(ed|ing|ation|s)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\binvalid(ated)?(\\s+receipt)?\\b", Pattern.CASE_INSENSITIVE),

        // Spoilage / Physical defects: spoil, spoiled, spoilage, torn, mutilated
        Pattern.compile("\\bspoil(ed|age|s)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\btorn(\\s+receipt)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmutilated\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bdefective\\b", Pattern.CASE_INSENSITIVE),

        // Misprint / Clerical error variations
        Pattern.compile("\\bmisprint(ed)?\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(wrong|error|erroneous)\\s+(entry|amount|receipt|name|encoding)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(printed|entered|issued)\\s+by\\s+mistake\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bmistake\\s+in\\s+receipt\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Checks whether a remark string expresses a refund.
     * If true, this remark must NOT be treated as a voided receipt under the damage/duplication rule.
     */
    public static boolean containsRefundStatement(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return false;
        }
        String normalized = remarks.trim().toLowerCase(Locale.ROOT);
        for (Pattern p : REFUND_PATTERNS) {
            if (p.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a remark string indicates that the receipt is voided
     * due to damages, duplication, spoilage, error, or cancellation,
     * while excluding refund statements.
     *
     * @param remarks The text from the remarks column/field
     * @return true if voided due to damages/duplication/etc., false otherwise
     */
    public static boolean isVoidDueToRemarks(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return false;
        }

        // Rule constraint: refund statements MUST be avoided
        if (containsRefundStatement(remarks)) {
            return false;
        }

        String normalized = remarks.trim().toLowerCase(Locale.ROOT);
        for (Pattern p : VOID_PATTERNS) {
            if (p.matcher(normalized).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates an audit trail reason string for a voided remark.
     */
    public static String getVoidReason(String remarks) {
        if (remarks == null || remarks.isBlank()) {
            return "Voided receipt";
        }
        return "Auto-voided based on remarks: " + remarks.trim();
    }
}

