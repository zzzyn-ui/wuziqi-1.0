@echo off
echo ========================================
echo 五子棋数据库修复脚本
echo ========================================
echo.

echo 正在连接MySQL并修复nickname字段...
echo.

mysql -h localhost -P 3307 -u root -proot123456 gobang << 'EOF'
-- 修复nickname字段
ALTER TABLE user MODIFY COLUMN nickname VARCHAR(32) NOT NULL DEFAULT '' COMMENT '昵称';

-- 显示表结构验证修改
DESCRIBE user;

SELECT '修复完成!' AS message;
EOF

echo.
echo ========================================
echo 修复完成！请重启后端服务。
echo ========================================
pause
