package com.payment;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles persistent storage of student payment data.
 * Saves/loads data to/from JSON file and merges new imports with existing data.
 */
public class DataManager {
    private static final String DATA_FILE = "student_payment_data.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), FORMATTER);
        }
    }

    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString(), FORMATTER);
        }
    }

    /**
     * Saved data structure containing students and metadata
     */
    public static class SavedData {
        private List<Student> students;
        private String lastImportFile;
        private LocalDateTime lastImportTime;
        private int totalImports;

        public SavedData() {
            this.students = new ArrayList<>();
            this.totalImports = 0;
        }

        public List<Student> getStudents() { return students; }
        public void setStudents(List<Student> students) { this.students = students; }
        public String getLastImportFile() { return lastImportFile; }
        public void setLastImportFile(String lastImportFile) { this.lastImportFile = lastImportFile; }
        public LocalDateTime getLastImportTime() { return lastImportTime; }
        public void setLastImportTime(LocalDateTime lastImportTime) { this.lastImportTime = lastImportTime; }
        public int getTotalImports() { return totalImports; }
        public void setTotalImports(int totalImports) { this.totalImports = totalImports; }
    }

    /**
     * Load saved data from JSON file
     * @return SavedData object, or null if file doesn't exist or is corrupted
     */
    public static SavedData loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, SavedData.class);
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Error loading saved data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Save data to JSON file
     * @param data SavedData object to save
     * @return true if successful
     */
    public static boolean saveData(SavedData data) {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            GSON.toJson(data, writer);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Assign student codes to students that don't have one.
     * Uses STU-XXXXXX format.
     *
     * @param students List of students to process
     * @return Number of students assigned codes
     */
    public static int assignStudentCodes(List<Student> students) {
        // Find max existing sequence
        int maxSeq = 0;
        for (Student s : students) {
            if (s.getStudentCode() != null) {
                int seq = StudentCodeGenerator.extractSequence(s.getStudentCode());
                if (seq > maxSeq) maxSeq = seq;
            }
        }

        int assigned = 0;
        for (Student s : students) {
            if (s.getStudentCode() == null || s.getStudentCode().isEmpty()) {
                maxSeq++;
                s.setStudentCode(StudentCodeGenerator.generate(maxSeq));
                assigned++;
            }
        }
        return assigned;
    }

    /**
     * Merge newly imported students with existing saved students.
     * New payments are appended to existing students (matched by normalized name).
     * New students are added to the list.
     * Student codes are assigned to new students.
     * Payment.studentId is linked to Student.studentCode.
     *
     * @param newStudents List of students from new import
     * @return Merged list of students
     */
    public static List<Student> mergeStudents(List<Student> newStudents) {
        SavedData savedData = loadData();
        List<Student> existingStudents = (savedData != null && savedData.getStudents() != null)
            ? savedData.getStudents()
            : new ArrayList<>();

        // Create a map of existing students by normalized name (case-insensitive)
        Map<String, Student> existingMap = new HashMap<>();
        for (Student s : existingStudents) {
            String key = s.getNormalizedName() != null ? s.getNormalizedName() : s.getName().toLowerCase();
            existingMap.put(key, s);
        }

        // Merge new students
        for (Student newStudent : newStudents) {
            String key = newStudent.getNormalizedName() != null
                ? newStudent.getNormalizedName()
                : newStudent.getName().toLowerCase();
            Student existing = existingMap.get(key);

            if (existing != null) {
                // Student exists - merge payments
                // Add new payments that don't already exist (by receipt number)
                Set<Integer> existingReceipts = new HashSet<>();
                for (Payment p : existing.getPayments()) {
                    existingReceipts.add(p.getReceiptNumber());
                }

                for (Payment newPayment : newStudent.getPayments()) {
                    if (!existingReceipts.contains(newPayment.getReceiptNumber())) {
                        // Link payment to student's code
                        newPayment.setStudentId(existing.getStudentCode());
                        existing.addPayment(newPayment);
                    }
                }

                // Re-sort payments by receipt number
                existing.sortPaymentsByReceiptNumber();
            } else {
                // New student - add to map
                existingMap.put(key, newStudent);
            }
        }

        // Convert back to sorted list
        List<Student> merged = new ArrayList<>(existingMap.values());
        merged.sort(Comparator.comparing(Student::getName, String.CASE_INSENSITIVE_ORDER));

        // Assign student codes to any students without them
        assignStudentCodes(merged);

        // Link all payments to their student's code
        for (Student s : merged) {
            for (Payment p : s.getPayments()) {
                if (p.getStudentId() == null || p.getStudentId().isEmpty()) {
                    p.setStudentId(s.getStudentCode());
                }
            }
        }

        return merged;
    }

    /**
     * Save current students after import (increments import counter)
     * @param students Current list of students
     * @param importFileName Name of the imported file
     * @return true if successful
     */
    public static boolean saveAfterImport(List<Student> students, String importFileName) {
        // Ensure all students have codes and payments are linked
        assignStudentCodes(students);
        for (Student s : students) {
            for (Payment p : s.getPayments()) {
                if (p.getStudentId() == null || p.getStudentId().isEmpty()) {
                    p.setStudentId(s.getStudentCode());
                }
            }
        }

        SavedData data = new SavedData();
        data.setStudents(students);
        data.setLastImportFile(importFileName);
        data.setLastImportTime(LocalDateTime.now());

        // Get previous total imports and increment
        SavedData previous = loadData();
        int totalImports = (previous != null) ? previous.getTotalImports() + 1 : 1;
        data.setTotalImports(totalImports);

        return saveData(data);
    }

    /**
     * Save current students without incrementing import counter (for auto-save on exit)
     * @param students Current list of students
     * @return true if successful
     */
    public static boolean saveDataOnly(List<Student> students) {
        SavedData previous = loadData();
        if (previous == null) {
            previous = new SavedData();
        }
        SavedData data = new SavedData();
        data.setStudents(students);
        data.setLastImportFile(previous.getLastImportFile());
        data.setLastImportTime(previous.getLastImportTime());
        data.setTotalImports(previous.getTotalImports());

        return saveData(data);
    }

    /**
     * Get info about last import for display
     * @return string with last import info, or empty string
     */
    public static String getLastImportInfo() {
        SavedData data = loadData();
        if (data == null || data.getLastImportTime() == null) {
            return "";
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return String.format("Last import: %s (%s) - %d total imports",
            data.getLastImportFile(),
            data.getLastImportTime().format(fmt),
            data.getTotalImports());
    }
}