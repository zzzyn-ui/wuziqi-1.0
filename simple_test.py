#!/usr/bin/env python3
"""简化测试 - 只发送最基本的消息"""

import asyncio
import websockets
import json

async def simple_test():
    print("测试 1: 发送最简化的登录消息")
    try:
        async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
            # 最简化的格式 - 只有 type 和 body
            msg = {"type": 1, "body": {"username": "test", "password": "test"}}
            print(f"  发送: {json.dumps(msg)}")

            await ws.send(json.dumps(msg))
            print("  ✓ 已发送")

            try:
                resp = await asyncio.wait_for(ws.recv(), timeout=3)
                data = json.loads(resp)
                print(f"  ✓ 收到: type={data.get('type')}")
                if 'body' in data:
                    print(f"  消息: {data['body']}")
            except asyncio.TimeoutError:
                print("  ✗ 超时")
    except Exception as e:
        print(f"  ✗ 错误: {e}")

    print()
    print("测试 2: 尝试注册消息")
    try:
        async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
            msg = {"type": 2, "body": {"username": "test", "password": "test"}}
            print(f"  发送: {json.dumps(msg)}")

            await ws.send(json.dumps(msg))
            print("  ✓ 已发送")

            try:
                resp = await asyncio.wait_for(ws.recv(), timeout=3)
                data = json.loads(resp)
                print(f"  ✓ 收到: type={data.get('type')}")
                if 'body' in data:
                    print(f"  消息: {data['body']}")
            except asyncio.TimeoutError:
                print("  ✗ 超时")
    except Exception as e:
        print(f"  ✗ 错误: {e}")

asyncio.run(simple_test())
