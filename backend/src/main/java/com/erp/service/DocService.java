package com.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.BizException;
import com.erp.config.FieldConfig;
import com.erp.config.FieldConfig.FieldDef;
import com.erp.dto.DocDetailVO;
import com.erp.dto.DocListVO;
import com.erp.dto.DocSaveRequest;
import com.erp.entity.DocDetail;
import com.erp.entity.DocHead;
import com.erp.mapper.DocDetailMapper;
import com.erp.mapper.DocHeadMapper;
import com.erp.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用单据引擎：10 种单据共用的 CRUD / 全局搜索 / search_text 维护。
 * 所有校验与字段处理只依据字段配置，不发明不修改字段。
 */
@Service
@RequiredArgsConstructor
public class DocService {

    private final DocHeadMapper docHeadMapper;
    private final DocDetailMapper docDetailMapper;
    private final FieldConfigService fieldConfigService;
    private final ObjectMapper objectMapper;

    // ---------------- 列表 ----------------

    public Page<DocListVO> page(String docType, long page, long size, String keyword) {
        FieldConfig cfg = fieldConfigService.get(docType);
        LambdaQueryWrapper<DocHead> wrapper = new LambdaQueryWrapper<DocHead>()
                .eq(DocHead::getDocType, docType);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DocHead::getSearchText, keyword.trim());
        }
        wrapper.orderByDesc(DocHead::getId);
        Page<DocHead> headPage = docHeadMapper.selectPage(new Page<>(page, size), wrapper);

        List<DocListVO> vos = new ArrayList<>();
        if (!headPage.getRecords().isEmpty()) {
            List<Long> ids = headPage.getRecords().stream().map(DocHead::getId).toList();
            Map<Long, Long> counts = countDetails(ids);
            Map<Long, Map<String, Object>> firsts = firstDetails(ids);
            for (DocHead h : headPage.getRecords()) {
                DocListVO vo = new DocListVO();
                vo.setId(h.getId());
                vo.setDocType(h.getDocType());
                vo.setBizNo(h.getBizNo());
                vo.setHeadData(parseMap(h.getHeadData()));
                vo.setUpdatedAt(h.getUpdatedAt());
                vo.setDetailCount(counts.getOrDefault(h.getId(), 0L));
                vo.setFirstDetail(firsts.getOrDefault(h.getId(), new HashMap<>()));
                vos.add(vo);
            }
        }

        Page<DocListVO> result = new Page<>(headPage.getCurrent(), headPage.getSize(), headPage.getTotal());
        result.setRecords(vos);
        return result;
    }

    private Map<Long, Long> countDetails(List<Long> headIds) {
        Map<Long, Long> result = new HashMap<>();
        if (headIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = docDetailMapper.selectMaps(
                new QueryWrapper<DocDetail>()
                        .select("head_id", "COUNT(*) AS cnt")
                        .in("head_id", headIds)
                        .groupBy("head_id"));
        for (Map<String, Object> row : rows) {
            Object hid = row.get("head_id");
            Object cnt = row.get("cnt");
            if (hid instanceof Number hn && cnt instanceof Number cn) {
                result.put(hn.longValue(), cn.longValue());
            }
        }
        return result;
    }

    /** 每个 head_id 取第一条明细（row_no 升序第一行）的 detail_data，解析为 Map */
    private Map<Long, Map<String, Object>> firstDetails(List<Long> headIds) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (headIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = docDetailMapper.selectMaps(
                new QueryWrapper<DocDetail>()
                        .select("head_id", "detail_data")
                        .in("head_id", headIds)
                        .orderByAsc("row_no")
                        .orderByAsc("id"));
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object hid = row.get("head_id");
            if (!(hid instanceof Number hn) || !seen.add(hn.longValue())) {
                continue; // 非 head_id 行，或该单据的第一条已取过
            }
            Object json = row.get("detail_data");
            result.put(hn.longValue(), parseMap(json == null ? null : json.toString()));
        }
        return result;
    }

    // ---------------- 详情 ----------------

    public DocDetailVO detail(String docType, Long id) {
        DocHead head = requireHead(docType, id);
        List<DocDetail> details = docDetailMapper.selectList(
                new LambdaQueryWrapper<DocDetail>()
                        .eq(DocDetail::getHeadId, id)
                        .orderByAsc(DocDetail::getRowNo));

        DocDetailVO vo = new DocDetailVO();
        vo.setHead(parseMap(head.getHeadData()));
        List<DocDetailVO.RowVO> rows = new ArrayList<>();
        for (DocDetail d : details) {
            DocDetailVO.RowVO row = new DocDetailVO.RowVO();
            row.setRowNo(d.getRowNo());
            row.setDetail(parseMap(d.getDetailData()));
            rows.add(row);
        }
        vo.setDetails(rows);
        return vo;
    }

    // ---------------- 新增 / 修改 / 删除 ----------------

    @Transactional
    public Long save(String docType, DocSaveRequest req) {
        FieldConfig cfg = fieldConfigService.get(docType);
        Map<String, Object> head = filterMap(req.getHead(), fieldConfigService.headKeySet(cfg));
        String bizNo = strValue(head.get("编号")).trim();
        validate(cfg, head, bizNo, null);

        DocHead doc = new DocHead();
        doc.setDocType(docType);
        doc.setBizNo(bizNo);
        doc.setHeadData(writeJson(head));
        doc.setCreatedBy(CurrentUser.username());
        doc.setSearchText(buildSearchText(cfg, head, detailsOf(req)));
        docHeadMapper.insert(doc);
        insertDetails(docType, doc.getId(), detailsOf(req));
        return doc.getId();
    }

    @Transactional
    public void update(String docType, Long id, DocSaveRequest req) {
        DocHead existing = requireHead(docType, id);
        FieldConfig cfg = fieldConfigService.get(docType);
        Map<String, Object> head = filterMap(req.getHead(), fieldConfigService.headKeySet(cfg));
        String bizNo = strValue(head.get("编号")).trim();
        validate(cfg, head, bizNo, id);

        existing.setHeadData(writeJson(head));
        existing.setSearchText(buildSearchText(cfg, head, detailsOf(req)));
        docHeadMapper.updateById(existing);

        docDetailMapper.delete(new LambdaQueryWrapper<DocDetail>().eq(DocDetail::getHeadId, id));
        insertDetails(docType, id, detailsOf(req));
    }

    @Transactional
    public void delete(String docType, Long id) {
        requireHead(docType, id);
        docDetailMapper.delete(new LambdaQueryWrapper<DocDetail>().eq(DocDetail::getHeadId, id));
        docHeadMapper.deleteById(id);
    }

    // ---------------- 校验与装配 ----------------

    /** 增改校验：编号必填唯一、日期必填 */
    private void validate(FieldConfig cfg, Map<String, Object> head, String bizNo, Long excludeId) {
        if (bizNo.isEmpty()) {
            throw BizException.badRequest("编号不能为空");
        }
        String date = strValue(head.get("日期")).trim();
        if (date.isEmpty()) {
            throw BizException.badRequest("日期不能为空");
        }
        LambdaQueryWrapper<DocHead> dup = new LambdaQueryWrapper<DocHead>()
                .eq(DocHead::getDocType, cfg.getDocType())
                .eq(DocHead::getBizNo, bizNo);
        if (excludeId != null) {
            dup.ne(DocHead::getId, excludeId);
        }
        Long count = docHeadMapper.selectCount(dup);
        if (count != null && count > 0) {
            throw BizException.badRequest("编号已存在");
        }
    }

    private List<DocSaveRequest.DetailRow> detailsOf(DocSaveRequest req) {
        return req.getDetails() == null ? new ArrayList<>() : req.getDetails();
    }

    private void insertDetails(String docType, Long headId, List<DocSaveRequest.DetailRow> rows) {
        int idx = 1;
        for (DocSaveRequest.DetailRow row : rows) {
            DocDetail d = new DocDetail();
            d.setDocType(docType);
            d.setHeadId(headId);
            d.setRowNo(row.getRowNo() != null ? row.getRowNo() : idx);
            d.setDetailData(writeJson(row.getDetail() == null ? new HashMap<>() : row.getDetail()));
            docDetailMapper.insert(d);
            idx++;
        }
    }

    /** 只保留配置中存在且非空的 key；字符串去首尾空白 */
    private Map<String, Object> filterMap(Map<String, Object> raw, Set<String> allowedKeys) {
        Map<String, Object> result = new HashMap<>();
        if (raw == null) {
            return result;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!allowedKeys.contains(e.getKey())) {
                continue; // 未知 key 忽略
            }
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof String s && s.trim().isEmpty()) {
                continue;
            }
            if (v instanceof String s) {
                result.put(e.getKey(), s.trim());
            } else {
                result.put(e.getKey(), v);
            }
        }
        return result;
    }

    /** search_text：headFields + 每条明细的 detailFields 按配置顺序拼接非空值 */
    private String buildSearchText(FieldConfig cfg, Map<String, Object> head,
                                   List<DocSaveRequest.DetailRow> details) {
        StringBuilder sb = new StringBuilder();
        for (FieldDef f : cfg.getHeadFields()) {
            appendValue(sb, head.get(f.getKey()));
        }
        for (DocSaveRequest.DetailRow row : details) {
            Map<String, Object> detail = row.getDetail();
            if (detail == null) {
                continue;
            }
            for (FieldDef f : cfg.getDetailFields()) {
                appendValue(sb, detail.get(f.getKey()));
            }
        }
        return sb.toString().trim();
    }

    private void appendValue(StringBuilder sb, Object v) {
        if (v == null) {
            return;
        }
        String s = v.toString();
        if (!s.isBlank()) {
            sb.append(s).append(' ');
        }
    }

    private DocHead requireHead(String docType, Long id) {
        DocHead head = docHeadMapper.selectById(id);
        if (head == null || !docType.equals(head.getDocType())) {
            throw BizException.notFound("单据不存在");
        }
        return head;
    }

    private String strValue(Object v) {
        return v == null ? "" : v.toString();
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
}
