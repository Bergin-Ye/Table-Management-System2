package com.erp.service;

import com.erp.common.BizException;
import com.erp.config.FieldConfig;
import com.erp.config.FieldConfig.FieldDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 单据字段配置加载与工具服务。
 * 启动时从 classpath:field-config/*.json 加载全部 10 份配置（权威契约，只读）。
 * 提供：元数据读取、合法 key 集合、Excel 列映射、按字段类型做值转换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldConfigService {

    private static final Pattern NUMBER_CLEAN = Pattern.compile("[^\\d.\\-]");

    private final Map<String, FieldConfig> configs = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void load() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:field-config/*.json");
            for (Resource r : resources) {
                FieldConfig cfg = objectMapper.readValue(r.getInputStream(), FieldConfig.class);
                configs.put(cfg.getDocType(), cfg);
            }
            log.info("已加载字段配置 {} 份: {}", configs.size(), configs.keySet());
            if (configs.isEmpty()) {
                throw new IllegalStateException("未找到任何字段配置 classpath:field-config/*.json");
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载字段配置失败", e);
        }
    }

    public FieldConfig get(String docType) {
        FieldConfig cfg = configs.get(docType);
        if (cfg == null) {
            throw BizException.notFound("单据类型不存在: " + docType);
        }
        return cfg;
    }

    public boolean exists(String docType) {
        return configs.containsKey(docType);
    }

    /** 全部合法 head key */
    public Set<String> headKeySet(FieldConfig cfg) {
        Set<String> keys = new HashSet<>();
        for (FieldDef f : cfg.getHeadFields()) {
            keys.add(f.getKey());
        }
        return keys;
    }

    /** 全部合法 detail key */
    public Set<String> detailKeySet(FieldConfig cfg) {
        Set<String> keys = new HashSet<>();
        for (FieldDef f : cfg.getDetailFields()) {
            keys.add(f.getKey());
        }
        return keys;
    }

    /** 表头文本规范化：去掉所有空白 */
    public static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "").trim();
    }

    /**
     * Excel 列映射：对每个有内容的列，按"headFields 顺序、再 detailFields 顺序，
     * 贪心匹配第一个尚未被占用的字段（excelLabel 去空白 或 key 相等）。
     * 解决 head「编号」与 detail「编号」同名的冲突。
     *
     * @return 列索引 -> 字段定义
     */
    public Map<Integer, FieldDef> buildColumnMap(FieldConfig cfg, Map<Integer, String> headMap) {
        List<FieldDef> pool = new ArrayList<>();
        pool.addAll(cfg.getHeadFields());
        pool.addAll(cfg.getDetailFields());

        Set<FieldDef> used = new HashSet<>();
        Map<Integer, FieldDef> result = new LinkedHashMap<>();

        List<Integer> indexes = new ArrayList<>(headMap.keySet());
        indexes.sort(Integer::compareTo);

        for (Integer idx : indexes) {
            String header = normalizeHeader(headMap.get(idx));
            if (header.isEmpty()) {
                continue;
            }
            for (FieldDef f : pool) {
                if (used.contains(f)) {
                    continue;
                }
                String excel = normalizeHeader(f.getExcelLabel());
                String key = normalizeHeader(f.getKey());
                if (header.equals(excel) || header.equals(key)) {
                    used.add(f);
                    result.put(idx, f);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 按字段 type 做值规范化：
     * - date  -> "yyyy-MM-dd"（支持多种入参形态：Date、数字、字符串多种格式）
     * - int   -> Long
     * - number-> Double
     * - text  -> 去首尾空白的字符串
     * 转换失败返回 null（由调用方决定记失败行）。
     */
    public Object convertByType(FieldDef f, Object raw) {
        if (raw == null) {
            return null;
        }
        String type = f.getType() == null ? "text" : f.getType();

        // Excel 日期单元格
        if (raw instanceof java.util.Date d) {
            if ("date".equals(type)) {
                return d.toInstant().atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return raw.toString();
        }
        if (raw instanceof java.time.LocalDate ld) {
            return "date".equals(type) ? ld.format(DateTimeFormatter.ISO_LOCAL_DATE) : ld.toString();
        }
        if (raw instanceof java.time.LocalDateTime ldt) {
            return "date".equals(type)
                    ? ldt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : ldt.toLocalDate().toString();
        }

        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }

        switch (type) {
            case "date":
                return parseDate(s);
            case "int": {
                String cleaned = NUMBER_CLEAN.matcher(s).replaceAll("");
                if (cleaned.isEmpty() || cleaned.equals("-")) {
                    return null;
                }
                try {
                    return Long.parseLong(cleaned);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "number": {
                String cleaned = NUMBER_CLEAN.matcher(s).replaceAll("");
                if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".") || cleaned.equals("-.")) {
                    return null;
                }
                try {
                    return Double.parseDouble(cleaned);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            default:
                return s;
        }
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日")
    };

    private String parseDate(String s) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate d = LocalDate.parse(s.trim(), fmt);
                return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        // 兼容 Excel 默认 "M/d/yy"（如 8/4/26）
        try {
            String[] p = s.split("/");
            if (p.length == 3) {
                int month = Integer.parseInt(p[0]);
                int day = Integer.parseInt(p[1]);
                int year = Integer.parseInt(p[2]);
                if (year < 100) {
                    year += 2000;
                }
                LocalDate d = LocalDate.of(year, month, day);
                return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception ignored) {
            // 解析失败返回 null
        }
        return null;
    }
}
