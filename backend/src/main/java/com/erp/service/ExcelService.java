package com.erp.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.BizException;
import com.erp.config.FieldConfig;
import com.erp.config.FieldConfig.FieldDef;
import com.erp.dto.ImportResult;
import com.erp.entity.DocDetail;
import com.erp.entity.DocHead;
import com.erp.mapper.DocDetailMapper;
import com.erp.mapper.DocHeadMapper;
import com.erp.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Excel 导入 / 导出（系统最重要功能，按规格书第 9 节严格实现）。
 * 列映射、分组合并、重复编号跳过、失败行报告、往返保真。
 */
@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter IMP_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DocHeadMapper docHeadMapper;
    private final DocDetailMapper docDetailMapper;
    private final FieldConfigService fieldConfigService;
    private final ObjectMapper objectMapper;

    // ==================== 导出 ====================

    public void export(HttpServletResponse response, String docType, String keyword) {
        FieldConfig cfg = fieldConfigService.get(docType);

        LambdaQueryWrapper<DocHead> wrapper = new LambdaQueryWrapper<DocHead>()
                .eq(DocHead::getDocType, docType);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DocHead::getSearchText, keyword.trim());
        }
        wrapper.orderByAsc(DocHead::getId).last("LIMIT 5000");
        List<DocHead> heads = docHeadMapper.selectList(wrapper);

        List<Long> ids = heads.stream().map(DocHead::getId).toList();
        Map<Long, List<DocDetail>> detailsByHead = new HashMap<>();
        if (!ids.isEmpty()) {
            List<DocDetail> all = docDetailMapper.selectList(
                    new LambdaQueryWrapper<DocDetail>().in(DocDetail::getHeadId, ids).orderByAsc(DocDetail::getRowNo));
            for (DocDetail d : all) {
                detailsByHead.computeIfAbsent(d.getHeadId(), k -> new ArrayList<>()).add(d);
            }
        }

        List<List<String>> header = new ArrayList<>();
        for (FieldDef f : cfg.getHeadFields()) {
            header.add(List.of(f.getExcelLabel()));
        }
        for (FieldDef f : cfg.getDetailFields()) {
            header.add(List.of(f.getExcelLabel()));
        }

        List<List<Object>> rows = new ArrayList<>();
        for (DocHead h : heads) {
            Map<String, Object> hm = parseMap(h.getHeadData());
            List<DocDetail> dets = detailsByHead.get(h.getId());
            if (dets == null || dets.isEmpty()) {
                rows.add(buildExportRow(cfg, hm, null));
            } else {
                for (DocDetail d : dets) {
                    rows.add(buildExportRow(cfg, hm, parseMap(d.getDetailData())));
                }
            }
        }

        String fileName = cfg.getName() + "_导出_" + LocalDate.now().format(FILE_DATE) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try {
            EasyExcel.write(response.getOutputStream())
                    .head(header)
                    .sheet(cfg.getName())
                    .doWrite(rows);
        } catch (IOException e) {
            throw BizException.badRequest("导出失败");
        }
    }

    private List<Object> buildExportRow(FieldConfig cfg, Map<String, Object> hm, Map<String, Object> dm) {
        List<Object> row = new ArrayList<>();
        for (FieldDef f : cfg.getHeadFields()) {
            row.add(fmtVal(hm == null ? null : hm.get(f.getKey())));
        }
        for (FieldDef f : cfg.getDetailFields()) {
            row.add(fmtVal(dm == null ? null : dm.get(f.getKey())));
        }
        return row;
    }

    private String fmtVal(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                return "";
            }
            if (d == Math.floor(d)) {
                return String.valueOf(d.longValue());
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    // ==================== 导入 ====================

    @Transactional
    public ImportResult importExcel(MultipartFile file, String docType) {
        FieldConfig cfg = fieldConfigService.get(docType);

        // 监听器把表头/数据行收集到可变容器（匿名类无法改局部变量）
        HeadHolder holder = new HeadHolder();
        List<RowData> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream()) {
            EasyExcel.read(is)
                    .headRowNumber(1)
                    .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                        @Override
                        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                            holder.map = headMap == null ? new HashMap<>() : new HashMap<>(headMap);
                        }

                        @Override
                        public void invoke(Map<Integer, String> data, AnalysisContext context) {
                            int physicalRow = context.readRowHolder().getRowIndex() + 1; // 1 起，表头=1
                            rows.add(new RowData(physicalRow,
                                    data == null ? new HashMap<>() : new HashMap<>(data)));
                        }

                        @Override
                        public void doAfterAllAnalysed(AnalysisContext context) {
                        }
                    })
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw BizException.badRequest("读取文件失败，请确认是 Excel 文件");
        }

        return processRows(cfg, holder.map, rows);
    }

    private ImportResult processRows(FieldConfig cfg, Map<Integer, String> headMap,
                                     List<RowData> rows) {
        ImportResult result = new ImportResult();
        Map<Integer, FieldDef> colMap = fieldConfigService.buildColumnMap(cfg, headMap);

        // 1. 逐行解析
        List<ParsedRow> parsed = new ArrayList<>();
        for (RowData rd : rows) {
            if (isBlankRow(rd.raw)) {
                continue; // 全空行跳过
            }
            result.setTotalRows(result.getTotalRows() + 1);

            Map<String, Object> head = new LinkedHashMap<>();
            Map<String, Object> detail = new LinkedHashMap<>();
            boolean rowOk = true;
            for (Map.Entry<Integer, FieldDef> e : colMap.entrySet()) {
                FieldDef f = e.getValue();
                Object raw = rd.raw.get(e.getKey());
                if (raw == null) {
                    continue;
                }
                Object val = fieldConfigService.convertByType(f, raw);
                if (val == null) {
                    // 非空内容转换失败
                    result.getFailRows().add(new ImportResult.FailRow(rd.rowNo, f.getKey() + "格式错误"));
                    rowOk = false;
                    break;
                }
                if (isHeadField(cfg, f)) {
                    head.put(f.getKey(), val);
                } else {
                    detail.put(f.getKey(), val);
                }
            }
            if (rowOk) {
                parsed.add(new ParsedRow(rd.rowNo, head, detail));
            }
        }

        // 2. 分组：同「编号」合并为一单；空编号每行独立成单（回退编号）
        List<GroupDoc> groups = new ArrayList<>();
        Map<String, GroupDoc> byBizNo = new LinkedHashMap<>();
        String ts = java.time.LocalDateTime.now().format(IMP_TS);
        int impSeq = 0;
        for (ParsedRow pr : parsed) {
            String bizNo = pr.head.containsKey("编号")
                    ? String.valueOf(pr.head.get("编号")).trim()
                    : "";
            if (bizNo.isEmpty()) {
                impSeq++;
                String gen = "IMP" + ts + String.format("%02d", impSeq);
                GroupDoc g = new GroupDoc(pr.rowNo, gen, pr.head);
                g.details.add(pr.detail);
                groups.add(g);
            } else {
                GroupDoc g = byBizNo.get(bizNo);
                if (g == null) {
                    g = new GroupDoc(pr.rowNo, bizNo, new LinkedHashMap<>());
                    byBizNo.put(bizNo, g);
                    groups.add(g);
                }
                for (Map.Entry<String, Object> e : pr.head.entrySet()) {
                    if (!g.head.containsKey(e.getKey()) || g.head.get(e.getKey()) == null) {
                        g.head.put(e.getKey(), e.getValue());
                    }
                }
                g.details.add(pr.detail);
            }
        }

        // 3. 逐单写入：日期必填、编号唯一（含本文件内）
        Set<String> seenBizNo = new HashSet<>();
        for (GroupDoc g : groups) {
            g.head.put("编号", g.bizNo);
            String date = g.head.containsKey("日期") ? String.valueOf(g.head.get("日期")).trim() : "";
            if (date.isEmpty()) {
                result.getFailRows().add(new ImportResult.FailRow(g.firstRowNo, "日期不能为空"));
                continue;
            }
            if (seenBizNo.contains(g.bizNo)) {
                result.getFailRows().add(new ImportResult.FailRow(g.firstRowNo, "编号已存在"));
                continue;
            }
            Long cnt = docHeadMapper.selectCount(new LambdaQueryWrapper<DocHead>()
                    .eq(DocHead::getDocType, cfg.getDocType())
                    .eq(DocHead::getBizNo, g.bizNo));
            if (cnt != null && cnt > 0) {
                result.getFailRows().add(new ImportResult.FailRow(g.firstRowNo, "编号已存在"));
                continue;
            }

            DocHead doc = new DocHead();
            doc.setDocType(cfg.getDocType());
            doc.setBizNo(g.bizNo);
            doc.setHeadData(writeJson(g.head));
            doc.setCreatedBy(CurrentUser.username());
            doc.setSearchText(buildSearchText(cfg, g.head, g.details));
            docHeadMapper.insert(doc);

            int idx = 1;
            for (Map<String, Object> d : g.details) {
                DocDetail det = new DocDetail();
                det.setDocType(cfg.getDocType());
                det.setHeadId(doc.getId());
                det.setRowNo(idx++);
                det.setDetailData(writeJson(d));
                docDetailMapper.insert(det);
            }
            seenBizNo.add(g.bizNo);
            result.setSuccessDocs(result.getSuccessDocs() + 1);
        }

        return result;
    }

    private boolean isBlankRow(Map<Integer, String> raw) {
        for (Object v : raw.values()) {
            if (v != null && !v.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** 字段是否属于 head（按对象引用判定，避免「编号」等同名 key 混淆） */
    private boolean isHeadField(FieldConfig cfg, FieldDef f) {
        for (FieldDef hf : cfg.getHeadFields()) {
            if (hf == f) {
                return true;
            }
        }
        return false;
    }

    private String buildSearchText(FieldConfig cfg, Map<String, Object> head, List<Map<String, Object>> details) {
        StringBuilder sb = new StringBuilder();
        for (FieldDef f : cfg.getHeadFields()) {
            append(sb, head.get(f.getKey()));
        }
        for (Map<String, Object> detail : details) {
            for (FieldDef f : cfg.getDetailFields()) {
                append(sb, detail.get(f.getKey()));
            }
        }
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, Object v) {
        if (v == null) {
            return;
        }
        String s = v.toString();
        if (!s.isBlank()) {
            sb.append(s).append(' ');
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw BizException.badRequest("数据序列化失败");
        }
    }

    // ---------------- 内部数据结构 ----------------

    private static class HeadHolder {
        Map<Integer, String> map = new HashMap<>();
    }

    private static class RowData {
        final int rowNo;
        final Map<Integer, String> raw;

        RowData(int rowNo, Map<Integer, String> raw) {
            this.rowNo = rowNo;
            this.raw = raw;
        }
    }

    private static class ParsedRow {
        final int rowNo;
        final Map<String, Object> head;
        final Map<String, Object> detail;

        ParsedRow(int rowNo, Map<String, Object> head, Map<String, Object> detail) {
            this.rowNo = rowNo;
            this.head = head;
            this.detail = detail;
        }
    }

    private static class GroupDoc {
        final int firstRowNo;
        final String bizNo;
        final Map<String, Object> head;
        final List<Map<String, Object>> details = new ArrayList<>();

        GroupDoc(int firstRowNo, String bizNo, Map<String, Object> head) {
            this.firstRowNo = firstRowNo;
            this.bizNo = bizNo;
            this.head = head;
        }
    }
}
