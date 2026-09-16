package com.payment;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class ExcelImporter {

    public static List<Student> importFromExcel(String filePath) throws IOException {
        return importFromExcel(filePath, LocalDate.now());
    }

    public static List<Student> importFromExcel(String filePath, LocalDate remittanceDate) throws IOException {
        // Use normalized name as key for matching
        Map<String, Student> studentMap = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            Map<String, Integer> headers = new HashMap<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String header = getStringValue(cell).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
                    if (!header.isEmpty()) headers.put(header, cell.getColumnIndex());
                }
            }

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // Read cells based on header mapping if available, with robust fallbacks
                boolean hasOthers = headers.containsKey("others") || headers.containsKey("other");
                int defaultRecByCol = hasOthers ? 9 : 8;
                int defaultRemCol = hasOthers ? 10 : 9;

                Cell receiptCell = optionalCell(row, headers, 1, "receipt", "receipt no", "receipt number", "or no", "or number");
                Cell nameCell = optionalCell(row, headers, 2, "name", "student name");
                Cell programCell = optionalCell(row, headers, 3, "program", "course", "year program", "program year");
                Cell intelFeeCell = optionalCell(row, headers, 4, "intel fee", "intel");
                Cell tshirtCell = optionalCell(row, headers, 5, "tshirt sizing", "t shirt sizing", "tshirt", "t shirt", "shirt");
                Cell penaltiesCell = optionalCell(row, headers, 6, "penalties", "penalty");
                Cell citNightCell = optionalCell(row, headers, 7, "cit night", "citnight", "night");
                Cell receivedByCell = optionalCell(row, headers, defaultRecByCol, "received by", "received", "receivedby", "receiver");
                Cell remarksCell = optionalCell(row, headers, defaultRemCol, "remarks", "remark", "note", "notes", "comments");
                Cell chargeTermCell = optionalCell(row, headers, -1, "charge term", "charge academic term");
                Cell intelTermCell = optionalCell(row, headers, 11, "intel fee term", "intel term");
                Cell intelAyCell = optionalCell(row, headers, 12, "intel fee ay", "intel ay", "intel academic year");
                Cell tshirtTermCell = optionalCell(row, headers, 13, "t shirt term", "tshirt term", "t shirt sizing term");
                Cell tshirtAyCell = optionalCell(row, headers, 14, "t shirt ay", "tshirt ay", "t shirt academic year");
                Cell penaltiesTermCell = optionalCell(row, headers, 15, "penalties term", "penalty term");
                Cell penaltiesAyCell = optionalCell(row, headers, 16, "penalties ay", "penalty ay", "penalties academic year");
                Cell citTermCell = optionalCell(row, headers, 17, "cit night term", "cit term");
                Cell citAyCell = optionalCell(row, headers, 18, "cit night ay", "cit ay", "cit night academic year");

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
                ChargeAcademicTerm chargeTerm = ChargeAcademicTerm.fromCode(getStringValue(chargeTermCell).trim());

                // Normalize name for matching key
                String normalizedName = NameNormalizer.normalize(name);

                // Get or create student
                Student student = studentMap.get(normalizedName);
                if (student == null) {
                    student = new Student(name);
                    student.setProgram(program);
                    studentMap.put(normalizedName, student);
                } else {
                    // If existing student has no program but this row has one, set it
                    if (student.getProgram() == null && !program.isEmpty()) {
                        student.setProgram(program);
                    }
                }

                // Add payment with remittance date
                Payment payment = new Payment(receiptNumber, name, program,
                        intelFee, tshirtSizing, penalties, citNight,
                        receivedBy, remarks);
                if (VoidReceiptRule.isVoidDueToRemarks(remarks)) {
                    payment.setStatus(Payment.STATUS_VOID);
                } else if (RefundReceiptRule.isRefundDueToRemarks(remarks)) {
                    payment.setStatus(Payment.STATUS_REFUNDED);
                }
                payment.setRemittanceDate(remittanceDate);
                payment.setChargeAcademicTerm(chargeTerm);
                payment.setIntelFeeTerm(parseOptionalTerm(intelTermCell)); payment.setIntelFeeAy(getOptionalString(intelAyCell));
                payment.setTshirtTerm(parseOptionalTerm(tshirtTermCell)); payment.setTshirtAy(getOptionalString(tshirtAyCell));
                payment.setPenaltiesTerm(parseOptionalTerm(penaltiesTermCell)); payment.setPenaltiesAy(getOptionalString(penaltiesAyCell));
                payment.setCitNightTerm(parseOptionalTerm(citTermCell)); payment.setCitNightAy(getOptionalString(citAyCell));
                payment.setImportSourceFile(new File(filePath).getName());
                payment.setImportSourceRow(i + 1);
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

    private static Cell optionalCell(Row row, Map<String, Integer> headers, int fallback, String... names) {
        for (String name : names) {
            Integer index = headers.get(name);
            if (index != null) return row.getCell(index);
        }
        return fallback >= 0 ? row.getCell(fallback) : null;
    }

    private static String getOptionalString(Cell cell) {
        String value = getStringValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static ChargeAcademicTerm parseOptionalTerm(Cell cell) {
        String value = getOptionalString(cell);
        return value == null ? null : ChargeAcademicTerm.fromCode(value);
    }
}
