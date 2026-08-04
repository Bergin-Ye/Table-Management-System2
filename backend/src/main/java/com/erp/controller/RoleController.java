package com.erp.controller;

import com.erp.common.ApiResponse;
import com.erp.dto.RoleMenuSaveRequest;
import com.erp.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** 角色已授权的菜单 id 列表 */
    @GetMapping("/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Long>> roleMenus(@RequestParam String role) {
        return ApiResponse.ok(roleService.getRoleMenus(role));
    }

    /** 保存角色授权 */
    @PutMapping("/menus")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> saveRoleMenus(@RequestBody RoleMenuSaveRequest req) {
        roleService.saveRoleMenus(req.getRole(), req.getMenuIds());
        return ApiResponse.ok();
    }
}
