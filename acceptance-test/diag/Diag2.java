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

/** 诊断：用后端生产代码 buildColumnMap 复现列映射，检查 col[30]/col[101] 是否都映射。 */
public class Diag2 {
    public static void main(String[] args) throws Exception {
        String xlsx = args[0];
        String cfgJson = args[1];

        // 1) EasyExcel 读表头
        final Map<Integer, String> headMap = new HashMap<>();
        try (FileInputStream is = new FileInputStream(xlsx)) {
            EasyExcel.read(is)
                    .headRowNumber(1)
                    .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                        @Override public void invokeHeadMap(Map<Integer, String> h, AnalysisContext c) {
                            headMap.putAll(h);
                        }
                        @Override public void invoke(Map<Integer, String> d, AnalysisContext c) { }
                        @Override public void doAfterAllAnalysed(AnalysisContext c) { }
                    })
                    .sheet().doRead();
        }

        // 2) 加载字段配置
        ObjectMapper om = new ObjectMapper();
        FieldConfig cfg = om.readValue(new File(cfgJson), FieldConfig.class);
        FieldConfigService svc = new FieldConfigService(om);

        // 3) 调用生产 buildColumnMap
        Map<Integer, FieldDef> colMap = svc.buildColumnMap(cfg, headMap);

        Set<FieldDef> headSet = new HashSet<>(cfg.getHeadFields());
        System.out.println("headMap.size=" + headMap.size() + " colMap.size=" + colMap.size());
        for (Integer idx : new TreeSet<>(colMap.keySet())) {
            FieldDef f = colMap.get(idx);
            if (f.getKey().equals("运费") || f.getKey().equals("编号")) {
                System.out.println("col[" + idx + "]=" + headMap.get(idx) + " -> key=" + f.getKey()
                        + " 区段=" + (headSet.contains(f) ? "HEAD" : "DETAIL"));
            }
        }
        System.out.println("col101 mapped? " + (colMap.containsKey(101) ? colMap.get(101).getKey() : "NOT-MAPPED"));

        // 调试：找出池中所有与 col[101] 表头匹配的字段及其 used 状态
        String h101 = FieldConfigService.normalizeHeader(headMap.get(101));
        List<FieldDef> pool = new ArrayList<>();
        pool.addAll(cfg.getHeadFields());
        pool.addAll(cfg.getDetailFields());
        Set<FieldDef> used = new HashSet<>(colMap.values());
        for (FieldDef f : pool) {
            String ex = FieldConfigService.normalizeHeader(f.getExcelLabel());
            String kk = FieldConfigService.normalizeHeader(f.getKey());
            if (h101.equals(ex) || h101.equals(kk)) {
                System.out.println("  pool匹配 col101: key=" + f.getKey()
                        + " 区段=" + (headSet.contains(f) ? "HEAD" : "DETAIL")
                        + " used=" + used.contains(f));
            }
        }
    }
}
