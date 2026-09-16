package com.payment;

import com.payment.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StudentSimilarityServiceTest {

    private DatabaseManager db;
    private StudentSimilarityService service;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.setCustomDatabaseUrl("jdbc:sqlite:test_similarity.db");
        DatabaseManager.resetInstance();
        db = DatabaseManager.getInstance();
        try (var conn = db.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM dismissed_student_similarities");
            stmt.execute("DELETE FROM students");
            stmt.execute("DELETE FROM payments");
        }
        service = new StudentSimilarityService(db);
    }

    @Test
    void testDetectSimilarStudents() {
        List<Student> list = new ArrayList<>();

        Student s1 = new Student("Abadilla, Naomi Kaye");
        s1.setStudentCode("STU-000001");
        s1.setProgram("EMC");

        Student s2 = new Student("Abadilla, Naomi K.");
        s2.setStudentCode("STU-000002");
        s2.setProgram("EMC");

        Student s3 = new Student("Banlasan, Ibanlee");
        s3.setStudentCode("STU-000003");
        s3.setProgram("IS");

        Student s4 = new Student("Canoy, Cean");
        s4.setStudentCode("STU-000004");
        s4.setProgram("BSCS");

        Student s5 = new Student("Canoy, Sean");
        s5.setStudentCode("STU-000005");
        s5.setProgram("BSCS");

        list.add(s1);
        list.add(s2);
        list.add(s3);
        list.add(s4);
        list.add(s5);

        List<SimilarStudentCandidate> candidates = service.findSimilarStudents(list, 0.80, false, false);
        assertFalse(candidates.isEmpty(), "Should find candidate duplicates");

        // Should find Abadilla Naomi pair
        boolean foundAbadilla = candidates.stream().anyMatch(c ->
            (c.getStudentA().getStudentCode().equals("STU-000001") && c.getStudentB().getStudentCode().equals("STU-000002")) ||
            (c.getStudentA().getStudentCode().equals("STU-000002") && c.getStudentB().getStudentCode().equals("STU-000001"))
        );
        assertTrue(foundAbadilla, "Abadilla pair should be detected");

        // Should find Canoy Cean / Sean pair
        boolean foundCanoy = candidates.stream().anyMatch(c ->
            (c.getStudentA().getStudentCode().equals("STU-000004") && c.getStudentB().getStudentCode().equals("STU-000005")) ||
            (c.getStudentA().getStudentCode().equals("STU-000005") && c.getStudentB().getStudentCode().equals("STU-000004"))
        );
        assertTrue(foundCanoy, "Canoy Cean/Sean typo pair should be detected");
    }

    @Test
    void testDismissedPairExclusion() throws Exception {
        List<Student> list = new ArrayList<>();

        Student s1 = new Student("Canoy, Cean");
        s1.setStudentCode("STU-000004");
        s1.setProgram("BSCS");

        Student s2 = new Student("Canoy, Sean");
        s2.setStudentCode("STU-000005");
        s2.setProgram("BSCS");

        list.add(s1);
        list.add(s2);

        // Initially detected
        List<SimilarStudentCandidate> before = service.findSimilarStudents(list, 0.80, false, false);
        assertEquals(1, before.size());

        // Dismiss the pair
        db.dismissStudentSimilarity("STU-000004", "STU-000005", "Twins with different names", "test-user");
        assertTrue(db.isSimilarityDismissed("STU-000004", "STU-000005"));
        assertTrue(db.isSimilarityDismissed("STU-000005", "STU-000004"));

        // Scan again - should be excluded
        List<SimilarStudentCandidate> after = service.findSimilarStudents(list, 0.80, false, false);
        assertEquals(0, after.size(), "Dismissed pair should not be returned");

        // If includeDismissed is true, it should appear
        List<SimilarStudentCandidate> withDismissed = service.findSimilarStudents(list, 0.80, false, true);
        assertEquals(1, withDismissed.size(), "Should appear when includeDismissed is true");

        // Undismiss
        db.undismissStudentSimilarity("STU-000004", "STU-000005");
        assertFalse(db.isSimilarityDismissed("STU-000004", "STU-000005"));
    }
}
