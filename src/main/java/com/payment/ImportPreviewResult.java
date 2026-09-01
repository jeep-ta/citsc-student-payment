package com.payment;

import java.util.List;

/**
 * Result of import preview generation.
 * Contains the preview items and batch metadata.
 */
public class ImportPreviewResult {

    private List<ImportPreviewItem> items;
    private ImportBatch batch;
    private List<ImportBatchFile> files;

    public ImportPreviewResult(List<ImportPreviewItem> items, ImportBatch batch) {
        this(items, batch, List.of());
    }

    public ImportPreviewResult(List<ImportPreviewItem> items, ImportBatch batch, List<ImportBatchFile> files) {
        this.items = items;
        this.batch = batch;
        this.files = files != null ? files : List.of();
    }

    public List<ImportPreviewItem> getItems() {
        return items;
    }

    public void setItems(List<ImportPreviewItem> items) {
        this.items = items;
    }

    public ImportBatch getBatch() {
        return batch;
    }

    public void setBatch(ImportBatch batch) {
        this.batch = batch;
    }

    public List<ImportBatchFile> getFiles() { return files; }
    public void setFiles(List<ImportBatchFile> files) {
        this.files = files != null ? files : List.of();
    }

    public int getFileCount() { return files.size(); }

    // --- Summary getters ---
    public int getTotalItems() {
        return items != null ? items.size() : 0;
    }

    public int getNewCount() {
        return (int) items.stream().filter(ImportPreviewItem::isNew).count();
    }

    public int getDuplicateCount() {
        return (int) items.stream().filter(ImportPreviewItem::isDuplicate).count();
    }

    public int getConflictCount() {
        return (int) items.stream().filter(ImportPreviewItem::isConflict).count();
    }

    public int getAmbiguousCount() {
        return (int) items.stream().filter(ImportPreviewItem::isAmbiguous).count();
    }

    public int getErrorCount() {
        return (int) items.stream().filter(ImportPreviewItem::isError).count();
    }

    public int getRequiresReviewCount() {
        return (int) items.stream().filter(ImportPreviewItem::requiresReview).count();
    }

    @Override
    public String toString() {
        return String.format(
            "ImportPreviewResult[total=%d, new=%d, dup=%d, conflict=%d, ambiguous=%d, error=%d]",
            getTotalItems(), getNewCount(), getDuplicateCount(), getConflictCount(),
            getAmbiguousCount(), getErrorCount()
        );
    }
}
