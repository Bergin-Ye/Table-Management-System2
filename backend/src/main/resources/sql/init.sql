-- =====================================================================
-- ERP 单据管理系统 · 初始化脚本
-- 库名 erp · MySQL 8.x · utf8mb4
-- 执行：mysql -uroot -phaoyu2026 < init.sql
-- 注意：重复执行会因外键依赖顺序报错，请勿重复执行。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS erp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE erp;

-- ---------------- 用户 ----------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(50)  NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,          -- BCrypt 加密
  nickname   VARCHAR(50),
  role       VARCHAR(20)  NOT NULL DEFAULT 'USER', -- ADMIN / USER
  status     TINYINT      DEFAULT 1,         -- 1 启用 0 停用
  created_at DATETIME,
  updated_at DATETIME
);

-- ---------------- 菜单（含上下级） ----------------
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT       DEFAULT 0,          -- 0 = 顶级
  name      VARCHAR(50),
  path      VARCHAR(100),                    -- 前端路由
  doc_type  VARCHAR(20),                     -- 单据类型；非单据菜单为 NULL
  sort      INT          DEFAULT 0
);

-- ---------------- 角色-菜单授权（RBAC） ----------------
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  role    VARCHAR(20) NOT NULL,              -- ADMIN / USER
  menu_id BIGINT      NOT NULL
);

-- ---------------- 单据头（所有单据共用） ----------------
DROP TABLE IF EXISTS doc_head;
CREATE TABLE doc_head (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type   VARCHAR(20) NOT NULL,
  biz_no     VARCHAR(100) NOT NULL,          -- 编号（手动填写/导入）
  head_data  JSON,                           -- 头部字段 KV
  status     VARCHAR(20) DEFAULT NULL,       -- 【预留】审批流扩展位，当前恒为 NULL
  search_text TEXT,                          -- 搜索文本：head + 全部明细值拼接
  created_by VARCHAR(50),
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_doc_bizno (doc_type, biz_no)
);

-- ---------------- 明细行 ----------------
DROP TABLE IF EXISTS doc_detail;
CREATE TABLE doc_detail (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type    VARCHAR(20) NOT NULL,
  head_id     BIGINT NOT NULL,
  row_no      INT DEFAULT 0,
  detail_data JSON,
  KEY idx_head (head_id)
);

-- ---------------- 用户列偏好 ----------------
DROP TABLE IF EXISTS sys_column_pref;
CREATE TABLE sys_column_pref (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT      NOT NULL,
  doc_type VARCHAR(20) NOT NULL,
  columns JSON,
  UNIQUE KEY uk_user_doc (user_id, doc_type)
);

-- =====================================================================
-- 种子数据
-- =====================================================================

-- 用户：admin/ADMIN（密码 admin123）、user1/USER（密码 123456）
INSERT INTO sys_user (username, password, nickname, role, status, created_at, updated_at) VALUES
('admin', '$2b$12$6hW6jCpp94QQ1TkuthURFOLDQWppynI4zhvKdrH8F4V8PG5JoOhWy', '系统管理员', 'ADMIN', 1, NOW(), NOW()),
('user1', '$2b$12$W79i0V0H2pcW5lmPZhaj.OPqXA1GcTKg0KbKP4bq7Rhwogszk6mmC', '普通用户', 'USER', 1, NOW(), NOW());

-- 菜单树
-- 顶级：1 销售管理 / 2 采购管理 / 3 库存管理 / 4 系统管理
INSERT INTO sys_menu (id, parent_id, name, path, doc_type, sort) VALUES
(1, 0, '销售管理', '/sale', NULL, 1),
(2, 0, '采购管理', '/purchase', NULL, 2),
(3, 0, '库存管理', '/stock', NULL, 3),
(4, 0, '系统管理', '/system', NULL, 9);

-- 单据子菜单：10..19
INSERT INTO sys_menu (id, parent_id, name, path, doc_type, sort) VALUES
(10, 1, '销售订单', '/doc/xsdd', 'xsdd', 1),
(11, 1, '销售出库', '/doc/xsck', 'xsck', 2),
(12, 2, '采购申请', '/doc/cgsq', 'cgsq', 1),
(13, 2, '采购订单', '/doc/cgdd', 'cgdd', 2),
(14, 3, '外购入库', '/doc/wgrk', 'wgrk', 1),
(15, 3, '生产领料单', '/doc/scld', 'scld', 2),
(16, 3, '产品入库', '/doc/cprk', 'cprk', 3),
(17, 3, '其他出库', '/doc/qtck', 'qtck', 4),
(18, 3, '其他入库单', '/doc/qtrk', 'qtrk', 5),
(19, 3, '调拨单', '/doc/dbd', 'dbd', 6);

-- 系统管理子菜单：20 用户管理 / 21 角色授权
INSERT INTO sys_menu (id, parent_id, name, path, doc_type, sort) VALUES
(20, 4, '用户管理', '/system/user', NULL, 1),
(21, 4, '角色授权', '/system/role', NULL, 2);

-- 角色-菜单授权
-- ADMIN → 全部菜单（1..21）
INSERT INTO sys_role_menu (role, menu_id) VALUES
('ADMIN', 1), ('ADMIN', 2), ('ADMIN', 3), ('ADMIN', 4),
('ADMIN', 10), ('ADMIN', 11), ('ADMIN', 12), ('ADMIN', 13),
('ADMIN', 14), ('ADMIN', 15), ('ADMIN', 16), ('ADMIN', 17),
('ADMIN', 18), ('ADMIN', 19), ('ADMIN', 20), ('ADMIN', 21);

-- USER → 3 个业务父菜单 + 10 个单据子菜单（不含系统管理）
INSERT INTO sys_role_menu (role, menu_id) VALUES
('USER', 1), ('USER', 2), ('USER', 3),
('USER', 10), ('USER', 11), ('USER', 12), ('USER', 13),
('USER', 14), ('USER', 15), ('USER', 16), ('USER', 17),
('USER', 18), ('USER', 19);
