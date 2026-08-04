package com.erp.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String nickname;
    private String role;
    private Integer status;
    /** 非空则重置密码 */
    private String password;
}
