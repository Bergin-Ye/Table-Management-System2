package com.erp.dto;

import lombok.Data;

import java.util.List;

@Data
public class PrefSaveRequest {
    private List<String> columns;
}
