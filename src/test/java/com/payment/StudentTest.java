package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.time.LocalDateTime;

public class StudentTest {

    @Test
    void testStudentCodeGeneration() {
        Student s = new Student("Juan Dela Cruz");
        assertNull(s.getStudentCode(), "Student code should be null until assigned");
        s.setStudentCode(StudentCodeGenerator.generate(1));
        assertEquals("STU-000001", s.getStudentCode());
    }

    @Test
    void testNormalizedNamePreserved() {
        Student s = new Student("  Juan   Dela   Cruz  ");
        assertEquals("Juan   Dela   Cruz", s.getName(), "Original name with extra spaces must be preserved");
        assertEquals("juan dela cruz", s.getNormalizedName(), "Normalized name should be trimmed, collapsed, lowercased");
    }

    @Test
    void testNormalizedNameOnSetName() {
        Student s = new Student("Initial");
        s.setName("Maria  GARCIA ");
        assertEquals("Maria  GARCIA", s.getName());
        assertEquals("maria garcia", s.getNormalizedName());
    }

    @Test
    void testEqualityByStudentCode() {
        Student a = new Student("Juan");
        a.setStudentCode("STU-000001");
        Student b = new Student("Juan Dela Cruz"); // different name, same code
        b.setStudentCode("STU-000001");
        assertEquals(a, b, "Students with same studentCode should be equal");

        Student c = new Student("Juan");
        c.setStudentCode("STU-000002");
        assertNotEquals(a, c, "Students with different codes should not be equal");
    }

    @Test
    void testEqualityByNormalizedName() {
        Student a = new Student("Juan Dela Cruz");
        Student b = new Student("  JUAN   DELA   CRUZ  ");
        assertEquals(a, b, "Students with same normalized name should be equal (even without code)");
    }

    @Test
    void testTotalAmount() {
        Student s = new Student("Test Student");
        Payment p1 = new Payment(1, "Test Student", "BSIT", 100.0, 50.0, 0.0, 25.0, "Receiver", "R1");
        Payment p2 = new Payment(2, "Test Student", "BSIT", 200.0, null, 10.0, 0.0, "Receiver", "R2");
        s.addPayment(p1);
        s.addPayment(p2);
        assertEquals(385.0, s.getTotalAmount(), 0.001);
        assertEquals(2, s.getPaymentCount());
    }

    @Test
    void testCreatedUpdatedTimestamps() {
        Student s = new Student("Test");
        assertNotNull(s.getCreatedAt());
        assertNotNull(s.getUpdatedAt());
        assertTrue(s.getUpdatedAt().compareTo(s.getCreatedAt()) >= 0);

        LocalDateTime before = s.getUpdatedAt();
        s.setName("Changed Name");
        assertTrue(s.getUpdatedAt().compareTo(before) >= 0 || s.getUpdatedAt().equals(before));
    }
}