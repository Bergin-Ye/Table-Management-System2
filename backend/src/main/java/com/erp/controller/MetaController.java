package com.erp.controller;

import com.erp.common.ApiResponse;
import com.erp.config.FieldConfig;
import com.erp.service.FieldConfigService;
import com.erp.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final FieldConfigService fieldConfigService;
    private final MenuService menuService;

    /** 返回单据字段配置（需登录 + 该 docType 权限） */
    @GetMapping("/{docType}")
    public ApiResponse<FieldConfig> meta(@PathVariable String docType) {
        menuService.assertDocPermission(docType);
        return ApiResponse.ok(fieldConfigService.get(docType));
    }
}
