ALTER TABLE sys_user
  ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'token版本号：改密/强退/新终端登录时+1，旧token立即失效'
  AFTER status;

ALTER TABLE sys_role
  ADD COLUMN perms TEXT NULL COMMENT '权限点，逗号分隔；为空表示该角色走 PermissionRules 内置默认值'
  AFTER role_key;

SELECT id, username, status, version FROM sys_user;
SELECT id, role_name, role_key, status, LEFT(perms, 40) AS perms FROM sys_role;