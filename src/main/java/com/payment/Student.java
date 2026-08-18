package com.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class Student {
    private String name;
    private List<Payment> payments;

    public Student(String name) {
        this.name = name;
        this.payments = new ArrayList<>();
    }

    // No-arg constructor for Gson deserialization
    public Student() {
        this.payments = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void addPayment(Payment payment) {
        this.payments.add(payment);
    }

    public void sortPaymentsByReceiptNumber() {
        this.payments.sort(Comparator.comparing(Payment::getReceiptNumber));
    }

    public double getTotalAmount() {
        return payments.stream()
            .mapToDouble(Payment::getTotalAmount)
            .sum();
    }

    public int getPaymentCount() {
        return payments.size();
    }

    @Override
    public String toString() {
        return name + " (" + getPaymentCount() + " payment(s), Total: ₱" + String.format("%,.2f", getTotalAmount()) + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Student student = (Student) obj;
        return name != null ? name.equals(student.name) : student.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}