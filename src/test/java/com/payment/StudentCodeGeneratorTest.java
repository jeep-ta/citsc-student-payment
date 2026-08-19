package com.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StudentCodeGeneratorTest {

    @Test
    void testGenerate() {
        assertEquals("STU-000001", StudentCodeGenerator.generate(1));
        assertEquals("STU-000042", StudentCodeGenerator.generate(42));
        assertEquals("STU-001000", StudentCodeGenerator.generate(1000));
        assertEquals("STU-123456", StudentCodeGenerator.generate(123456));
    }

    @Test
    void testExtractSequence() {
        assertEquals(1, StudentCodeGenerator.extractSequence("STU-000001"));
        assertEquals(42, StudentCodeGenerator.extractSequence("STU-000042"));
        assertEquals(1000, StudentCodeGenerator.extractSequence("STU-001000"));
        assertEquals(123456, StudentCodeGenerator.extractSequence("STU-123456"));
    }

    @Test
    void testInvalidFormat() {
        assertEquals(-1, StudentCodeGenerator.extractSequence("STU-001")); // too short
        assertEquals(-1, StudentCodeGenerator.extractSequence("STU-ABCDEF")); // not numeric
        assertEquals(-1, StudentCodeGenerator.extractSequence("STU")); // incomplete
        assertEquals(-1, StudentCodeGenerator.extractSequence(null)); // null
        assertEquals(-1, StudentCodeGenerator.extractSequence("")); // empty
    }

    @Test
    void testNextSequence() {
        String[] codes = {"STU-000001", "STU-000005", "STU-000003"};
        assertEquals(6, StudentCodeGenerator.nextSequence(codes));
    }

    @Test
    void testNextSequenceEmpty() {
        String[] codes = {};
        assertEquals(1, StudentCodeGenerator.nextSequence(codes));
    }

    @Test
    void testIsValidFormat() {
        assertTrue(StudentCodeGenerator.isValidFormat("STU-000001"));
        assertTrue(StudentCodeGenerator.isValidFormat("STU-123456"));
        assertFalse(StudentCodeGenerator.isValidFormat("STU-001"));
        assertFalse(StudentCodeGenerator.isValidFormat("STU-ABCDEF"));
        assertFalse(StudentCodeGenerator.isValidFormat(null));
        assertFalse(StudentCodeGenerator.isValidFormat(""));
    }
}