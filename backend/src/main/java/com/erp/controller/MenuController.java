package com.erp.controller;

import com.erp.common.ApiResponse;
import com.erp.dto.MenuVO;
import com.erp.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 当前用户可见菜单树（按 RBAC 过滤） */
    @GetMapping("/mine")
    public ApiResponse<List<MenuVO>> mine() {
        return ApiResponse.ok(menuService.mine());
    }
}
