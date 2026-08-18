package com.payment;

public class Payment {
    private int receiptNumber;
    private String name;
    private String program;
    private Double intelFee;
    private Double tshirtSizing;
    private Double penalties;
    private Double citNight;
    private String receivedBy;
    private String remarks;

    public Payment(int receiptNumber, String name, String program,
                   Double intelFee, Double tshirtSizing, Double penalties,
                   Double citNight, String receivedBy, String remarks) {
        this.receiptNumber = receiptNumber;
        this.name = name;
        this.program = program;
        this.intelFee = intelFee;
        this.tshirtSizing = tshirtSizing;
        this.penalties = penalties;
        this.citNight = citNight;
        this.receivedBy = receivedBy;
        this.remarks = remarks;
    }

    public int getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(int receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public Double getIntelFee() {
        return intelFee;
    }

    public void setIntelFee(Double intelFee) {
        this.intelFee = intelFee;
    }

    public Double getTshirtSizing() {
        return tshirtSizing;
    }

    public void setTshirtSizing(Double tshirtSizing) {
        this.tshirtSizing = tshirtSizing;
    }

    public Double getPenalties() {
        return penalties;
    }

    public void setPenalties(Double penalties) {
        this.penalties = penalties;
    }

    public Double getCitNight() {
        return citNight;
    }

    public void setCitNight(Double citNight) {
        this.citNight = citNight;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public double getTotalAmount() {
        double total = 0;
        if (intelFee != null) total += intelFee;
        if (tshirtSizing != null) total += tshirtSizing;
        if (penalties != null) total += penalties;
        if (citNight != null) total += citNight;
        return total;
    }

    public boolean hasPayment() {
        return intelFee != null || tshirtSizing != null || penalties != null || citNight != null;
    }

    @Override
    public String toString() {
        return "Receipt #" + receiptNumber + " - " + program + " - Total: ₱" + String.format("%,.2f", getTotalAmount());
    }
}