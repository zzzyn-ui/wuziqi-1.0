#!/usr/bin/env python3
"""Simple WebSocket login test"""

import asyncio
import websockets
import json

async def test_login():
    # Try port 9090
    uri = "ws://localhost:9090/ws"
    try:
        print(f"Connecting to {uri}...")
        async with websockets.connect(uri, close_timeout=5) as ws:
            print("✓ Connected!")

            # Test AUTH_LOGIN message
            login_msg = {
                "type": 1,
                "sequence_id": 12345,
                "timestamp": 1234567890,
                "body": {
                    "username": "testuser1",
                    "password": "testpass123"
                }
            }

            print(f"Sending: {json.dumps(login_msg)}")
            await ws.send(json.dumps(login_msg))
            print("✓ Message sent!")

            # Wait for response
            try:
                response = await asyncio.wait_for(ws.recv(), timeout=5)
                print(f"✓ Received: {response}")

                # Parse response
                data = json.loads(response)
                print(f"✓ Parsed: {data}")
                return True
            except asyncio.TimeoutError:
                print("✗ No response received (timeout)")
                return False

    except Exception as e:
        print(f"✗ Error: {e}")
        return False

if __name__ == "__main__":
    result = asyncio.run(test_login())
    if result:
        print("\n✓ Login test PASSED")
    else:
        print("\n✗ Login test FAILED")
