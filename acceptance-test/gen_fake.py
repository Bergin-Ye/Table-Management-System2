# -*- coding: utf-8 -*-
"""生成假数据 Excel：覆盖同编号多行/单行/空编号(自动回退)/特殊字符/空单元格/失败行。

head/detail 分开存放，不合并键（cgdd 等配置中 head 与 detail 都有「编号」）。
"""
import json
import os
import sys
from openpyxl import Workbook

from common import load_cfg, ART


def gen_head_values(cfg, doc_idx, key):
    """按字段 type 生成确定性的头部字段值（doc_idx 区分单据）。"""
    for f in cfg["headFields"]:
        if f["key"] == key:
            t = f["type"]
            i = doc_idx
            if t == "date":
                return f"2026-08-{(i * 3) % 27 + 1:02d}"
            if t == "number":
                return float(i) + 0.5 if i % 2 else float(i)
            if t == "int":
                return i * 7
            special = {"供应商": "深圳华科电子有限公司",
                       "部门": "采购一部",
                       "业务员": "ZhangSan",
                       "制单": "LiSi",
                       "制单机构": "华南分公司",
                       "摘要": "特殊字符测试-_.、（）%",
                       "交货地": "上海-浦东_园区",
                       "联系人": "王五（经理）",
                       "联系电话": "138-0000-1234",
                       "采购方式": "公开询价_2026",
                       "结算方式": "月结30天",
                       "云仓单号": "YC-2026-0001",
                       "业务类别": "常规采购%",
                       "汇率": "7.15"}
            return special.get(key, f"H{i:02d}-{key}")
    return None


def gen_detail_values(cfg, doc_idx, row_idx, key):
    for f in cfg["detailFields"]:
        if f["key"] == key:
            t = f["type"]
            i = doc_idx
            if t == "date":
                return f"2026-09-{((i + row_idx) * 2) % 27 + 1:02d}"
            if t == "number":
                if key == "数量":
                    return [10.5, 3, 2.5][row_idx % 3]
                if key == "单价":
                    return 12.8
                if key == "金额":
                    return round(134.4, 2)
                if key in ("税率(%)", "折扣率(%)"):
                    return 13
                return float(i) + row_idx * 0.25
            if t == "int":
                if key == "行号":
                    return row_idx + 1
                return i * 10 + row_idx
            special = {"物料代码": f"MAT-{1000 + i}",
                       "物料名称": f"集成电路-芯片A_批次{row_idx}",
                       "规格型号": "SS8550",
                       "单位": "pcs",
                       "备注": f"备注-中文（{i}）_.%",
                       "对应代码": f"CODE-{i}",
                       "对应名称": "对应物料名称",
                       "机型": f"MODEL-{i}",
                       "问题描述": "外观检查（ok）",
                       "源单单号": f"SRC-{i}-{row_idx}",
                       "辅助单位": "箱",
                       "商品类别": "电子元件",
                       "商品品牌": "Example"}
            return special.get(key, f"D{i:02d}r{row_idx}-{key}")
    return None


def build_fake_rows(cfg):
    """生成 (head_dict, detail_dict) 数据行列表。返回 (cols, rows, n_success, expect, fail_expect)。"""
    hkeys = [f["key"] for f in cfg["headFields"]]
    dkeys = [f["key"] for f in cfg["detailFields"]]
    cols = hkeys + dkeys

    def row_for(biz_no, doc_idx, row_idx, detail_overrides=None, head_overrides=None,
                blank_keys=()):
        head = {}
        for k in hkeys:
            if k == "编号":
                head[k] = biz_no
                continue
            if k in (head_overrides or {}):
                head[k] = head_overrides[k]
                continue
            v = gen_head_values(cfg, doc_idx, k)
            head[k] = "" if k in blank_keys else v
        detail = {}
        for k in dkeys:
            if k in (detail_overrides or {}):
                detail[k] = detail_overrides[k]
                continue
            v = gen_detail_values(cfg, doc_idx, row_idx, k)
            detail[k] = "" if k in blank_keys else v
        return head, detail

    rows = []
    expect = []

    # Doc A: 同编号 3 行明细（CG-RT-001）
    doc_idx = 1
    for r in range(3):
        rows.append(row_for("CG-RT-001", doc_idx, r))
    expect.append(("CG-RT-001", 1, 3))

    # Doc B~G: 单行单据
    for i in range(2, 8):
        rows.append(row_for(f"CG-RT-00{i}", i, 0))
        expect.append((f"CG-RT-00{i}", i, 1))

    # Doc H: 空编号行 → 自动回退 IMP
    rows.append(row_for("", 8, 0))
    expect.append(("__AUTO__", 8, 1))

    # Doc I: 特殊字符行（编号含特殊字符）
    rows.append(row_for("CG-RT-008-%.（）", 9, 0))
    expect.append(("CG-RT-008-%.（）", 9, 1))

    # Doc J: 空单元格覆盖
    rows.append(row_for("CG-RT-009", 10, 0, blank_keys=("供应商", "数量", "单价")))
    expect.append(("CG-RT-009", 10, 1))

    # 失败行 1：数量写非法数字 "abc" → 数量格式错误
    rows.append(row_for("CG-RT-ERR1", 11, 0, detail_overrides={"数量": "abc"}))
    # 失败行 2：日期为空 → 日期不能为空
    rows.append(row_for("CG-RT-ERR2", 12, 0, head_overrides={"日期": ""}))

    fail_expect = ["数量格式错误", "日期不能为空"]
    # 若该单据无「数量」明细字段，则"数量=abc"失败行不会失败（覆盖值不写入文件）→ 成为成功单
    has_qty = any(f["key"] == "数量" for f in cfg["detailFields"])
    n_success = len(rows) - 2 + (0 if has_qty else 1)
    return cols, rows, n_success, expect, fail_expect


def write_fake(cfg, rows, out_path):
    wb = Workbook()
    ws = wb.active
    ws.title = cfg["name"]
    hfields = cfg["headFields"]
    dfields = cfg["detailFields"]
    header = [f["excelLabel"] for f in hfields] + [f["excelLabel"] for f in dfields]
    ws.append(header)
    hkeys = [f["key"] for f in hfields]
    dkeys = [f["key"] for f in dfields]
    for head, detail in rows:
        row = [head.get(k, "") for k in hkeys] + [detail.get(k, "") for k in dkeys]
        ws.append(row)
    wb.save(out_path)
    return out_path


def main(doc_type="cgdd"):
    cfg = load_cfg(doc_type)
    cols, rows, n_success, expect, fail_expect = build_fake_rows(cfg)
    out = os.path.join(ART, f"{doc_type}_fake.xlsx")
    write_fake(cfg, rows, out)
    meta = {
        "doc_type": doc_type,
        "name": cfg["name"],
        "data_rows": len(rows),
        "success_rows": n_success,
        "expect_success_docs": len(expect),
        "expected": [e[0] for e in expect],
        "expected_auto_imp": sum(1 for e in expect if e[0] == "__AUTO__"),
        "fail_expect": fail_expect,
    }
    print(json.dumps(meta, ensure_ascii=False, indent=2))
    return out, expect, fail_expect


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "cgdd")
