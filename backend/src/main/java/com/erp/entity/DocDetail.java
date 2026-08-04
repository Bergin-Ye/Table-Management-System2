package com.erp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 明细行表 doc_detail —— 一行一条明细，字段以 JSON 存储
 */
@Data
@TableName("doc_detail")
public class DocDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docType;

    /** 关联 doc_head.id */
    private Long headId;

    /** 行号 */
    private Integer rowNo;

    /** 明细字段 KV 的 JSON 文本 */
    private String detailData;
}
