package com.erp.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DocSaveRequest {
    private Map<String, Object> head;
    private List<DetailRow> details;

    @Data
    public static class DetailRow {
        private Integer rowNo;
        private Map<String, Object> detail;
    }
}
