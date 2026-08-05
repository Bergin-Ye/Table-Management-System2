# -*- coding: utf-8 -*-
"""Excel 假数据往返测试：导入 → 字段值校验 → 导出 → 与原 Excel 逐字段比对 → 再次导入还原。

脚本幂等：开始前清理前次遗留的测试单据（编号前缀 CG-RT/IMP/D0），不动库内其他数据。
"""
import json
import os
import sys

import re

from common import (login, api_list, api_detail, api_import, api_export,
                    api_delete, norm, read_sheet, ART, load_cfg)
from gen_fake import build_fake_rows, write_fake


def is_test_bizno(b):
    """识别本次验收测试产生的单据编号（含早期 bug 版生成器遗留的 Dxx r 模式）。"""
    return bool(re.match(r"^(CG-RT|IMP|D\d+r\d+)", b or ""))


def expected_docs(cfg, rows):
    """镜像后端分组逻辑，构造期望单据。rows = [(head, detail), ...]"""
    docs = []
    by_no = {}
    auto = 0
    for head, detail in rows:
        biz = str(head.get("编号") or "").strip()
        if not biz:
            auto += 1
            docs.append({"biz_no": f"__AUTO__{auto}", "head": head, "details": [detail]})
        else:
            if biz not in by_no:
                by_no[biz] = {"biz_no": biz, "head": head, "details": []}
                docs.append(by_no[biz])
            g = by_no[biz]
            for k, v in head.items():
                if v and not g["head"].get(k):
                    g["head"][k] = v
            g["details"].append(detail)
    return docs


def verify_docs(token, doc_type, expected):
    """逐单据核对 DB 中的 head/detail 字段值。返回 (passed, failures)"""
    failures = []
    for exp in expected:
        if exp["biz_no"].startswith("__AUTO__"):
            page = api_list(token, doc_type, keyword="IMP", size=50)
            found = next((rec for rec in page.get("records", [])
                          if str(rec.get("bizNo") or "").startswith("IMP")), None)
            if found is None:
                failures.append({"doc": exp["biz_no"], "why": "未找到 IMP 自动编号单据"})
                continue
            biz = found["bizNo"]
        else:
            biz = exp["biz_no"]
            page = api_list(token, doc_type, keyword=biz, size=10)
            recs = [r for r in page.get("records", []) if r["bizNo"] == biz]
            if not recs:
                failures.append({"doc": biz, "why": "列表未找到"})
                continue
            found = recs[0]

        det = api_detail(token, doc_type, found["id"])
        hd = det.get("head") or {}
        d_rows = [r.get("detail") or {} for r in det.get("details") or []]

        for k, v in exp["head"].items():
            if not str(v).strip():
                continue
            got = hd.get(k)
            if norm(got) != norm(v):
                failures.append({"doc": biz, "field": f"head.{k}",
                                 "expect": norm(v), "got": norm(got)})
        if len(d_rows) != len(exp["details"]):
            failures.append({"doc": biz, "field": "detail行数",
                             "expect": len(exp["details"]), "got": len(d_rows)})
        for i, exp_d in enumerate(exp["details"]):
            if i >= len(d_rows):
                break
            for k, v in exp_d.items():
                if not str(v).strip():
                    continue
                got = d_rows[i].get(k)
                if norm(got) != norm(v):
                    failures.append({"doc": biz, "field": f"detail[{i}].{k}",
                                     "expect": norm(v), "got": norm(got)})
    return len(failures) == 0, failures


def compare_sheets(orig_path, export_path, cfg, n_success_rows):
    """逐单元格比对原始文件与导出文件（仅前 n_success_rows 数据行参与，失败行不落库）。"""
    orig = read_sheet(orig_path)
    expo = read_sheet(export_path)
    h_labels = [f["excelLabel"] for f in cfg["headFields"]]
    d_labels = [f["excelLabel"] for f in cfg["detailFields"]]
    labels = h_labels + d_labels

    o_rows = orig[1:1 + n_success_rows]  # 排除失败行
    # 导出文件可能含库内既有的非测试单据（如 E2E-*），只保留本次测试单据行
    bh_col = next((i for i, L in enumerate(labels) if L.replace(" ", "") == "编号"), None)
    x_rows = []
    for r in expo[1:]:
        if bh_col is not None and str(r[bh_col] or "").strip() != "" and \
           not re.match(r"^(CG-RT|IMP)", str(r[bh_col]).strip()):
            continue
        x_rows.append(r)
    diffs = []
    auto_expected = 0
    checked = 0
    for ri in range(min(len(o_rows), len(x_rows))):
        o, x = o_rows[ri], x_rows[ri]
        for ci in range(min(len(o), len(x))):
            if ci >= len(labels):
                break
            ov, xv = norm(o[ci]), norm(x[ci])
            is_head_bizno = ci < len(h_labels) and labels[ci].replace(" ", "") == "编号"
            checked += 1
            if ov == xv:
                continue
            if is_head_bizno and ov == "" and xv.startswith("IMP"):
                auto_expected += 1  # 空编号 → 自动回退编号（预期差异）
                continue
            diffs.append({"row": ri + 2, "col": labels[ci], "orig": ov, "export": xv})
    match = (len(o_rows) == len(x_rows) and not diffs)
    stats = {
        "orig_rows": len(o_rows), "export_rows": len(x_rows),
        "cells_checked": checked, "expected_auto_imp_diff": auto_expected,
        "unexpected_diffs": len(diffs),
    }
    return match, diffs, stats


