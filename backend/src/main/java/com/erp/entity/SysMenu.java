package com.erp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜单表 sys_menu（含上下级，parent_id=0 为顶级）
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0 = 顶级 */
    private Long parentId;

    private String name;

    /** 前端路由 */
    private String path;

    /** 单据类型；非单据菜单为 NULL */
    private String docType;

    private Integer sort;
}
