#!/usr/bin/env python3
"""测试服务器与Redis的连接状态"""

import asyncio
import websockets
import json

async def test_redis_connection():
    print("测试服务器Redis连接状态...")

    # 登录用户
    async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
        # 注册/登录
        msg = {"type": 2, "body": {"username": "redis_test", "password": "test123456", "nickname": "Redis测试"}}
        await ws.send(json.dumps(msg))
        resp = await asyncio.wait_for(ws.recv(), timeout=3)
        data = json.loads(resp)
        print(f"注册响应: type={data.get('type')}, success={data.get('body', {}).get('success')}")

        # 登录
        msg = {"type": 1, "body": {"username": "redis_test", "password": "test123456"}}
        await ws.send(json.dumps(msg))
        resp = await asyncio.wait_for(ws.recv(), timeout=3)
        data = json.loads(resp)
        print(f"登录响应: type={data.get('type')}, success={data.get('body', {}).get('success')}")

        # 测试匹配功能（使用Redis）
        print("\n测试匹配功能（需要Redis）...")
        msg = {"type": 10, "body": {"rating": 1200, "mode": "casual"}}
        await ws.send(json.dumps(msg))

        # 等待匹配结果或超时
        try:
            resp = await asyncio.wait_for(ws.recv(), timeout=5)
            data = json.loads(resp)
            print(f"匹配响应: type={data.get('type')}")
            if data.get('body'):
                print(f"  消息: {data.get('body')}")
        except asyncio.TimeoutError:
            print("匹配超时（正常，因为没有其他玩家）")

        # 取消匹配
        msg = {"type": 11}
        await ws.send(json.dumps(msg))

        try:
            resp = await asyncio.wait_for(ws.recv(), timeout=3)
            data = json.loads(resp)
            print(f"取消匹配响应: type={data.get('type')}")
        except asyncio.TimeoutError:
            print("取消匹配超时")

if __name__ == "__main__":
    asyncio.run(test_redis_connection())
