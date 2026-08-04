package com.erp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DocListVO {
    private Long id;
    private String docType;
    private String bizNo;
    private Map<String, Object> headData;
    private LocalDateTime updatedAt;
    private Long detailCount;
}
