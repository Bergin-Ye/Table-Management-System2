# -*- coding: utf-8 -*-
"""运行全部单据类型的往返测试，汇总结果矩阵。"""
import json
import subprocess
import sys
from common import ART

TYPES = ["cgdd", "cgsq", "xsdd", "xsck", "wgrk", "scld", "cprk", "qtck", "qtrk", "dbd"]


def run_one(t):
    p = subprocess.run([sys.executable, "roundtrip.py", t],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    raw = p.stdout
    start = raw.find("{")
    if start < 0:
        return {"doc_type": t, "script_error": raw[:300], "rc": p.returncode}
    d = json.loads(raw[start:])
    return d


def main():
    summary = []
    for t in TYPES:
        d = run_one(t)
        imp = d.get("steps", {}).get("import", {}).get("data") or {}
        row = {
            "doc_type": t,
            "import_success": imp.get("successDocs"),
            "import_fail": imp.get("failRows"),
            "verify_db_pass": d.get("steps", {}).get("verify_db", {}).get("pass"),
            "compare_match": d.get("steps", {}).get("compare_orig", {}).get("match"),
            "compare_stats": d.get("steps", {}).get("compare_orig", {}).get("stats"),
            "reimport_success": d.get("steps", {}).get("reimport", {}).get("data", {}).get("successDocs"),
            "verify_after_reimport_pass": d.get("steps", {}).get("verify_after_reimport", {}).get("pass"),
            "verify_failures_head": d.get("steps", {}).get("verify_db", {}).get("failures", [])[:3],
        }
        summary.append(row)
        print(json.dumps(row, ensure_ascii=False))

    out = os.path.join(ART, "all_roundtrip_summary.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("saved", out)


if __name__ == "__main__":
    import os
    main()
