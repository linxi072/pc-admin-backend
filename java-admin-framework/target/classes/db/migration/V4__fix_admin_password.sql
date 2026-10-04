-- V4 修正超级管理员初始口令。
-- 原 V1 中 admin 的 password_hash 为占位 hash（{bcrypt}$2a$10$EixZaYVK...），
-- 该 hash 无对应明文（实测 password/admin/123456/ChangeMe123! 等均不匹配），
-- 导致超级管理员账号无法登录。此处将其修正为明确默认口令 admin123。
-- 仅当口令仍为原占位值时更新，避免覆盖已自行修改的密码。
UPDATE sys_user
SET password_hash = '{bcrypt}$2a$10$HXuNze85xFb7XsvExix/mO/s49oO6K7L7NbFDKGqdXhGa1IsDnvXO',
    password_changed_at = NOW(3)
WHERE username = 'admin'
  AND password_hash = '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96wNz/9H1P4F1q0qU3eZ0q7e2m';
