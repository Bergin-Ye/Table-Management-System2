package com.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.BizException;
import com.erp.config.FieldConfig;
import com.erp.entity.SysColumnPref;
import com.erp.mapper.SysColumnPrefMapper;
import com.erp.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户列偏好：(user_id, doc_type) upsert；无记录返回配置 defaultColumns。
 */
@Service
@RequiredArgsConstructor
public class DocPrefService {

    private final SysColumnPrefMapper columnPrefMapper;
    private final FieldConfigService fieldConfigService;
    private final ObjectMapper objectMapper;

    public List<String> get(String docType) {
        FieldConfig cfg = fieldConfigService.get(docType);
        SysColumnPref pref = columnPrefMapper.selectOne(
                new LambdaQueryWrapper<SysColumnPref>()
                        .eq(SysColumnPref::getUserId, CurrentUser.id())
                        .eq(SysColumnPref::getDocType, docType));
        if (pref == null) {
            return cfg.getDefaultColumns();
        }
        try {
            return objectMapper.readValue(pref.getColumns(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return cfg.getDefaultColumns();
        }
    }

    public void save(String docType, List<String> columns) {
        fieldConfigService.get(docType); // 校验 docType 存在
        Long userId = CurrentUser.id();
        SysColumnPref pref = columnPrefMapper.selectOne(
                new LambdaQueryWrapper<SysColumnPref>()
                        .eq(SysColumnPref::getUserId, userId)
                        .eq(SysColumnPref::getDocType, docType));
        try {
            String json = objectMapper.writeValueAsString(columns);
            if (pref == null) {
                SysColumnPref np = new SysColumnPref();
                np.setUserId(userId);
                np.setDocType(docType);
                np.setColumns(json);
                columnPrefMapper.insert(np);
            } else {
                pref.setColumns(json);
                columnPrefMapper.updateById(pref);
            }
        } catch (JsonProcessingException e) {
            throw BizException.badRequest("列偏好数据不合法");
        }
    }
}
