import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.erp.config.FieldConfig;
import com.erp.config.FieldConfig.FieldDef;
import com.erp.service.FieldConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/** 诊断：验证建议修复 —— 用 IdentityHashMap 做 used 去重，col[101] 是否映射到 detail 运费。 */
public class Diag3 {
    static Map<Integer, FieldDef> buildColumnMapFixed(FieldConfig cfg, Map<Integer, String> headMap) {
        List<FieldDef> pool = new ArrayList<>();
        pool.addAll(cfg.getHeadFields());
        pool.addAll(cfg.getDetailFields());
        Set<FieldDef> used = Collections.newSetFromMap(new IdentityHashMap<FieldDef, Boolean>());
        Map<Integer, FieldDef> result = new LinkedHashMap<>();
        List<Integer> indexes = new ArrayList<>(headMap.keySet());
        indexes.sort(Integer::compareTo);
        for (Integer idx : indexes) {
            String header = FieldConfigService.normalizeHeader(headMap.get(idx));
            if (header.isEmpty()) continue;
            for (FieldDef f : pool) {
                if (used.contains(f)) continue;
                String excel = FieldConfigService.normalizeHeader(f.getExcelLabel());
                String key = FieldConfigService.normalizeHeader(f.getKey());
                if (header.equals(excel) || header.equals(key)) {
                    used.add(f);
                    result.put(idx, f);
                    break;
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        String xlsx = args[0];
        String cfgJson = args[1];
        final Map<Integer, String> headMap = new HashMap<>();
        try (FileInputStream is = new FileInputStream(xlsx)) {
            EasyExcel.read(is).headRowNumber(1)
                    .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                        @Override public void invokeHeadMap(Map<Integer, String> h, AnalysisContext c) { headMap.putAll(h); }
                        @Override public void invoke(Map<Integer, String> d, AnalysisContext c) { }
                        @Override public void doAfterAllAnalysed(AnalysisContext c) { }
                    }).sheet().doRead();
        }
        ObjectMapper om = new ObjectMapper();
        FieldConfig cfg = om.readValue(new File(cfgJson), FieldConfig.class);

        Map<Integer, FieldDef> fixed = buildColumnMapFixed(cfg, headMap);
        System.out.println("FIXED colMap.size=" + fixed.size());
        for (Integer idx : new TreeSet<>(fixed.keySet())) {
            FieldDef f = fixed.get(idx);
            if (f.getKey().equals("运费") || f.getKey().equals("编号")) {
                System.out.println("  col[" + idx + "] -> key=" + f.getKey());
            }
        }
    }
}
