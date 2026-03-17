#!/usr/bin/env python3
"""深度诊断脚本 - 找出登录无响应的根本原因"""

import asyncio
import websockets
import json
import time

async def deep_test():
    print("=" * 60)
    print("五子棋服务器 - 深度诊断")
    print("=" * 60)

    # 测试连接
    print("\n[1] 测试 WebSocket 连接...")
    try:
        async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
            print("    ✓ WebSocket 连接成功")

            # 测试不同消息格式
            test_cases = [
                {
                    "name": "标准格式（嵌套body）",
                    "message": {
                        "type": 1,
                        "sequence_id": int(time.time() * 1000),
                        "timestamp": int(time.time() * 1000),
                        "body": {
                            "username": "test",
                            "password": "test"
                        }
                    }
                },
                {
                    "name": "扁平格式（直接字段）",
                    "message": {
                        "type": 1,
                        "sequence_id": int(time.time() * 1000),
                        "username": "test",
                        "password": "test"
                    }
                },
                {
                    "name": "驼峰命名 sequenceId",
                    "message": {
                        "type": 1,
                        "sequenceId": int(time.time() * 1000),
                        "body": {
                            "username": "test",
                            "password": "test"
                        }
                    }
                }
            ]

            for i, test in enumerate(test_cases, 1):
                print(f"\n[2.{i}] 测试: {test['name']}")
                print(f"    发送: {json.dumps(test['message'], indent=4)}")

                try:
                    await ws.send(json.dumps(test['message']))
                    print("    ✓ 消息已发送")

                    # 等待响应
                    try:
                        response = await asyncio.wait_for(ws.recv(), timeout=5)
                        print(f"    ✓ 收到响应: {response}")

                        # 解析响应
                        try:
                            data = json.loads(response)
                            print(f"    ✓ 解析成功: type={data.get('type')}")
                            if 'body' in data:
                                body = data['body']
                                if body.get('success'):
                                    print(f"    ✓ 登录成功!")
                                else:
                                    print(f"    ! 登录失败: {body.get('message')}")
                        except:
                            print(f"    ! 响应不是有效JSON")

                    except asyncio.TimeoutError:
                        print(f"    ✗ 无响应（5秒超时）")

                except Exception as e:
                    print(f"    ✗ 发送失败: {e}")

                # 等待一下再测试下一个
                await asyncio.sleep(1)

    except websockets.exceptions.ConnectionClosed as e:
        print(f"    ✗ 连接被关闭: {e}")
    except Exception as e:
        print(f"    ✗ 连接失败: {e}")

    print("\n" + "=" * 60)
    print("诊断完成")
    print("=" * 60)
    print("\n建议:")
    print("1. 如果所有测试都超时，说明服务器没有处理消息")
    print("2. 检查服务器日志: logs/gobang-server.log")
    print("3. 确认服务器使用的是最新编译的代码")
    print("4. 尝试重启服务器")

if __name__ == "__main__":
    asyncio.run(deep_test())
