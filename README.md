# Bitbot Android

Android remote control app for humanoid robots. Connects to a robot backend via WebSocket and HTTP, providing a dual-joystick gamepad interface for real-time velocity control and a realtime data monitoring panel.

Based on the [bitbot_xbox](https://github.com/dknt0/bitbot_xbox) protocol.

See also: [BitbotCopilot](https://github.com/ZzzzzzS/BitbotCopilot) (desktop Qt/C++ version)

## Features

- **Dual virtual joysticks** — left joystick for yaw, right joystick for linear movement (x/y)
- **Policy mode switching** — Standing, Walking, Robust with configurable velocity limits
- **Action buttons** — Power On, Init Pose, Start State Machine, Run Policy
- **Emergency stop** — large E-STOP button with physical separation from other controls
- **Realtime data panel** — live telemetry table with kernel stats, device data grouped by type, and state name lookup
- **Panel switcher** — tap-to-toggle FAB to switch between Pilot and Data panels
- **Configurable velocity limits** — separate positive/negative limits per axis per policy mode
- **Debug overlay** — real-time velocity readout for operator monitoring
- **Auto-reconnect** — automatically reconnects when connection drops
- **Landscape immersive mode** — full-screen control panel with no system UI distractions

## Screenshots

| Home Screen | Pilot Panel | Data Panel |
|---|---|---|
| *Connection UI* | *Gamepad control panel* | *Realtime telemetry table* |

## Requirements

- Android 12 (API 31) or later
- A robot running a WebSocket server compatible with the bitbot_xbox protocol on port 12888

## Build

```bash
./gradlew assembleDebug
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or build a release APK:

```bash
./gradlew assembleRelease
```

## Usage

### 1. Connect to Robot

On the home screen, enter the robot's IP address and port (default: `127.0.0.1:12888`), then tap **Connect**.

### 2. Control Panel (Pilot)

The pilot screen shows a landscape gamepad layout:

```
  ┌────────┐                                       ┌────────┐
  │        │   [PowerOn] [InitPose]                │        │
  │  Yaw   │   [ Start ] [  Run   ]                │  Move  │
  │        │   [Stand] [Walk] [Robust]             │        │
  └────────┘                                       └────────┘
                          [ E-STOP ]
```

- **Left joystick (Yaw)** — controls rotation velocity
- **Right joystick (Move)** — controls forward/backward (Y axis) and lateral (X axis) velocity
- **Power On** — enables record mode and powers on the robot
- **Init Pose** — moves the robot to initial standing pose
- **Start** — starts the state machine (toggle)
- **Run** — starts policy inference
- **Stand / Walk / Robust** — switch between policy modes
- **E-STOP** — emergency stop (sends `stop` event)

### 3. Data Panel

Switch to the Data panel via the floating panel switcher (bottom-left corner). Shows:

- **Kernel stats bar** — period, state, process time, kernel time, CPU usage
- **Tabbed device table** — devices grouped by type (e.g., MujocoJoint), with sticky column headers
- **State names** — robot state IDs mapped to human-readable names

Data is polled at 10Hz via `request_data` WebSocket messages (only when the data panel is active).

### 4. Configure Velocity Limits

Go to **Settings** to configure maximum velocity per axis per policy mode. Each axis has separate positive (`+`) and negative (`-`) limits:

| Mode | vel_x [neg, pos] | vel_y [neg, pos] | vel_yaw [neg, pos] |
|---|---|---|---|
| Standing | [-1, 4] | [-1, 1] | [-3, 3] |
| Walking | [0, 0.6] | [0, 0] | [-1, 1] |
| Robust | [0, 1.5] | [0, 0] | [-0.6, 0.6] |

When a negative limit is set to 0, the corresponding joystick direction is disabled. Settings are persisted across app restarts.

## Development

### Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp (HTTP + WebSocket)
- Hilt dependency injection
- MVVM architecture with DataStore preferences

### Project Structure

```
app/src/main/java/com/bitbot/
├── BitbotApp.kt
├── MainActivity.kt              # Landscape + immersive mode
├── di/
│   ├── AppModule.kt             # DataStore provider
│   └── NetworkModule.kt         # OkHttp, Json, RobotApi providers
├── data/
│   ├── model/
│   │   ├── ConnectionState.kt
│   │   ├── ControlEvent.kt
│   │   └── RobotState.kt
│   ├── remote/
│   │   ├── api/RobotApi.kt      # HTTP endpoints
│   │   ├── websocket/WebSocketClient.kt  # WS + monitor_data parsing + polling
│   │   └── dto/
│   └── repository/RobotRepository.kt
├── domain/
│   ├── RepositoryInterfaces.kt
│   └── usecase/
├── ui/
│   ├── navigation/NavGraph.kt
│   ├── components/
│   │   └── PanelSwitcher.kt     # Floating panel switch FAB
│   ├── screens/
│   │   ├── home/                # Connection UI (IP/port)
│   │   ├── PanelHostScreen.kt   # Hosts Pilot or Data with switcher overlay
│   │   ├── pilot/               # Control panel (joysticks + buttons)
│   │   │   ├── PilotScreen.kt
│   │   │   ├── PilotViewModel.kt
│   │   │   └── components/
│   │   │       └── VirtualGamepad.kt
│   │   ├── data/                # Realtime data monitoring
│   │   │   ├── DataScreen.kt
│   │   │   └── DataViewModel.kt
│   │   └── settings/
│   └── theme/
└── util/
    ├── Constants.kt
    └── Extensions.kt
```

## Protocol

The app communicates with the robot backend using the bitbot_xbox WebSocket protocol:

- **WebSocket endpoint:** `ws://<host>:<port>/console`
- **HTTP endpoints:** `/monitor/headers`, `/monitor/stateslist`, `/setting/control/get`
- **Message format:** Double-serialized JSON text frames
- **Button events:** value `1` (fire) or `2` (toggle)
- **Velocity events:** value is `Double.toBits()` (int64 bitcast of double), sent at 100Hz
- **Data polling:** `request_data` sent at 10Hz, response is `monitor_data` with flat double array

## Authors

- **Dknt** — [github.com/dknt0](https://github.com/dknt0)
- **Claude Code** — [claude.ai/code](https://claude.ai/code)

## License

MIT