def run(doc_type="cgdd"):
    cfg = load_cfg(doc_type)
    token = login()
    result = {"doc_type": doc_type, "steps": {}}

    # ---- 幂等预清理：删除前次运行遗留的测试单据（CG-RT/IMP/D0），保留库内其他数据 ----
    page = api_list(token, doc_type, size=500)
    stale = [r["id"] for r in page.get("records", [])
             if is_test_bizno(str(r.get("bizNo") or ""))]
    for i in stale:
        api_delete(token, doc_type, i)
    result["pre_clean_deleted"] = len(stale)

    # ---- 生成假文件 ----
    cols, rows, n_success, expect, fail_expect = build_fake_rows(cfg)
    fake_path = os.path.join(ART, f"{doc_type}_fake.xlsx")
    write_fake(cfg, rows, fake_path)
    exp_docs = expected_docs(cfg, rows[:n_success])
    result["steps"]["generate"] = {"file": fake_path, "data_rows": len(rows),
                                   "success_rows": n_success, "expect_docs": len(exp_docs),
                                   "fail_expect": fail_expect}

    # ---- 基线 ----
    base = api_list(token, doc_type, size=1)
    result["baseline_total"] = base.get("total", 0)

    # ---- 导入 ----
    code, msg, imp = api_import(token, doc_type, fake_path)
    result["steps"]["import"] = {"code": code, "message": msg, "data": imp}
    if code != 0:
        return result

    # ---- 字段值校验（API 列表+详情） ----
    ok, failures = verify_docs(token, doc_type, exp_docs)
    result["steps"]["verify_db"] = {"pass": ok, "failures": failures}

    # ---- 导出全部 ----
    export_path, cd = api_export(token, doc_type, out_path=os.path.join(ART, f"{doc_type}_export.xlsx"))
    result["steps"]["export"] = {"path": export_path, "content_disposition": cd}

    # ---- 与原文件逐字段比对 ----
    match, diffs, stats = compare_sheets(fake_path, export_path, cfg, n_success)
    result["steps"]["compare_orig"] = {"match": match, "diffs": diffs, "stats": stats}

    # ---- 导出文件再次导入（先仅清掉本次导入的 CG-RT/IMP 记录） ----
    page2 = api_list(token, doc_type, size=500)
    ids_to_del = [r["id"] for r in page2.get("records", [])
                  if is_test_bizno(str(r.get("bizNo") or ""))]
    del_result = [api_delete(token, doc_type, i) for i in ids_to_del]
    result["steps"]["cleanup_before_reimport"] = {"deleted": len(ids_to_del),
                                                  "results": del_result}

    code2, msg2, imp2 = api_import(token, doc_type, export_path)
    result["steps"]["reimport"] = {"code": code2, "message": msg2, "data": imp2}

    # 再次核对（导出文件行是列表 → 拆成 (head, detail) 元组）
    hkeys = [f["key"] for f in cfg["headFields"]]
    dkeys = [f["key"] for f in cfg["detailFields"]]
    rows2 = []
    for r in read_sheet(export_path)[1:]:
        rows2.append((dict(zip(hkeys, r[:len(hkeys)])),
                      dict(zip(dkeys, r[len(hkeys):len(hkeys) + len(dkeys)]))))
    exp2 = expected_docs(cfg, rows2)
    ok2, failures2 = verify_docs(token, doc_type, exp2)
    result["steps"]["verify_after_reimport"] = {"pass": ok2, "failures": failures2}

    return result


def main():
    doc_type = sys.argv[1] if len(sys.argv) > 1 else "cgdd"
    res = run(doc_type)
    out = os.path.join(ART, f"{doc_type}_roundtrip_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=2)
    print(json.dumps(res, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
