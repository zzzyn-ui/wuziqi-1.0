# 五子棋服务器 - 彻底重启并修复
# 请以管理员身份运行 PowerShell 执行此脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "五子棋服务器 - 彻底重启并修复" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 步骤 1: 停止所有 Java 进程
Write-Host "[1/7] 停止所有 Java 进程..." -ForegroundColor Yellow
try {
    Get-Process | Where-Object {$_.ProcessName -eq 'java'} | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "    ✓ 所有 Java 进程已停止" -ForegroundColor Green
} catch {
    Write-Host "    ! 停止进程时出错: $_" -ForegroundColor Red
}

Write-Host ""

# 步骤 2: 等待端口释放
Write-Host "[2/7] 等待端口释放..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# 检查端口
$portInUse = Get-NetTCPConnection -LocalPort 9090 -ErrorAction SilentlyContinue | Where-Object {$_.State -eq 'Listen'}
if ($portInUse) {
    Write-Host "    ! 端口 9090 仍被占用，强制清理..." -ForegroundColor Red
    $portInUse | ForEach-Object {
        Write-Host "    终止进程 $($_.OwningProcess)..."
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 3
}

Write-Host "    ✓ 端口已释放" -ForegroundColor Green
Write-Host ""

# 步骤 3: 清理旧日志
Write-Host "[3/7] 清理旧日志..." -ForegroundColor Yellow
$logFiles = @("logs\gobang-server.log", "logs\console-output.log")
foreach ($file in $logFiles) {
    if (Test-Path $file) {
        Remove-Item $file -Force
    }
}
Write-Host "    ✓ 日志已清理" -ForegroundColor Green
Write-Host ""

# 步骤 4: 重新编译
Write-Host "[4/7] 重新编译最新代码..." -ForegroundColor Yellow
Set-Location D:\wuziqi

$compileResult = & mvn compile -DskipTests -q
if ($LASTEXITCODE -eq 0) {
    Write-Host "    ✓ 编译成功" -ForegroundColor Green
} else {
    Write-Host "    ✗ 编译失败！" -ForegroundColor Red
    Write-Host "    错误信息: $compileResult"
    Read-Host "按回车键退出"
    exit 1
}
Write-Host ""

# 步骤 5: 启动服务器
Write-Host "[5/7] 启动服务器..." -ForegroundColor Yellow
$javaProcess = Start-Process -FilePath "java" `
    -ArgumentList "-cp", "target/classes;target/dependency/*", "com.gobang.GobangServer" `
    -RedirectStandardOutput "logs\gobang-server.log" `
    -NoNewWindow `
    -PassThru

Write-Host "    PID: $($javaProcess.Id)" -ForegroundColor Cyan
Write-Host ""

# 步骤 6: 等待服务器启动
Write-Host "[6/7] 等待服务器启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 检查服务器是否成功启动
$serverRunning = Get-NetTCPConnection -LocalPort 9090 -ErrorAction SilentlyContinue | Where-Object {$_.State -eq 'Listen'}

if ($serverRunning) {
    Write-Host "    ✓ 服务器正在运行" -ForegroundColor Green
    Write-Host "    ✓ WebSocket 端口: 9090" -ForegroundColor Green
} else {
    Write-Host "    ✗ 服务器启动失败！" -ForegroundColor Red
    Write-Host ""
    Write-Host "查看错误日志:" -ForegroundColor Yellow
    Get-Content logs\gobang-server.log -Tail 50 | Write-Host
    Read-Host "按回车键退出"
    exit 1
}
Write-Host ""

# 步骤 7: 显示启动日志
Write-Host "[7/7] 服务器启动日志:" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Get-Content logs\gobang-server.log -Tail 30 | Write-Host
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 测试登录功能
Write-Host "现在测试登录功能..." -ForegroundColor Yellow
Write-Host ""

$testResult = & python deep_diagnose.py
Write-Host $testResult

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "服务器已启动并测试完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "监控实时日志:" -ForegroundColor Yellow
Write-Host "  powershell -Command 'Get-Content logs\gobang-server.log -Wait'"
Write-Host ""
Write-Host "停止服务器:" -ForegroundColor Yellow
Write-Host "  Stop-Process -Id $($javaProcess.Id)"
Write-Host ""
Write-Host "按 Ctrl+C 退出此窗口（服务器继续运行）" -ForegroundColor Cyan
Write-Host ""

# 实时监控日志
try {
    Get-Content logs\gobang-server.log -Wait -ErrorAction SilentlyContinue
} catch [System.Management.Automation.PipelineStoppedException] {
    Write-Host ""
    Write-Host "日志监控已停止，服务器仍在运行" -ForegroundColor Yellow
    Write-Host "如需停止服务器，运行: Stop-Process -Id $($javaProcess.Id)" -ForegroundColor Yellow
}
