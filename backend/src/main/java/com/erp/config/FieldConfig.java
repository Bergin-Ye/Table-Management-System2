package com.erp.config;

import lombok.Data;

import java.util.List;

/**
 * 单据字段配置（field-config/{docType}.json）反序列化模型。
 * 权威来源：docs/superpowers/specs/field-config/*.json，本模型只读不改。
 */
@Data
public class FieldConfig {

    private String docType;
    private String name;
    private String menu;
    private List<FieldDef> headFields;
    private List<FieldDef> detailFields;
    private List<String> defaultColumns;

    @Data
    public static class FieldDef {
        private String key;
        private String label;
        private String excelLabel;
        /** text / number / int / date */
        private String type;
        private boolean required;
    }
}
