-- ============================================================
-- 权限管理系统 - 数据库关系模型建表 SQL
-- 数据库：MySQL 8.0+（兼容 8.1.0）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 存储引擎：InnoDB
-- 关系：用户 N:N 角色；用户 N:N 部门；角色 N:N 菜单
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 用户表 sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username     VARCHAR(64)  NOT NULL COMMENT '登录账号',
  password     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密码（加密存储）',
  display_name VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名称/昵称',
  email        VARCHAR(128) DEFAULT '' COMMENT '邮箱',
  mobile       VARCHAR(32)  DEFAULT '' COMMENT '手机号',
  avatar_url   VARCHAR(255) DEFAULT '' COMMENT '头像URL',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ----------------------------
-- 2. 角色表 sys_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  role_code    VARCHAR(64)  NOT NULL COMMENT '角色编码（程序标识，如 SUPER_ADMIN）',
  role_name    VARCHAR(64)  NOT NULL COMMENT '角色名称',
  role_type    VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS' COMMENT '角色类型：SYSTEM=内置 BUSINESS=业务',
  description  VARCHAR(255) DEFAULT '' COMMENT '角色描述',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ----------------------------
-- 3. 部门表 sys_department（支持树形层级）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_department (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  dept_code  VARCHAR(64)  NOT NULL COMMENT '部门编码',
  dept_name  VARCHAR(64)  NOT NULL COMMENT '部门名称',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID（0=顶级）',
  ancestors  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '祖级ID路径，逗号分隔',
  leader     VARCHAR(64)  DEFAULT '' COMMENT '负责人',
  sort_no    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dept_code (dept_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

-- ----------------------------
-- 4. 菜单表 sys_menu（支持树形层级）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID（0=顶级）',
  menu_name  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
  menu_code  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜单编码（唯一标识）',
  menu_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1=目录 2=菜单 3=按钮',
  path       VARCHAR(128) DEFAULT '' COMMENT '路由路径',
  component  VARCHAR(128) DEFAULT '' COMMENT '前端组件路径',
  icon       VARCHAR(64)  DEFAULT '' COMMENT '图标',
  permission VARCHAR(64)  DEFAULT '' COMMENT '权限标识（后端接口/auth码）',
  sort_no    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  visible    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1=显示 0=隐藏',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_code (menu_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

-- ----------------------------
-- 5. 用户-角色关联表（多对多）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT   NOT NULL COMMENT '用户ID',
  role_id    BIGINT   NOT NULL COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';

-- ----------------------------
-- 6. 用户-部门关联表（多对多，可标记主部门）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_department (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT   NOT NULL COMMENT '用户ID',
  dept_id    BIGINT   NOT NULL COMMENT '部门ID',
  is_primary TINYINT  NOT NULL DEFAULT 0 COMMENT '是否主部门：1=是 0=否',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_dept (user_id, dept_id),
  KEY idx_dept (dept_id),
  CONSTRAINT fk_ud_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_ud_dept FOREIGN KEY (dept_id) REFERENCES sys_department (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-部门关联表';

-- ----------------------------
-- 7. 角色-菜单关联表（多对多）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id    BIGINT   NOT NULL COMMENT '角色ID',
  menu_id    BIGINT   NOT NULL COMMENT '菜单ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  KEY idx_menu (menu_id),
  CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
  CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单关联表';

-- ============================================================
-- 8. 菜单初始化数据（典型权限管理菜单树）
--    menu_type: 1=目录 2=菜单 3=按钮
--    visible=0 的按钮级菜单不在左侧导航展示，仅用于权限校验
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, permission, sort_no, visible, status) VALUES
-- 顶级：系统管理（目录）
(1, 0,  '系统管理', 'system',        1, '/system',       'Layout',          'setting', '',                1, 1, 1),
-- 用户管理（菜单）
(2, 1,  '用户管理', 'system:user',   2, '/system/user',  'system/user/index', 'user', 'system:user:read',   1, 1, 1),
(3, 2,  '用户新增', 'system:user:create', 3, '', '', '', 'system:user:create', 1, 0, 1),
(4, 2,  '用户编辑', 'system:user:update', 3, '', '', '', 'system:user:update', 2, 0, 1),
(5, 2,  '用户删除', 'system:user:delete', 3, '', '', '', 'system:user:delete', 3, 0, 1),
-- 角色管理（菜单）
(6, 1,  '角色管理', 'system:role',   2, '/system/role',  'system/role/index', 'role', 'system:role:read',   2, 1, 1),
(7, 6,  '角色新增', 'system:role:create', 3, '', '', '', 'system:role:create', 1, 0, 1),
(8, 6,  '角色编辑', 'system:role:update', 3, '', '', '', 'system:role:update', 2, 0, 1),
(9, 6,  '角色删除', 'system:role:delete', 3, '', '', '', 'system:role:delete', 3, 0, 1),
-- 部门管理（菜单）
(10, 1, '部门管理', 'system:dept',   2, '/system/dept',  'system/dept/index', 'tree', 'system:dept:read',   3, 1, 1),
(11, 10, '部门新增', 'system:dept:create', 3, '', '', '', 'system:dept:create', 1, 0, 1),
(12, 10, '部门编辑', 'system:dept:update', 3, '', '', '', 'system:dept:update', 2, 0, 1),
(13, 10, '部门删除', 'system:dept:delete', 3, '', '', '', 'system:dept:delete', 3, 0, 1),
-- 菜单管理（菜单）
(14, 1, '菜单管理', 'system:menu',   2, '/system/menu',  'system/menu/index', 'menu', 'system:menu:read',   4, 1, 1),
(15, 14, '菜单新增', 'system:menu:create', 3, '', '', '', 'system:menu:create', 1, 0, 1),
(16, 14, '菜单编辑', 'system:menu:update', 3, '', '', '', 'system:menu:update', 2, 0, 1),
(17, 14, '菜单删除', 'system:menu:delete', 3, '', '', '', 'system:menu:delete', 3, 0, 1);

-- ============================================================
-- 9. 种子数据：超级管理员（SUPER_ADMIN）
--    包含：角色(1) + 用户(1) + 用户-角色关联 + 角色-菜单全量关联
--    说明：username=admin，密码密文为 BCrypt 对 "123456" 的加密结果
--          （Spring Security BCryptPasswordEncoder，可直接登录）
-- ============================================================

-- 9.1 角色：SUPER_ADMIN（内置系统角色，拥有全部菜单权限）
INSERT INTO sys_role (id, role_code, role_name, role_type, description, status, sort_no) VALUES
(1, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', '系统内置超级管理员，拥有全部菜单与操作权限', 1, 1);

-- 9.2 用户：超级管理员账号
INSERT INTO sys_user (id, username, password, display_name, email, mobile, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZaMOBA1tOJ.9LGGzX1L7j2T/uuI.k/gote1Yq8syi8.', '超级管理员', 'admin@example.com', '13800000000', 1);

-- 9.3 用户-角色关联：admin 绑定 SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1);

-- 9.4 角色-菜单关联：SUPER_ADMIN 拥有全部菜单（含目录/菜单/按钮级）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(1, 6), (1, 7), (1, 8), (1, 9),
(1, 10), (1, 11), (1, 12), (1, 13),
(1, 14), (1, 15), (1, 16), (1, 17);

-- ============================================================
-- 10. 部门种子 + 用户-部门（多部门归属）种子
--     演示：一个用户可属于多个部门，并以 is_primary 标记主部门
-- ============================================================

-- 10.1 部门表（树形）：总公司 → 技术部 / 财务部 / 人力资源部
INSERT INTO sys_department (id, parent_id, dept_code, dept_name, ancestors, leader, sort_no, status) VALUES
(1, 0, 'HQ',   '总公司',     '0',      '王总',   1, 1),
(2, 1, 'TECH', '技术部',     '0,1',    '李工',   1, 1),
(3, 1, 'FIN',  '财务部',     '0,1',    '赵财务', 2, 1),
(4, 1, 'HR',   '人力资源部', '0,1',    '钱经理', 3, 1);

-- 10.2 用户-部门关联：admin(1) 主部门为技术部，同时兼任财务部
INSERT INTO sys_user_department (user_id, dept_id, is_primary) VALUES
(1, 2, 1),
(1, 3, 0);
