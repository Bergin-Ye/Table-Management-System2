import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 诊断：打印 headMap 全量 + 数据行在 运费/编号 列的取值，确定列映射缺失原因。 */
public class EzDiag {
    public static void main(String[] args) throws Exception {
        String file = args[0];
        String out = args[1];
        PrintStream ps = new PrintStream(out, StandardCharsets.UTF_8);
        int[] rowCount = {0};
        try (FileInputStream is = new FileInputStream(file)) {
            EasyExcel.read(is)
                    .headRowNumber(1)
                    .registerReadListener(new AnalysisEventListener<Map<Integer, String>>() {
                        @Override
                        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                            ps.println("HEADMAP size=" + headMap.size());
                            for (Integer k : new TreeSet<>(headMap.keySet())) {
                                ps.println("  col[" + k + "]=" + headMap.get(k));
                            }
                        }
                        @Override
                        public void invoke(Map<Integer, String> data, AnalysisContext context) {
                            rowCount[0]++;
                            if (rowCount[0] <= 3) {
                                ps.println("ROW" + rowCount[0] + " 30=" + data.get(30) + " | 101=" + data.get(101)
                                        + " | 29=" + data.get(29) + " | 100=" + data.get(100));
                            }
                        }
                        @Override
                        public void doAfterAllAnalysed(AnalysisContext context) { }
                    })
                    .sheet()
                    .doRead();
        }
        ps.println("totalRows=" + rowCount[0]);
        ps.close();
    }
}
