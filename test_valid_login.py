#!/usr/bin/env python3
"""Test with valid credentials"""

import asyncio
import websockets
import json

async def test_login():
    print("Testing login with valid credentials (need to create user first)")
    try:
        async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
            # Register first
            print("\n1. Registering new user...")
            msg = {"type": 2, "body": {"username": "testuser123", "password": "testpass123", "nickname": "TestUser"}}
            await ws.send(json.dumps(msg))

            resp = await asyncio.wait_for(ws.recv(), timeout=3)
            data = json.loads(resp)
            print(f"   Register response: type={data.get('type')}")
            if data.get('body'):
                print(f"   Success: {data['body'].get('success')}")
                print(f"   Message: {data['body'].get('message')}")

            # Now login
            print("\n2. Logging in...")
            msg = {"type": 1, "body": {"username": "testuser123", "password": "testpass123"}}
            await ws.send(json.dumps(msg))

            resp = await asyncio.wait_for(ws.recv(), timeout=3)
            data = json.loads(resp)
            print(f"   Login response: type={data.get('type')}")
            if data.get('body'):
                print(f"   Success: {data['body'].get('success')}")
                print(f"   Message: {data['body'].get('message')}")
                if data['body'].get('user_info'):
                    print(f"   User: {data['body']['user_info']}")
    except Exception as e:
        print(f"   Error: {e}")

asyncio.run(test_login())
