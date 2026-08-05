# -*- coding: utf-8 -*-
"""边界测试：空Excel、表头缺列、编号重复、超大文件、空文件、非法docType、401/403（已含冒烟）。"""
import io
import json
import os
import re
import time

import requests
from openpyxl import Workbook

from common import login, api_list, api_delete, ART, load_cfg

BASE = "http://localhost:8080"
RESULTS = []


def record(item, ok, detail=""):
    RESULTS.append({"item": item, "pass": bool(ok), "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {item} {detail}")


def do_import(token, doc_type, file_bytes, fname, timeout=300):
    r = requests.post(BASE + f"/api/doc/{doc_type}/import",
                      files={"file": (fname, io.BytesIO(file_bytes),
                                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")},
                      headers={"Authorization": "Bearer " + token}, timeout=timeout)
    try:
        return r.json()
    except Exception:
        return {"code": -1, "message": f"http {r.status_code}", "raw": r.text[:200]}


def build_wb(header, rows):
    wb = Workbook()
    ws = wb.active
    ws.append(header)
    for r in rows:
        ws.append(r)
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


def main():
    token = login()
    cfg = load_cfg("cgdd")
    hkeys = [f["key"] for f in cfg["headFields"]]
    dkeys = [f["key"] for f in cfg["detailFields"]]
    hd_label = [f["excelLabel"] for f in cfg["headFields"]] + [f["excelLabel"] for f in cfg["detailFields"]]
    hd_keys = hkeys + dkeys

    # 清理本次测试单据
    lst = api_list(token, "cgdd", size=500)
    for rec in lst.get("records", []):
        if re.match(r"^(CG-RT|IMP|BND-)", str(rec.get("bizNo") or "")):
            api_delete(token, "cgdd", rec["id"])

    # ---------- 1. 空 Excel（仅表头，无数据行） ----------
    empty = build_wb(hd_label, [])
    j = do_import(token, "cgdd", empty, "empty.xlsx", timeout=120)
    ok = j.get("code") == 0 and j["data"]["totalRows"] == 0 and j["data"]["successDocs"] == 0
    record("空Excel(仅表头) 返回0行0成功", ok, json.dumps(j, ensure_ascii=False))

    # ---------- 2. 表头缺列（缺"编号"与"日期"列 → 每行按自动编号，日期空则失败） ----------
    # 构造：只保留"供应商"和"数量"两列
    sub_idx = [hd_label.index("供应商"), hd_label.index("数量")]
    sub_hdr = [hd_label[i] for i in sub_idx]
    rows = [["边界客户A", 1], ["边界客户B", 2]]
    wb = build_wb(sub_hdr, rows)
    j = do_import(token, "cgdd", wb, "nocol.xlsx", timeout=120)
    imp = j.get("data") or {}
    auto_ok = imp.get("successDocs") == 0 and any("日期不能为空" in (f.get("reason") or "") for f in imp.get("failRows", []))
    record("缺编号列→自动回退+缺日期失败(无日期列全部失败)", auto_ok,
           json.dumps(j, ensure_ascii=False))

    # 缺"编号"但保留"日期" → 每行自动编号成单
    sub_idx2 = [hd_label.index("供应商"), hd_label.index("日期")]
    sub_hdr2 = [hd_label[i] for i in sub_idx2]
    rows2 = [["边界客户C", "2026-08-04"], ["边界客户D", "2026-08-05"]]
    wb = build_wb(sub_hdr2, rows2)
    j = do_import(token, "cgdd", wb, "nodate.xlsx", timeout=120)
    imp = j.get("data") or {}
    record("缺编号列含日期→自动IMP编号成单", imp.get("successDocs") == 2,
           json.dumps(j, ensure_ascii=False))

    # 清理自动编号单据
    lst = api_list(token, "cgdd", keyword="IMP", size=200)
    for rec in lst.get("records", []):
        api_delete(token, "cgdd", rec["id"])

    # ---------- 3. 编号重复：库中已有则整组跳过 ----------
    # 先导入一个 BND-DUP 单据
    h = {"编号": "BND-DUP-1", "日期": "2026-08-04", "供应商": "重复测试"}
    rows3 = [[hd_label[0], "2026-08-04", "BND-DUP-1"]]  # 用 index 直接构造太繁琐，改用完整行
    # 完整一行：供应商/日期/编号/数量
    full_row = [""] * len(hd_label)
    full_row[hd_label.index("供应商")] = "重复测试A"
    full_row[hd_label.index("日期")] = "2026-08-04"
    full_row[hd_label.index("编    号")] = "BND-DUP-1"
    full_row[hd_label.index("数量")] = 5
    wb = build_wb(hd_label, [full_row])
    j = do_import(token, "cgdd", wb, "dup1.xlsx", timeout=120)
    first_ok = j.get("data", {}).get("successDocs") == 1

    # 再次导入相同编号（2 行）→ 整组跳过
    rows4 = []
    for i in range(2):
        r = [""] * len(hd_label)
        r[hd_label.index("供应商")] = "重复测试B"
        r[hd_label.index("日期")] = "2026-08-04"
        r[hd_label.index("编    号")] = "BND-DUP-1"
        r[hd_label.index("数量")] = i + 1
        rows4.append(r)
    wb = build_wb(hd_label, rows4)
    j = do_import(token, "cgdd", wb, "dup2.xlsx", timeout=120)
    imp = j.get("data") or {}
    skip_ok = imp.get("successDocs") == 0 and all("编号已存在" in (f.get("reason") or "") for f in imp.get("failRows", []))
    record("编号重复整组跳过并报编号已存在", first_ok and skip_ok, json.dumps(j, ensure_ascii=False))
    # 清理
    lst = api_list(token, "cgdd", keyword="BND-DUP-1", size=10)
    for rec in lst.get("records", []):
        api_delete(token, "cgdd", rec["id"])

    # ---------- 4. 超大文件：5000 行 ----------
    big_ts = str(int(time.time()))
    big_rows = []
    for i in range(5000):
        r = [""] * len(hd_label)
        r[hd_label.index("供应商")] = f"批量供应商{i:05d}"
        r[hd_label.index("日期")] = "2026-08-04"
        r[hd_label.index("编    号")] = f"BND-BIG-{big_ts}-{i:05d}"
        r[hd_label.index("数量")] = (i % 100) + 1
        big_rows.append(r)
    wb = build_wb(hd_label, big_rows)
    t0 = time.time()
    j = do_import(token, "cgdd", wb, "big5000.xlsx", timeout=600)
    elapsed = time.time() - t0
    imp = j.get("data") or {}
    big_ok = j.get("code") == 0 and imp.get("successDocs") == 5000 and not imp.get("failRows")
    record(f"超大文件5000行导入成功(耗时{elapsed:.1f}s)", big_ok, json.dumps(j, ensure_ascii=False)[:200])

    # 验证列表能查询到
    lst = api_list(token, "cgdd", keyword=f"BND-BIG-{big_ts}-00000", size=5)
    record("大文件后模糊搜索可命中",
           any(rec.get("bizNo") == f"BND-BIG-{big_ts}-00000" for rec in lst.get("records", [])))
    # 清理
    lst = api_list(token, "cgdd", keyword="BND-BIG", size=500)
    for rec in lst.get("records", []):
        api_delete(token, "cgdd", rec["id"])

    # ---------- 5. 空字节文件 / 非 Excel ----------
    r = requests.post(BASE + "/api/doc/cgdd/import",
                      files={"file": ("a.txt", io.BytesIO(b"hello"), "text/plain")},
                      headers={"Authorization": "Bearer " + token}, timeout=60)
    j = r.json()
    record("非Excel文件导入返回业务错误", j.get("code") != 0, json.dumps(j, ensure_ascii=False)[:120])

    # ---------- 6. 非法 docType → 404 ----------
    r = requests.get(BASE + "/api/meta/notexist", headers={"Authorization": "Bearer " + token}, timeout=10)
    record("不存在docType的meta返回404", r.status_code == 404, f"http={r.status_code}")

    out = os.path.join(ART, "boundary_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(RESULTS, f, ensure_ascii=False, indent=2)
    passed = sum(1 for x in RESULTS if x["pass"])
    print(f"\n=== 边界汇总: {passed}/{len(RESULTS)} 通过 ===")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
