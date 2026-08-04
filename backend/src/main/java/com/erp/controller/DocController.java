package com.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.ApiResponse;
import com.erp.config.FieldConfig;
import com.erp.dto.DocDetailVO;
import com.erp.dto.DocListVO;
import com.erp.dto.DocSaveRequest;
import com.erp.dto.ImportResult;
import com.erp.dto.PrefSaveRequest;
import com.erp.service.DocPrefService;
import com.erp.service.DocService;
import com.erp.service.ExcelService;
import com.erp.service.FieldConfigService;
import com.erp.service.MenuService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 通用单据接口：10 种单据共用，docType 由字段配置决定差异。
 * 全部接口先做「登录 + 角色菜单含 docType」鉴权（403）。
 */
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;
    private final DocPrefService docPrefService;
    private final ExcelService excelService;
    private final MenuService menuService;
    private final FieldConfigService fieldConfigService;

    /** 先校验 docType 存在（否则 404），再鉴权（无权限 403） */
    private FieldConfig requireDocType(String docType) {
        FieldConfig cfg = fieldConfigService.get(docType); // 不存在 → 404
        menuService.assertDocPermission(docType);          // 存在但无权限 → 403
        return cfg;
    }

    @GetMapping("/{docType}")
    public ApiResponse<Page<DocListVO>> list(@PathVariable String docType,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String keyword) {
        requireDocType(docType);
        return ApiResponse.ok(docService.page(docType, page, size, keyword));
    }

    @GetMapping("/{docType}/{id}")
    public ApiResponse<DocDetailVO> detail(@PathVariable String docType, @PathVariable Long id) {
        requireDocType(docType);
        return ApiResponse.ok(docService.detail(docType, id));
    }

    @PostMapping("/{docType}")
    public ApiResponse<Long> save(@PathVariable String docType, @RequestBody DocSaveRequest req) {
        requireDocType(docType);
        return ApiResponse.ok(docService.save(docType, req));
    }

    @PutMapping("/{docType}/{id}")
    public ApiResponse<Void> update(@PathVariable String docType, @PathVariable Long id,
                                    @RequestBody DocSaveRequest req) {
        requireDocType(docType);
        docService.update(docType, id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{docType}/{id}")
    public ApiResponse<Void> delete(@PathVariable String docType, @PathVariable Long id) {
        requireDocType(docType);
        docService.delete(docType, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{docType}/import")
    public ApiResponse<ImportResult> importExcel(@PathVariable String docType,
                                                 @RequestParam("file") MultipartFile file) {
        requireDocType(docType);
        return ApiResponse.ok(excelService.importExcel(file, docType));
    }

    @GetMapping("/{docType}/export")
    public void export(@PathVariable String docType,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        requireDocType(docType);
        excelService.export(response, docType, keyword);
    }

    @GetMapping("/{docType}/pref")
    public ApiResponse<List<String>> getPref(@PathVariable String docType) {
        requireDocType(docType);
        return ApiResponse.ok(docPrefService.get(docType));
    }

    @PutMapping("/{docType}/pref")
    public ApiResponse<Void> savePref(@PathVariable String docType, @RequestBody PrefSaveRequest req) {
        requireDocType(docType);
        docPrefService.save(docType, req.getColumns());
        return ApiResponse.ok();
    }
}
