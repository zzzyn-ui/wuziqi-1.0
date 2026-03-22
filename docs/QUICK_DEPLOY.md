# 🚀 五子棋项目 - 快速部署到ECS

## 📦 第一步：本地打包

双击运行 `build-and-package.bat`，或在命令行执行：

```bash
cd D:\wuziqi
mvn clean package -DskipTests
```

打包完成后，JAR文件位于：`target/gobang-server-1.0.0.jar`

---

## 🌐 第二步：连接ECS

```bash
# 使用SSH连接（替换为你的ECS公网IP）
ssh root@your_ecs_ip
```

---

## ⚡ 第三步：一键部署（推荐）

在ECS上执行：

```bash
# 下载并运行一键部署脚本
wget -O deploy.sh https://raw.githubusercontent.com/your-repo/deploy.sh
chmod +x deploy.sh
sudo ./deploy.sh
```

或者手动复制 `deploy.sh` 内容到ECS执行。

---

## 📤 第四步：上传JAR文件

### 方法1：使用SCP（命令行）
```bash
# 在本地执行（Windows PowerShell）
scp target\gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/
```

### 方法2：使用WinSCP（推荐）
1. 下载 WinSCP: https://winscp.net/
2. 连接到ECS
3. 上传文件到 `/opt/gobang/`

### 方法3：使用FileZilla
1. 下载 FileZilla: https://filezilla-project.org/
2. SFTP连接到ECS
3. 上传文件到 `/opt/gobang/`

---

## 🎮 第五步：启动服务

```bash
# 在ECS上执行
cd /opt/gobang

# 设置JAR文件权限
chmod +x gobang-server-1.0.0.jar
mv gobang-server-1.0.0.jar gobang-server.jar

# 启动服务
sudo systemctl start gobang

# 设置开机自启
sudo systemctl enable gobang

# 查看状态
sudo systemctl status gobang
```

---

## 🔥 阿里云安全组配置

在阿里云控制台配置安全组规则：

1. 登录阿里云控制台
2. 进入 云服务器ECS → 安全组
3. 点击配置规则 → 入方向
4. 添加规则：

| 协议类型 | 端口范围 | 授权对象 | 描述 |
|---------|---------|---------|------|
| TCP | 8083/8083 | 0.0.0.0/0 | 游戏服务器 |
| TCP | 80/80 | 0.0.0.0/0 | HTTP |
| TCP | 443/443 | 0.0.0.0/0 | HTTPS |

---

## ✅ 验证部署

```bash
# 测试API
curl http://your_ecs_ip:8083/api/health

# 预期返回
{"success":true,"data":{"status":"ok",...}}
```

浏览器访问：
- `http://your_ecs_ip:8083/index.html`

---

## 📊 常用管理命令

```bash
# 查看服务状态
sudo systemctl status gobang

# 启动服务
sudo systemctl start gobang

# 停止服务
sudo systemctl stop gobang

# 重启服务
sudo systemctl restart gobang

# 查看实时日志
sudo journalctl -u gobang -f

# 查看最近日志
sudo journalctl -u gobang -n 100
```

---

## 🔧 更新应用

```bash
# 1. 停止服务
sudo systemctl stop gobang

# 2. 上传新的JAR文件
scp target\gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/

# 3. 在ECS上替换文件
cd /opt/gobang
mv gobang-server-1.0.0.jar gobang-server.jar

# 4. 启动服务
sudo systemctl start gobang
```

---

## ❓ 遇到问题？

### 服务无法启动
```bash
# 查看详细日志
sudo journalctl -u gobang -n 50
```

### 无法访问网站
1. 检查安全组规则
2. 检查ECS内部防火墙
3. 检查服务状态

### 数据库连接失败
```bash
# 检查MySQL状态
sudo systemctl status mysql

# 测试连接
mysql -u gobang -p
```

---

## 🎉 完成！

你的五子棋游戏现在可以访问了：

**游戏地址**: `http://your_ecs_ip:8083/index.html`

祝游戏运营顺利！🎮✨
