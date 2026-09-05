package com.payment;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Inclusive date-range rule used to attribute one fee category to an
 * academic year and term.  Receipt issuance period is deliberately not part
 * of this object: a single receipt may contain fees covered by different
 * rules.
 */
public class FeeTermRule {
    public static final String INTEL_FEE = "Intel Fee";
    public static final String T_SHIRT = "T-Shirt";
    public static final String PENALTIES = "Penalties";
    public static final String CIT_NIGHT = "CIT Night";

    private int id;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    private String academicYear;
    private ChargeAcademicTerm term;
    private boolean enabled = true;

    public FeeTermRule() { }

    public FeeTermRule(String category, LocalDate startDate, LocalDate endDate,
                       String academicYear, ChargeAcademicTerm term) {
        this.category = normalizeCategory(category);
        this.startDate = startDate;
        this.endDate = endDate;
        this.academicYear = academicYear == null || academicYear.isBlank() ? null : academicYear.trim();
        this.term = term == null ? ChargeAcademicTerm.UNASSIGNED : term;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = normalizeCategory(category); }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear == null || academicYear.isBlank() ? null : academicYear.trim(); }
    public ChargeAcademicTerm getTerm() { return term == null ? ChargeAcademicTerm.UNASSIGNED : term; }
    public void setTerm(ChargeAcademicTerm term) { this.term = term == null ? ChargeAcademicTerm.UNASSIGNED : term; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean matches(LocalDate date) {
        return enabled && date != null && startDate != null && endDate != null
            && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public void validate() {
        if (!isSupportedCategory(category)) throw new IllegalArgumentException("Unsupported fee category: " + category);
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new IllegalArgumentException("Rule date range must be complete and start on/before end date");
        if (academicYear == null || academicYear.isBlank()) throw new IllegalArgumentException("Academic year is required");
        if (getTerm() == ChargeAcademicTerm.UNASSIGNED || getTerm() == ChargeAcademicTerm.CURRENT || getTerm() == ChargeAcademicTerm.PREVIOUS)
            throw new IllegalArgumentException("Rule term must be a concrete semester or Summer");
    }

    public static String normalizeCategory(String category) {
        if (category == null) return null;
        String value = category.trim().toLowerCase(Locale.ROOT);
        if (value.equals("intel fee") || value.equals("intel_fee")) return INTEL_FEE;
        if (value.equals("t-shirt") || value.equals("t-shirt sizing") || value.equals("tshirt") || value.equals("tshirt sizing")) return T_SHIRT;
        if (value.equals("penalties") || value.equals("penalty")) return PENALTIES;
        if (value.equals("cit night") || value.equals("cit_night")) return CIT_NIGHT;
        return category.trim();
    }

    public static boolean isSupportedCategory(String category) {
        return INTEL_FEE.equals(category) || T_SHIRT.equals(category)
            || PENALTIES.equals(category) || CIT_NIGHT.equals(category);
    }
}
