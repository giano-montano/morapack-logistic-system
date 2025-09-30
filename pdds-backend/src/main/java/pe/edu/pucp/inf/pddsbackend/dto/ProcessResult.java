package pe.edu.pucp.inf.pddsbackend.dto;

import java.util.List;

public class ProcessResult {
    private final int savedCount;
    private final int skippedCount;
    private final List<String> errors;

    public ProcessResult(int savedCount, int skippedCount, List<String> errors) {
        this.savedCount = savedCount;
        this.skippedCount = skippedCount;
        this.errors = errors;
    }

    // getters
    public int getSavedCount() { return savedCount; }
    public int getSkippedCount() { return skippedCount; }
    public List<String> getErrors() { return errors; }
}
