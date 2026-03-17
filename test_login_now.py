#!/usr/bin/env python3
"""Quick login test"""

import asyncio
import websockets
import json

async def test():
    try:
        async with websockets.connect("ws://localhost:9090/ws", close_timeout=5) as ws:
            print("✓ Connected to server")

            msg = {"type": 1, "sequence_id": 1, "body": {"username": "test", "password": "test"}}
            await ws.send(json.dumps(msg))
            print("✓ Login message sent")

            try:
                resp = await asyncio.wait_for(ws.recv(), timeout=3)
                print(f"✓ Response: {resp}")
            except asyncio.TimeoutError:
                print("✗ No response (timeout)")

    except Exception as e:
        print(f"✗ Error: {e}")

asyncio.run(test())
