package com.erp.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleMenuSaveRequest {
    private String role;
    private List<Long> menuIds;
}
