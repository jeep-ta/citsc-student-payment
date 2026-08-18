package com.payment;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelImporter {

    public static List<Student> importFromExcel(String filePath) throws IOException {
        Map<String, Student> studentMap = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Read cells based on the exact spreadsheet format
                // Columns: # | Receipt # | Name | Program | Intel Fee | Tshirt Sizing | Penalties | CIT Night | Received by | Remarks
                Cell receiptCell = row.getCell(1);
                Cell nameCell = row.getCell(2);
                Cell programCell = row.getCell(3);
                Cell intelFeeCell = row.getCell(4);
                Cell tshirtCell = row.getCell(5);
                Cell penaltiesCell = row.getCell(6);
                Cell citNightCell = row.getCell(7);
                Cell receivedByCell = row.getCell(8);
                Cell remarksCell = row.getCell(9);

                // Skip empty rows
                if (nameCell == null || getStringValue(nameCell).trim().isEmpty()) {
                    continue;
                }

                int receiptNumber = (int) getNumericValue(receiptCell);
                String name = getStringValue(nameCell).trim();
                String program = getStringValue(programCell).trim();
                Double intelFee = getNumericValueOrNull(intelFeeCell);
                Double tshirtSizing = getNumericValueOrNull(tshirtCell);
                Double penalties = getNumericValueOrNull(penaltiesCell);
                Double citNight = getNumericValueOrNull(citNightCell);
                String receivedBy = getStringValue(receivedByCell).trim();
                String remarks = getStringValue(remarksCell).trim();

                // Get or create student
                Student student = studentMap.get(name);
                if (student == null) {
                    student = new Student(name);
                    studentMap.put(name, student);
                }

                // Add payment
                Payment payment = new Payment(receiptNumber, name, program,
                        intelFee, tshirtSizing, penalties, citNight,
                        receivedBy, remarks);
                student.addPayment(payment);
            }
        }

        // Sort payments for each student by receipt number
        for (Student student : studentMap.values()) {
            student.sortPaymentsByReceiptNumber();
        }

        // Convert to list and sort by student name
        List<Student> students = new ArrayList<>(studentMap.values());
        students.sort(Comparator.comparing(Student::getName, String.CASE_INSENSITIVE_ORDER));

        return students;
    }

    private static String getStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static double getNumericValue(Cell cell) {
        if (cell == null) return 0;
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    private static Double getNumericValueOrNull(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                double val = cell.getNumericCellValue();
                return val == 0 ? null : val;
            case STRING:
                String str = cell.getStringCellValue().trim();
                if (str.isEmpty()) return null;
                try {
                    double val2 = Double.parseDouble(str);
                    return val2 == 0 ? null : val2;
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }
}