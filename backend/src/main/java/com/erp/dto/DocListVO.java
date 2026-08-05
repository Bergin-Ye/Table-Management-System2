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
    /** 首条明细行（row_no 升序第一行）的字段 KV，供列表展示明细字段列 */
    private Map<String, Object> firstDetail;
}
