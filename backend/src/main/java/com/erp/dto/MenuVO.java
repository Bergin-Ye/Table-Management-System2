package com.erp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {
    private Long id;
    private String name;
    private String path;
    private String docType;
    private List<MenuVO> children = new ArrayList<>();
}
