#!/usr/bin/env python3
"""
Mock robot server for testing BitbotCopilot Android app.

Simulates the bitbot backend:
  - HTTP GET /monitor/stateslist
  - HTTP GET /monitor/headers
  - HTTP GET /setting/control/get
  - WebSocket at /console (port 12888)

Usage:
  python3 test_server.py [--port 12888] [--host 0.0.0.0]
"""

import argparse
import asyncio
import json
import struct
import time
from aiohttp import web

# --- HTTP Handlers ---

async def handle_stateslist(request):
    """GET /monitor/stateslist"""
    data = {
        "states": [
            {"id": 0, "name": "standing"},
            {"id": 1, "name": "walking"},
            {"id": 2, "name": "robust"},
        ]
    }
    return web.json_response(data)

async def handle_headers(request):
    """GET /monitor/headers"""
    data = {
        "kernel": ["state", "periods_count", "period(ms)", "process_t(ms)", "kernel_t(ms)"],
        "bus": {
            "devices": [
                {
                    "name": "left_hip_pitch_joint",
                    "type": "MujocoJoint",
                    "headers": ["mode", "actual_position", "target_position",
                                "actual_velocity", "target_velocity",
                                "actual_torque", "target_torque"]
                },
                {
                    "name": "right_hip_pitch_joint",
                    "type": "MujocoJoint",
                    "headers": ["mode", "actual_position", "target_position",
                                "actual_velocity", "target_velocity",
                                "actual_torque", "target_torque"]
                },
                {
                    "name": "imu",
                    "type": "IMU",
                    "headers": ["quat_w", "quat_x", "quat_y", "quat_z",
                                "gyro_x", "gyro_y", "gyro_z",
                                "acc_x", "acc_y", "acc_z"]
                },
            ]
        },
        "extra": ["policy_mode", "run_state"]
    }
    return web.json_response(data)

async def handle_control(request):
    """GET /setting/control/get"""
    data = [
        {"event": "stop", "kb_key": "Escape"},
        {"event": "start", "kb_key": "Enter"},
        {"event": "init_pose", "kb_key": "i"},
        {"event": "run_policy", "kb_key": "r"},
        {"event": "enable_standing_policy", "kb_key": "1"},
        {"event": "enable_warking_policy", "kb_key": "2"},
        {"event": "enable_robust_policy", "kb_key": "3"},
        {"event": "nav_trigger", "kb_key": "n"},
        {"event": "enable_record", "kb_key": "t"},
        {"event": "power_on", "kb_key": "p"},
    ]
    return web.json_response(data)


# --- WebSocket Handler ---

class MockRobot:
    """Tracks received events and sends mock monitor data."""

    def __init__(self):
        self.vel_x = 0.0
        self.vel_y = 0.0
        self.vel_w = 0.0
        self.buttons_pressed = set()
        self.policy_mode = "standing"
        self.event_log = []
        self.running = True

    def handle_message(self, raw_text):
        """Parse the double-serialized JSON protocol from bitbot_xbox."""
        try:
            outer = json.loads(raw_text)
        except json.JSONDecodeError:
            print(f"  [WARN] Failed to parse outer JSON: {raw_text[:100]}")
            return

        msg_type = outer.get("type", "")

        # Handle request_data subscription
        if msg_type == "request_data":
            print("  [SUB] request_data received")
            return

        data_str = outer.get("data", "")

        if not isinstance(data_str, str):
            print(f"  [WARN] data is not a string: {type(data_str)}")
            return

        try:
            inner = json.loads(data_str)
        except json.JSONDecodeError:
            print(f"  [WARN] Failed to parse inner JSON: {data_str[:100]}")
            return

        events = inner.get("events", [])
        for ev in events:
            name = ev.get("name", "?")
            value = ev.get("value", 0)

            # Velocity events: value is Double.toBits() (int64 bitcast)
            if name in ("set_vel_x", "set_vel_y", "set_vel_w"):
                # Convert int64 bit pattern back to double
                double_val = struct.unpack('d', struct.pack('q', int(value)))[0]
                if name == "set_vel_x":
                    self.vel_x = double_val
                elif name == "set_vel_y":
                    self.vel_y = double_val
                elif name == "set_vel_w":
                    self.vel_w = double_val
                print(f"  vel: {name} = {double_val:.4f}")
            else:
                # Button events: value is 1 (fire) or 2 (toggle)
                print(f"  btn: {name} = {value}")
                self.event_log.append((name, value, time.time()))

    def make_monitor_data(self):
        """Generate flat data array matching headers structure."""
        # kernel: state, periods_count, period, process_t, kernel_t
        kernel = [1.0, 100.0, 10.0, 0.5, 0.3]

        # joints (2 x 7 values)
        joints = []
        for _ in range(2):
            joints.extend([0.0, 0.1, 0.1, 0.0, 0.0, 0.5, 0.5])

        # imu (10 values)
        imu = [1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 9.81]

        # extra: policy_mode, run_state
        extra = [0.0, 1.0]

        return kernel + joints + imu + extra


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    print("[WS] Client connected")
    robot = MockRobot()
    subscribed = False

    # Send periodic monitor data at ~10Hz (only after request_data)
    async def send_monitor_data():
        nonlocal subscribed
        while robot.running and not ws.closed:
            if not subscribed:
                await asyncio.sleep(0.1)
                continue
            data = robot.make_monitor_data()
            msg = json.dumps({"type": "monitor_data", "data": json.dumps({"data": data})})
            try:
                await ws.send_str(msg)
            except Exception:
                break
            await asyncio.sleep(0.1)

    monitor_task = asyncio.create_task(send_monitor_data())

    try:
        async for msg in ws:
            if msg.type == web.WSMsgType.TEXT:
                if msg.data and '"request_data"' in msg.data:
                    subscribed = True
                    print("  [SUB] Client subscribed to monitor data")
                robot.handle_message(msg.data)
            elif msg.type == web.WSMsgType.ERROR:
                print(f"[WS] Error: {ws.exception()}")
    finally:
        robot.running = False
        monitor_task.cancel()
        print("[WS] Client disconnected")

    return ws


# --- Main ---

async def main():
    parser = argparse.ArgumentParser(description="Mock robot server for BitbotCopilot testing")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=12888)
    args = parser.parse_args()

    app = web.Application()
    app.router.add_get("/monitor/stateslist", handle_stateslist)
    app.router.add_get("/monitor/headers", handle_headers)
    app.router.add_get("/setting/control/get", handle_control)
    app.router.add_get("/console", websocket_handler)

    print(f"Mock robot server starting on http://{args.host}:{args.port}")
    print(f"  HTTP: /monitor/stateslist, /monitor/headers, /setting/control/get")
    print(f"  WS:   ws://{args.host}:{args.port}/console")
    print()

    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, args.host, args.port)
    await site.start()

    # Keep running
    try:
        while True:
            await asyncio.sleep(3600)
    except asyncio.CancelledError:
        pass
    finally:
        await runner.cleanup()


if __name__ == "__main__":
    asyncio.run(main())
