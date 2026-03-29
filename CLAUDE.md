# BitbotCopilot Android - Project Context

## Overview
BitbotCopilot Android is a mobile app for controlling a humanoid robot via WebSocket. The protocol is based on **bitbot_xbox** (`/home/dknt/Project/bitbot_xbox`), NOT the desktop BitbotCopilot Qt/C++ app.

## Technology Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Networking:** OkHttp (HTTP + WebSocket)
- **Dependency Injection:** Hilt
- **Architecture:** MVVM with Clean Architecture
- **Build System:** Gradle 8.6 with Kotlin DSL
- **Android Gradle Plugin:** 8.4.0
- **Minimum SDK:** Android 12 (API 31)
- **Target SDK:** Android 14 (API 34)
- **Orientation:** Landscape only (`sensorLandscape`)
- **System UI:** Immersive sticky mode (no nav bar)

## Project Structure

```
BitbotCopilot-Android/
├── app/src/main/java/com/bitbot/copilot/
│   ├── BitbotApp.kt
│   ├── MainActivity.kt              # Landscape + immersive mode
│   ├── di/
│   │   ├── AppModule.kt             # DataStore provider
│   │   └── NetworkModule.kt         # OkHttp, Json, RobotApi providers
│   ├── data/
│   │   ├── model/
│   │   │   ├── ConnectionState.kt
│   │   │   ├── ControlEvent.kt
│   │   │   └── RobotState.kt
│   │   ├── remote/
│   │   │   ├── api/RobotApi.kt
│   │   │   ├── websocket/WebSocketClient.kt
│   │   │   └── dto/
│   │   └── repository/RobotRepository.kt
│   ├── domain/
│   │   ├── RepositoryInterfaces.kt
│   │   └── usecase/
│   ├── ui/
│   │   ├── navigation/NavGraph.kt
│   │   ├── screens/
│   │   │   ├── home/                # Connection UI (IP/port)
│   │   │   ├── pilot/               # Control panel (joysticks + buttons)
│   │   │   │   ├── PilotScreen.kt
│   │   │   │   ├── PilotViewModel.kt
│   │   │   │   └── components/
│   │   │   │       └── VirtualGamepad.kt   # Joystick with raw pointer tracking
│   │   │   └── settings/
│   │   └── theme/
│   └── util/
│       ├── Constants.kt             # Event names, PolicyMode, velocity scaling
│       └── Extensions.kt            # normalizeJoystickValue (deadzone)
├── test_server.py                   # Mock robot server for testing
├── build.gradle.kts
└── gradle/
```

## Protocol (bitbot_xbox Reference)

### WebSocket Endpoint
- **URL:** `ws://<host>:<port>/console`
- **Subscription:** Send `{"type":"request_data","data":""}` on connect
- **Messages are TEXT**, with **double-serialized JSON**

### Message Format (Send to Robot)
```json
{"type":"events","data":"{\"events\":[{\"name\":\"EVENT\",\"value\":N}]}"}
```

### Button Events: value `1` (fire) or `2` (toggle)
`stop`, `power_on`, `start`, `init_pose`, `run_policy`, `enable_standing_policy`, `enable_warking_policy`, `enable_robust_policy`, `nav_trigger`, `enable_record`

### Velocity Events: value is `Double.toBits()` (int64 bitcast)
- `set_vel_x`, `set_vel_y`, `set_vel_w` — sent at 100Hz
- **Important:** `-(0.0f)` produces `-0.0f` which encodes as `Long.MIN_VALUE`. `scaleVelocity()` returns `0.0` explicitly when input is zero.

### Gamepad → Event Mapping (from bitbot_frontend.hpp)
| Gamepad | Event | Value |
|---|---|---|
| A | `init_pose` | Fire (1) |
| B | `start` | Toggle (2) |
| X | `enable_standing_policy` | Fire (1) |
| Y | `enable_record` + `power_on` | Fire (1) |
| LB | `enable_warking_policy` | Fire (1) |
| RB | `enable_robust_policy` | Fire (1) |
| RT > 0.9 | `stop` | Fire (1) |
| RS click | `run_policy` | Fire (1) |
| Right Y | `set_vel_x` | Velocity |
| -Right X | `set_vel_y` | Velocity |
| -Left X | `set_vel_w` | Velocity |

### Velocity Scaling by Policy Mode
Separate positive/negative limits per axis. Joystick center = 0 velocity.
Positive input scales to `posLimit`, negative input scales to `negLimit` magnitude.
| Mode | vel_x [neg, pos] | vel_y [neg, pos] | vel_yaw [neg, pos] |
|---|---|---|---|
| Standing | [-1, 4] | [-1, 1] | [-3, 3] |
| Walking | [0, 0.6] | [0, 0] | [-1, 1] |
| Robust | [0, 1.5] | [0, 0] | [-0.6, 0.6] |

These limits are configurable via Settings (persisted in DataStore). Keys: `VelocityPrefs.{mode}_{axis}_{pos/neg}`.

## Build & Install

```bash
cd /home/dknt/Project/mobile/BitbotCopilot-Android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing
```bash
python3 test_server.py --port 12888
```

## Key Implementation Notes
- **VirtualJoystick** uses `awaitEachGesture` + raw `awaitPointerEvent` (no touch slop)
- Layout values (size, maxDragPx) are **state variables** read dynamically inside pointerInput — NOT captured as immutable vals
- Connection retry: `WebSocketClient.disconnect()` clears `webSocket=null, currentUrl=null` so reconnect works after failure
- `onFailure`/`onClosing`/`onClosed` all reset state to allow fresh connections
- **PilotScreen layout**: Landscape gamepad — left Yaw joystick, center button panel (2x2 actions + 3 policy chips + E-STOP), right Move joystick
- **Velocity config**: 18 DataStore keys (3 policies × 3 axes × pos/neg), `SettingsUiState.velConfig` is a `Map<String, Double>`, `PilotViewModel` caches 6 values per active policy mode
- **scaleVelocity**: `scaleVelocity(input, posLimit, negLimit)` — input > 0 → input × posLimit, input < 0 → input × negLimit, input == 0 → 0.0. Guards against -0.0 via `if (result == 0.0) 0.0`.
- **Computed velocities** in `PilotUiState` (`velX`, `velY`, `velW`) updated every 100Hz tick, used by debug panel
- **Start button** uses `TOGGLE` (value 2), all other buttons use `FIRE` (value 1)

## Environment
- **Android SDK:** `/usr/lib/android-sdk`
- **Java:** OpenJDK 21
- **Default robot host:** 127.0.0.1:12888
