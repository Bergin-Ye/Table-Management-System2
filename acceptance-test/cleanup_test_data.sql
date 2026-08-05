-- 清理 Agent3 验收测试产生的单据（所有单据类型 + 本次测试编号前缀）
-- 不删除：xsdd 的 E2E-*（Agent1 遗留）、任何真实业务数据
USE erp;
DELETE d FROM doc_detail d
JOIN doc_head h ON d.head_id = h.id
WHERE (h.biz_no LIKE 'BND-BIG-%' OR h.biz_no LIKE 'CG-RT-%'
       OR h.biz_no LIKE 'IMP%' OR h.biz_no REGEXP '^D[0-9]+r[0-9]+');

DELETE FROM doc_head
WHERE (biz_no LIKE 'BND-BIG-%' OR biz_no LIKE 'CG-RT-%'
       OR biz_no LIKE 'IMP%' OR biz_no REGEXP '^D[0-9]+r[0-9]+');

SELECT doc_type, COUNT(*) AS cnt FROM doc_head GROUP BY doc_type ORDER BY doc_type;
