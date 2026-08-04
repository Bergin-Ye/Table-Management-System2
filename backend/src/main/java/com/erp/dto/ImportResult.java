package com.erp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult {
    private int totalRows;
    private int successDocs;
    private List<FailRow> failRows = new ArrayList<>();

    @Data
    public static class FailRow {
        private int rowNo;
        private String reason;

        public FailRow() {
        }

        public FailRow(int rowNo, String reason) {
            this.rowNo = rowNo;
            this.reason = reason;
        }
    }
}
