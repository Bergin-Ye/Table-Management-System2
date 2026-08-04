package com.erp.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DocDetailVO {
    private Map<String, Object> head;
    private List<RowVO> details;

    @Data
    public static class RowVO {
        private Integer rowNo;
        private Map<String, Object> detail;
    }
}
