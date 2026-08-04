package com.erp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户列偏好表 sys_column_pref —— 每用户每单据一条
 */
@Data
@TableName("sys_column_pref")
public class SysColumnPref {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String docType;

    /** 可见列 key 数组的 JSON 文本 */
    private String columns;
}
