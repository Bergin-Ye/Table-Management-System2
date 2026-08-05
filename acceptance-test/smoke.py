# -*- coding: utf-8 -*-
"""功能冒烟测试：10 单据 CRUD、全局模糊搜索、列偏好独立性、RBAC、401/403、菜单树。"""
import json
import os
import time

from common import login, api_list, api_detail, load_cfg, BASE
import requests

TYPES = ["cgdd", "cgsq", "xsdd", "xsck", "wgrk", "scld", "cprk", "qtck", "qtrk", "dbd"]
RESULTS = []
TS = str(int(time.time()))


def record(item, ok, detail=""):
    RESULTS.append({"item": item, "pass": bool(ok), "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {item} {detail}")


def hdr(token, json=True):
    return {"Authorization": "Bearer " + token, **({"Content-Type": "application/json"} if json else {})}


def main():
    admin = login("admin", "admin123")
    user1 = login("user1", "123456")

    # ========== 1. 未登录 401 ==========
    r = requests.get(BASE + "/api/doc/cgdd", headers={"Content-Type": "application/json"}, timeout=10)
    record("未登录访问单据接口返回 401", r.status_code == 401, f"http={r.status_code}")
    r2 = requests.get(BASE + "/api/auth/userinfo", timeout=10)
    record("未登录访问 userinfo 返回 401", r2.status_code == 401, f"http={r2.status_code}")

    # ========== 2. 登录态 ==========
    r = requests.get(BASE + "/api/auth/userinfo", headers=hdr(admin), timeout=10)
    rec = r.json().get("data") or {}
    record("admin userinfo 角色=ADMIN", rec.get("role") == "ADMIN", json.dumps(rec, ensure_ascii=False))
    r = requests.get(BASE + "/api/auth/userinfo", headers=hdr(user1), timeout=10)
    rec = r.json().get("data") or {}
    record("user1 userinfo 角色=USER", rec.get("role") == "USER", json.dumps(rec, ensure_ascii=False))

    # ========== 3. 菜单树 / RBAC ==========
    r = requests.get(BASE + "/api/menu/mine", headers=hdr(admin), timeout=10)
    menus = r.json().get("data") or []
    admin_roots = {m["name"] for m in menus}
    record("admin 菜单含 4 个顶级", admin_roots == {"销售管理", "采购管理", "库存管理", "系统管理"},
           f"roots={admin_roots}")
    sys_menu = next((m for m in menus if m["name"] == "系统管理"), None)
    record("admin 系统管理含子菜单", bool(sys_menu and sys_menu.get("children")),
           json.dumps(sys_menu, ensure_ascii=False))
    record("admin 业务父菜单含单据子菜单",
           all(any(c["docType"] for c in m.get("children", [])) for m in menus if m["name"] in ("销售管理", "采购管理", "库存管理")),
           json.dumps([{ "root": m["name"], "children": [c["name"] for c in m.get("children", [])] } for m in menus], ensure_ascii=False))

    r = requests.get(BASE + "/api/menu/mine", headers=hdr(user1), timeout=10)
    menus1 = r.json().get("data") or []
    user_roots = {m["name"] for m in menus1}
    record("user1 菜单不含系统管理", "系统管理" not in user_roots, f"roots={user_roots}")
    record("user1 菜单含 3 个业务顶级", user_roots == {"销售管理", "采购管理", "库存管理"})

    # user1 直调 ADMIN 接口 → 403
    for url in ["/api/user/list", "/api/role/menus?role=USER"]:
        r = requests.get(BASE + url, headers=hdr(user1), timeout=10)
        record(f"user1 访问 {url} → 403", r.status_code == 403, f"http={r.status_code}")
    # admin 访问正常
    r = requests.get(BASE + "/api/user/list", headers=hdr(admin), timeout=10)
    record("admin 访问 /api/user/list → 200", r.status_code == 200, f"http={r.status_code}")

    # user1 对单据接口有权限（docType 在 USER 菜单内）
    r = requests.get(BASE + "/api/meta/cgdd", headers=hdr(user1), timeout=10)
    record("user1 访问 /api/meta/cgdd → 200", r.status_code == 200, f"http={r.status_code}")

    # ========== 4. 十种单据 CRUD ==========
    for t in TYPES:
        cfg = load_cfg(t)
        hkeys = [f["key"] for f in cfg["headFields"]]
        dkeys = [f["key"] for f in cfg["detailFields"]]
        biz = f"SMK-{t}-{TS[-6:]}"
        head = {"编号": biz, "日期": "2026-08-04"}
        for k in hkeys:
            if k not in head:
                head[k] = "冒烟测试" if any(f["type"] != "number" for f in cfg["headFields"] if f["key"] == k) else 1
        details = []
        for r_i in range(2):
            d = {}
            for k in dkeys:
                ft = next(f["type"] for f in cfg["detailFields"] if f["key"] == k)
                if ft == "int":
                    d[k] = r_i + 1
                elif ft == "number":
                    d[k] = 5.5
                elif ft == "date":
                    d[k] = "2026-09-01"
                else:
                    d[k] = f"明细{r_i}测试"
            details.append({"rowNo": r_i + 1, "detail": d})

        r = requests.post(BASE + f"/api/doc/{t}", json={"head": head, "details": details}, headers=hdr(admin), timeout=10)
        j = r.json()
        if j.get("code") != 0:
            record(f"{t} 新增", False, json.dumps(j, ensure_ascii=False))
            continue
        did = j["data"]
        # 查看
        r = requests.get(BASE + f"/api/doc/{t}/{did}", headers=hdr(admin), timeout=10)
        det = r.json().get("data") or {}
        ok_detail = len(det.get("details") or []) == 2 and (det.get("head") or {}).get("编号") == biz
        # 列表含该单
        lst = api_list(admin, t, keyword=biz, size=10)
        in_list = any(rec["bizNo"] == biz for rec in lst.get("records", []))
        # 编辑
        new_biz = biz + "-E"
        head2 = dict(head); head2["编号"] = new_biz
        r = requests.put(BASE + f"/api/doc/{t}/{did}", json={"head": head2, "details": details}, headers=hdr(admin), timeout=10)
        upd_ok = r.json().get("code") == 0
        # 删除
        r = requests.delete(BASE + f"/api/doc/{t}/{did}", headers=hdr(admin), timeout=10)
        del_ok = r.json().get("code") == 0
        lst2 = api_list(admin, t, keyword=new_biz, size=10)
        gone = not any(rec["bizNo"] == new_biz for rec in lst2.get("records", []))
        record(f"{t} 新增/查看/列表/编辑/删除", ok_detail and in_list and upd_ok and del_ok and gone,
               f"did={did} detailRows={len(det.get('details') or [])} in_list={in_list} upd={upd_ok} del={del_ok} gone={gone}")

    # ========== 5. 全局模糊搜索 ==========
    # 头部字段命中 + 明细字段命中（整单返回）
    t = "cgdd"
    cfg = load_cfg(t)
    hbiz = f"SRCH-{TS[-6:]}"
    head = {"编号": hbiz, "日期": "2026-08-04", "供应商": "搜索测试客户-华东", "业务员": "SearchMan77"}
    details = [{"rowNo": 1, "detail": {"物料名称": "SearchDetailAlpha-9988", "数量": 9, "行号": 1}}]
    r = requests.post(BASE + f"/api/doc/{t}", json={"head": head, "details": details}, headers=hdr(admin), timeout=10)
    sid = r.json()["data"]
    # 搜头部值
    lst = api_list(admin, t, keyword="SearchMan77", size=10)
    hit_head = any(rec["id"] == sid for rec in lst.get("records", []))
    # 搜明细值
    lst = api_list(admin, t, keyword="SearchDetailAlpha-9988", size=10)
    hit_detail = any(rec["id"] == sid for rec in lst.get("records", []))
    record("模糊搜索命中头部字段值", hit_head)
    record("模糊搜索命中明细行字段值(返回整单)", hit_detail)
    record("模糊搜索返回整单 detailCount", hit_detail and next((rec["detailCount"] for rec in lst.get("records", []) if rec["id"] == sid), None) == 1,
           f"detailCount={next((rec['detailCount'] for rec in lst.get('records', []) if rec['id']==sid), None)}")
    requests.delete(BASE + f"/api/doc/{t}/{sid}", headers=hdr(admin), timeout=10)

    # ========== 6. 列偏好：每用户独立 ==========
    pref_cols = ["编号", "日期", "供应商", "业务员"]
    r = requests.put(BASE + "/api/doc/cgdd/pref", json={"columns": pref_cols}, headers=hdr(admin), timeout=10)
    save_ok = r.json().get("code") == 0
    r = requests.get(BASE + "/api/doc/cgdd/pref", headers=hdr(admin), timeout=10)
    read_back = r.json().get("data")
    same = read_back == pref_cols
    # 换 user1：应为默认列（独立）
    r = requests.get(BASE + "/api/doc/cgdd/pref", headers=hdr(user1), timeout=10)
    user1_pref = r.json().get("data")
    is_default = (user1_pref or []) == (cfg.get("defaultColumns") or [])
    record("admin 保存列偏好并读回一致", save_ok and same, f"pref={read_back}")
    record("user1 列偏好独立(返回默认列)", is_default, f"user1_pref={user1_pref}")
    # 清理 admin 偏好（避免影响后续）
    requests.put(BASE + "/api/doc/cgdd/pref", json={"columns": cfg.get("defaultColumns")}, headers=hdr(admin), timeout=10)

    # ========== 7. 越权：user1 直接操作单据（有权限，应 200） ==========
    r = requests.get(BASE + "/api/meta/xsdd", headers=hdr(user1), timeout=10)
    record("user1 有权限 docType meta → 200", r.status_code == 200, f"http={r.status_code}")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "artifacts", "smoke_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"results": RESULTS}, f, ensure_ascii=False, indent=2)
    passed = sum(1 for x in RESULTS if x["pass"])
    print(f"\n=== 冒烟汇总: {passed}/{len(RESULTS)} 通过 ===")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
