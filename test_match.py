#!/usr/bin/env python3
"""测试休闲模式匹配 - 先注册后匹配"""
import asyncio
import websockets
import json
import time

async def test_match():
    print("=== 测试休闲模式匹配 ===\n")
    
    # 连接两个客户端
    ws1 = await websockets.connect("ws://localhost:9090/ws")
    ws2 = await websockets.connect("ws://localhost:9090/ws")
    
    print("✓ 两个WebSocket连接已建立\n")
    
    # 注册两个用户
    username1 = f"test1_{int(time.time() % 10000)}"
    username2 = f"test2_{int(time.time() % 10000)}"
    
    reg1 = {"type": 2, "body": {"username": username1, "password": "123456", "nickname": "测试玩家1"}}
    await ws1.send(json.dumps(reg1))
    resp1 = await ws1.recv()
    print(f"客户端1注册响应: {json.dumps(json.loads(resp1), ensure_ascii=False)}\n")
    
    reg2 = {"type": 2, "body": {"username": username2, "password": "123456", "nickname": "测试玩家2"}}
    await ws2.send(json.dumps(reg2))
    resp2 = await ws2.recv()
    print(f"客户端2注册响应: {json.dumps(json.loads(resp2), ensure_ascii=False)}\n")
    
    # 客户端1登录
    login1 = {"type": 1, "body": {"username": username1, "password": "123456"}}
    await ws1.send(json.dumps(login1))
    resp1 = await ws1.recv()
    print(f"客户端1登录响应: {json.dumps(json.loads(resp1), ensure_ascii=False)}\n")
    
    # 客户端2登录
    login2 = {"type": 1, "body": {"username": username2, "password": "123456"}}
    await ws2.send(json.dumps(login2))
    resp2 = await ws2.recv()
    print(f"客户端2登录响应: {json.dumps(json.loads(resp2), ensure_ascii=False)}\n")
    
    # 客户端1发起休闲模式匹配
    match1 = {"type": 10, "body": {"rating": 1200, "mode": "casual"}}
    await ws1.send(json.dumps(match1))
    print("客户端1发起休闲模式匹配...")
    
    # 等待一点时间
    await asyncio.sleep(0.5)
    
    # 客户端2发起休闲模式匹配
    match2 = {"type": 10, "body": {"rating": 1200, "mode": "casual"}}
    await ws2.send(json.dumps(match2))
    print("客户端2发起休闲模式匹配...\n")
    
    # 等待匹配结果
    matched1 = False
    matched2 = False
    
    for i in range(10):
        try:
            if not matched1:
                resp = await asyncio.wait_for(ws1.recv(), timeout=2)
                data = json.loads(resp)
                if data.get("type") == 12:  # MATCH_SUCCESS
                    print(f"✓ 客户端1匹配成功!")
                    print(f"  房间ID: {data['body']['room_id']}")
                    print(f"  是否先手: {data['body']['is_first']}")
                    print(f"  棋子颜色: {data['body']['my_color']}")
                    print(f"  对手: {data['body']['opponent']}\n")
                    matched1 = True
        except asyncio.TimeoutError:
            pass
        
        try:
            if not matched2:
                resp = await asyncio.wait_for(ws2.recv(), timeout=2)
                data = json.loads(resp)
                if data.get("type") == 12:  # MATCH_SUCCESS
                    print(f"✓ 客户端2匹配成功!")
                    print(f"  房间ID: {data['body']['room_id']}")
                    print(f"  是否先手: {data['body']['is_first']}")
                    print(f"  棋子颜色: {data['body']['my_color']}")
                    print(f"  对手: {data['body']['opponent']}\n")
                    matched2 = True
        except asyncio.TimeoutError:
            pass
        
        if matched1 and matched2:
            break
    
    if not matched1:
        print("❌ 客户端1匹配超时")
    if not matched2:
        print("❌ 客户端2匹配超时")
    
    # 检查Redis队列状态
    import subprocess
    result = subprocess.run(
        ['docker', 'exec', 'gobang-redis', 'redis-cli', '-a', 'redis123', 'SMEMBERS', 'match:queue:casual'],
        capture_output=True, text=True
    )
    print(f"Redis休闲模式队列: {result.stdout.strip() if result.stdout.strip() else '(空)'}")
    
    await ws1.close()
    await ws2.close()
    print("\n=== 测试完成 ===")

if __name__ == "__main__":
    asyncio.run(test_match())
