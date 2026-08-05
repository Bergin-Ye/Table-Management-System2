USE erp;
SELECT h.biz_no, JSON_EXTRACT(h.head_data, '$."运费"') AS head_yf, JSON_EXTRACT(h.head_data, '$."编号"') AS head_bh
FROM doc_head h WHERE h.doc_type='xsdd' AND h.biz_no='CG-RT-001';
SELECT d.row_no, JSON_EXTRACT(d.detail_data, '$."编号"') AS det_bh, JSON_EXTRACT(d.detail_data, '$."运费"') AS det_yf
FROM doc_detail d JOIN doc_head h ON d.head_id=h.id WHERE h.doc_type='xsdd' AND h.biz_no='CG-RT-001' ORDER BY d.row_no;
