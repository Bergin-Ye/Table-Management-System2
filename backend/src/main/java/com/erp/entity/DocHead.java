package com.erp.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单据头表 doc_head —— 所有单据共用一张表，字段以 JSON 存储
 */
@Data
@TableName("doc_head")
public class DocHead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String docType;

    /** 编号（手动填写/导入），(doc_type, biz_no) 唯一 */
    private String bizNo;

    /** 头部字段 KV 的 JSON 文本 */
    private String headData;

    /** 【预留】审批流扩展位，当前恒为 NULL，代码不得读写 */
    private String status;

    /** 搜索文本：head + 全部明细值拼接 */
    private String searchText;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
