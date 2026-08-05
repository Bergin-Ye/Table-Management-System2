# -*- coding: utf-8 -*-
"""生成 10 张「可直接导入」的测试 Excel，按前端页面名命名，放入 E:\\code\\管理系统\\测试文档。

每个文件 = 字段配置(excelLabels 表头) + 覆盖多行/单行/自动编号/特殊字符/空单元格/失败行的测试数据。
"""
import os
import sys

from common import load_cfg
from gen_fake import build_fake_rows, write_fake

TYPES = ["cgsq", "cgdd", "xsdd", "xsck", "wgrk", "scld", "cprk", "qtck", "qtrk", "dbd"]
OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "测试文档")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for t in TYPES:
        cfg = load_cfg(t)
        _, rows, _, _, _ = build_fake_rows(cfg)
        # 用单据显示名作为文件名（与前端页面名一致）
        out = os.path.join(OUT_DIR, f"{cfg['name']}.xlsx")
        write_fake(cfg, rows, out)
        print(f"{t:6s} -> {cfg['name']}.xlsx  ({len(rows)} 数据行)")
    print("输出目录:", OUT_DIR)


if __name__ == "__main__":
    main()
