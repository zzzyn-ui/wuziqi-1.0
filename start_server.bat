@echo off
cd /d D:\wuziqi
echo Starting Gobang Server...
java -cp "target/classes;target/dependency/*" com.gobang.GobangServer
