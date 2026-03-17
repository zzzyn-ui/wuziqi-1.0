#!/usr/bin/env python3
"""Debug client to test server responses"""

import asyncio
import websockets
import json

async def test_login():
    uri = "ws://localhost:9091/ws"
    try:
        async with websockets.connect(uri) as ws:
            print("Connected to server")

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

            # Wait for response
            response = await ws.recv()
            print(f"Received: {response}")

            # Parse response
            data = json.loads(response)
            print(f"Parsed: {data}")

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(test_login())
