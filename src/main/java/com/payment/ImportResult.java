package com.payment;

/**
 * Result of a committed import operation.
 */
public class ImportResult {

    private ImportBatch batch;
    private int newRecords;
    private int duplicateRecords;
    private int conflictRecords;
    private int errorRecords;

    public ImportResult(ImportBatch batch, int newRecords, int duplicateRecords,
                        int conflictRecords, int errorRecords) {
        this.batch = batch;
        this.newRecords = newRecords;
        this.duplicateRecords = duplicateRecords;
        this.conflictRecords = conflictRecords;
        this.errorRecords = errorRecords;
    }

    public ImportBatch getBatch() { return batch; }
    public int getNewRecords() { return newRecords; }
    public int getDuplicateRecords() { return duplicateRecords; }
    public int getConflictRecords() { return conflictRecords; }
    public int getErrorRecords() { return errorRecords; }
    public int getTotalProcessed() { return newRecords + duplicateRecords + conflictRecords + errorRecords; }

    @Override
    public String toString() {
        return String.format(
            "ImportResult[batch=%s, new=%d, dup=%d, conflict=%d, error=%d]",
            batch.getBatchCode(), newRecords, duplicateRecords, conflictRecords, errorRecords
        );
    }
}