# 登录页面测试指南

## 已修复的问题

### ✅ 1. Content-Length计算错误
- **问题**: HTTP响应使用`json.length()`（UTF-16字符数）而不是字节数
- **修复**: 使用`json.getBytes(StandardCharsets.UTF_8).length`计算正确字节数

### ✅ 2. HTML全角字符问题
- **问题**: 输入框图标使用全角`?`字符导致JavaScript解析错误
- **修复**: 替换为emoji图标👤🔒

## 测试步骤

### 方法1: 使用简化测试页面（推荐）

打开浏览器访问:
```
http://localhost:9090/test-login-simple.html
```

这个页面功能完整且简洁，可以:
- ✅ 测试WebSocket连接
- ✅ 测试登录功能
- ✅ 查看实时状态和错误

### 方法2: 使用修复后的登录页面

打开浏览器访问:
```
http://localhost:9090/login.html
```

#### 测试注册:
1. 点击"注册"标签
2. 填写信息:
   - 用户名: `test1` (3-16位字母/数字)
   - 昵称: `测试用户1`
   - 密码: `123456` (至少6位)
   - 确认密码: `123456`
3. 点击"注册"按钮
4. 查看响应消息

#### 测试登录:
1. 确保在"登录"标签
2. 输入用户名和密码
3. 点击"登录"按钮
4. 查看响应消息

#### 游客模式:
1. 点击"游客模式体验"按钮
2. 自动进入主页面

## 调试方法

### 打开浏览器开发者工具:
- **Chrome**: 按F12
- **Firefox**: 按F12
- **Edge**: 按F12

### 查看Console标签:
- 应该看到: "=== 页面加载完成 ==="
- 应该看到: "=== 脚本执行完成 ==="
- 如有错误会显示红色错误信息

### 查看Network标签:
- 筛选"WS"查看WebSocket连接
- 筛选"XHR"查看API请求
- 应该看到:
  - `/api/health` 返回200
  - `ws://localhost:9090/ws` 连接成功

## 常见问题

### Q: 点击按钮没反应
**A**: 按F12查看Console是否有JavaScript错误

### Q: 提示"连接服务器失败"
**A**:
1. 确认服务器运行中: `netstat -ano | findstr 9090`
2. 检查防火墙是否阻止连接
3. 查看Network标签的HTTP状态码

### Q: 登录后没有跳转
**A**: 检查Console和Network标签查看具体错误

### Q: WebSocket连接成功但登录失败
**A**:
1. 确认用户名和密码正确
2. 查看服务器日志: `logs\server-error.log`

## 服务器状态检查

```powershell
# 检查服务器进程
Get-Process java

# 检查端口占用
netstat -ano | findstr 9090

# 查看服务器日志
Get-Content logs\server-error.log -Tail 50
```

## 成功标志

✅ 服务器状态显示: "● 服务器在线"
✅ Console显示: "✓ WebSocket 连接成功"
✅ Network显示: `/api/health` 返回200
✅ 登录后跳转到主页

## 需要帮助?

如果问题仍然存在，请提供:
1. 浏览器Console的错误信息
2. Network标签的HTTP响应
3. 服务器日志中的错误信息
