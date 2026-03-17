#!/usr/bin/env python3
"""模拟前端登录流程测试"""

import asyncio
import websockets
import json

async def test_login_like_browser():
    print("=== 模拟浏览器登录流程 ===")

    # 1. 首先检查健康API
    print("\n[1/3] 测试健康API...")
    try:
        import aiohttp
        async with aiohttp.ClientSession() as session:
            async with session.get("http://localhost:9090/api/health") as resp:
                if resp.status == 200:
                    data = await resp.json()
                    print(f"   ✅ 健康检查成功: {data.get('status')}")
                    print(f"   数据库: {data.get('database')}")
                    print(f"   在线用户: {data.get('online_users', 0)}")
                else:
                    print(f"   ❌ 健康检查失败: HTTP {resp.status}")
    except ImportError:
        print("   ⚠️  aiohttp未安装，跳过健康检查")
    except Exception as e:
        print(f"   ❌ 健康检查错误: {e}")

    # 2. 建立WebSocket连接（模拟页面加载后的连接）
    print("\n[2/3] 测试WebSocket连接...")
    try:
        async with websockets.connect("ws://localhost:9090/ws") as ws:
            print("   ✅ WebSocket连接成功")

            # 3. 测试登录（模拟用户点击登录按钮）
            print("\n[3/3] 测试登录...")

            # 先注册一个新用户
            import time
            username = f"u{int(time.time() % 100000)}"

            # 注册
            reg_msg = {"type": 2, "body": {"username": username, "password": "test123", "nickname": "浏览器测试"}}
            await ws.send(json.dumps(reg_msg))

            resp = await asyncio.wait_for(ws.recv(), timeout=3)
            reg_data = json.loads(resp)

            if reg_data.get('type') == 3:
                if reg_data.get('body', {}).get('success'):
                    print(f"   ✅ 注册成功: {username}")
                else:
                    print(f"   ⚠️  注册失败: {reg_data.get('body', {}).get('message')}")

            # 登录
            login_msg = {"type": 1, "body": {"username": username, "password": "test123"}}
            await ws.send(json.dumps(login_msg))

            resp = await asyncio.wait_for(ws.recv(), timeout=3)
            login_data = json.loads(resp)

            if login_data.get('type') == 3:
                body = login_data.get('body', {})
                if body.get('success'):
                    token = body.get('token')
                    user_info = body.get('user_info')
                    print(f"   ✅ 登录成功!")
                    print(f"   用户: {user_info}")
                    print(f"   Token长度: {len(token) if token else 0}")
                else:
                    print(f"   ❌ 登录失败: {body.get('message')}")
            else:
                print(f"   ❌ 响应类型错误: {login_data.get('type')}")

    except Exception as e:
        print(f"   ❌ WebSocket测试失败: {e}")

    print("\n=== 测试完成 ===")
    print("\n📝 建议:")
    print("1. 打开浏览器访问: http://localhost:9090/login.html")
    print("2. 按F12打开开发者工具查看Console")
    print("3. 尝试注册/登录测试完整流程")
    print("4. 检查Network标签确认API请求成功")

if __name__ == "__main__":
    asyncio.run(test_login_like_browser())
