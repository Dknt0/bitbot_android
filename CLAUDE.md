# Bitbot Android - Project Context

## Overview
Bitbot Android is a mobile app for controlling a humanoid robot via WebSocket. The protocol is based on **bitbot_xbox** (`/home/dknt/Project/bitbot_xbox`), NOT the desktop BitbotCopilot Qt/C++ app.

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
Bitbot-Android/
├── app/src/main/java/com/bitbot/
│   ├── BitbotApp.kt
│   ├── MainActivity.kt              # Landscape + immersive mode
│   ├── di/
│   │   ├── AppModule.kt             # DataStore provider
│   │   └── NetworkModule.kt         # OkHttp, Json, RobotApi providers
│   ├── data/
│   │   ├── model/
│   │   │   ├── ButtonConfig.kt      # Configurable button + layout codec + default layout
│   │   │   ├── ConnectionState.kt
│   │   │   ├── ControlEvent.kt
│   │   │   └── RobotState.kt
│   │   ├── remote/
│   │   │   ├── api/RobotApi.kt      # HTTP: headers, stateslist, control mappings
│   │   │   ├── websocket/WebSocketClient.kt  # WS + monitor_data parsing + polling
│   │   │   └── dto/                 # HeadersResponseDto, DeviceHeadersDto, etc.
│   │   └── repository/RobotRepository.kt
│   ├── domain/
│   │   ├── RepositoryInterfaces.kt
│   │   └── usecase/
│   ├── ui/
│   │   ├── navigation/NavGraph.kt   # Routes: home, panel_host/{initialPanel}
│   │   ├── components/
│   │   │   └── PanelSwitcher.kt     # Floating FAB to switch Pilot ↔ Data
│   │   ├── screens/
│   │   │   ├── home/                # Connection UI (IP/port)
│   │   │   ├── PanelHostScreen.kt   # Hosts Pilot or Data with PanelSwitcher overlay
│   │   │   ├── pilot/               # Control panel (joysticks + buttons)
│   │   │   │   ├── PilotScreen.kt
│   │   │   │   ├── PilotViewModel.kt
│   │   │   │   ├── editor/          # Button layout editor (add/move/size/color)
│   │   │   │   │   ├── ButtonEditorScreen.kt
│   │   │   │   │   └── ButtonEditorViewModel.kt
│   │   │   │   └── components/
│   │   │   │       └── VirtualGamepad.kt   # Joystick with raw pointer tracking
│   │   │   ├── data/                # Realtime data monitoring panel
│   │   │   │   ├── DataScreen.kt    # Kernel stats bar + tabbed device table
│   │   │   │   └── DataViewModel.kt # Fetches headers, polls monitor_data, parses rows
│   │   │   └── settings/
│   │   └── theme/
│   └── util/
│       ├── Constants.kt             # Event names, PolicyMode, velocity scaling
│       └── Extensions.kt            # normalizeJoystickValue (deadzone)
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

### Button Events: value `1` (key Down / press) or `2` (key Up / release)
`stop`, `power_on`, `start`, `init_pose`, `run_policy`, `enable_standing_policy`, `enable_warking_policy`, `enable_robust_policy`, `nav_trigger`, `enable_record`

Configurable panel buttons send Down on press and Up on release (like the desktop reference frontend; `start` only acts on Up). Internal events (`policy_switch`, `velo_*`, `set_vel_*`) are never user buttons — see `Constants.ButtonEvents.isButtonEvent` allow-list. The server's full event list comes from HTTP `GET /setting/control/get` (`List<ControlMappingDto>`), fetched by `RobotRepository.connect()`.

### Velocity Events: value is `Double.toBits()` (int64 bitcast)
- `set_vel_x`, `set_vel_y`, `set_vel_w` — sent at 100Hz
- **Important:** `-(0.0f)` produces `-0.0f` which encodes as `Long.MIN_VALUE`. `scaleVelocity()` returns `0.0` explicitly when input is zero.

### Monitor Data (Robot → App)
- **Polling:** App sends `{"type":"request_data","data":""}` at 10Hz (only when Data panel is active)
- **Response:** `{"type":"monitor_data","data":"{\"data\":[N,N,...]}"}`  — double-serialized JSON with flat double array
- **Data layout:** kernel values first, then all device headers sequentially, then extra values
- **Headers** fetched via HTTP `GET /monitor/headers` — returns `HeadersResponseDto { kernel, bus { devices [{ name, type, headers }] }, extra }`
- **State names** from HTTP `GET /monitor/stateslist` — maps state IDs to human-readable names

### Gamepad → Event Mapping (from bitbot_frontend.hpp; the app's default button layout mirrors it, except PowerOn no longer auto-sends `enable_record` — add a separate Record button if needed)
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

## Commit Convention
- Commits made by ZCode carry a `Co-Authored-By: ZCode <noreply@z.ai>` trailer.
- ZCode is credited in the README Authors list (between Dknt and Claude Code); keep it there on README rewrites.

## Build & Install

```bash
cd /home/dknt/Project/bitbot_android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Key Implementation Notes
- **VirtualJoystick** uses `awaitEachGesture` + raw `awaitPointerEvent` (no touch slop)
- Layout values (size, maxDragPx) are **state variables** read dynamically inside pointerInput — NOT captured as immutable vals
- Connection retry: `WebSocketClient.disconnect()` clears `webSocket=null, currentUrl=null` so reconnect works after failure
- `onFailure`/`onClosing`/`onClosed` all reset state to allow fresh connections
- **PilotScreen layout**: Landscape gamepad — left Yaw joystick, right Move joystick (fixed), E-STOP fixed at bottom center, plus user-configurable buttons positioned freely over the panel (normalized x/y → `BiasAlignment`). Buttons persist as JSON in DataStore key `button_layout` (`ButtonConfig` list via `ButtonLayoutCodec`); falls back to `ButtonLayouts.defaultLayout()` until the user saves a layout.
- **Configurable buttons**: press sends value `1` (Down), release sends `2` (Up), detected via `PressInteraction` on the button's `interactionSource`. Buttons whose event maps to a `PolicyMode` switch the local mode (velocity limits) and render highlighted when active. Which events may become buttons is decided by the allow-list `Constants.ButtonEvents.isButtonEvent` (fixed set + `enable_.+_policy` regex).
- **Button editor** (`button_editor` route, Tune icon on pilot top bar): only meaningful while connected — the add-list comes from `RobotRepository.availableEvents()` (server `/setting/control/get`, fetched on `connect()`; hardcoded fallback). Edits are in-memory until Save writes to DataStore; `PilotViewModel` observes the DataStore key so changes apply on return.
- **Numeric settings fields** (`DecimalField`/`IntField` in `ui/components/NumberFields.kt`): keep raw text state, select-all on focus, commit parsed values on change, reformat only when unfocused — never round-trip text through the stored value.
- **Velocity config**: 18 DataStore keys (3 policies × 3 axes × pos/neg), `SettingsUiState.velConfig` is a `Map<String, Double>`, `PilotViewModel` caches 6 values per active policy mode
- **scaleVelocity**: `scaleVelocity(input, posLimit, negLimit)` — input > 0 → input × posLimit, input < 0 → input × negLimit, input == 0 → 0.0. Guards against -0.0 via `if (result == 0.0) 0.0`.
- **Computed velocities** in `PilotUiState` (`velX`, `velY`, `velW`) updated every 100Hz tick, used by debug panel
- **Start button** acts on release (kernel `start` only fires on key Up); all buttons send Down+Up pairs
- **Data panel** fetches headers via HTTP (`/monitor/headers`) and polls `request_data` at 10Hz only when active. `startDataPolling()`/`stopDataPolling()` called from `DataViewModel` init/onCleared. Connection state observer retries polling on reconnect.
- **PanelSwitcher**: Tap-to-toggle FAB at bottom-left, switches between Pilot and Data panels. Uses `AnimatedVisibility` with horizontal slide.
- **PanelHostScreen**: Wraps PilotScreen and DataScreen as switchable composables. Route: `panel_host/{initialPanel}`.
- **Data table**: LazyColumn with stickyHeader, fixed column widths (Name=110dp, Values=64dp, Mode=28dp), shared horizontal ScrollState for sync scrolling. Kernel stats bar shows deduplicated labels in top bar.
- **HomeViewModel** observes DataStore as a `Flow` (not `.first()`) so settings changes from Settings screen are reflected immediately.
- **fetchHeaders()** uses `withContext(Dispatchers.IO)` to avoid `NetworkOnMainThreadException` when called from `viewModelScope` (which uses `Dispatchers.Main`).
- **Auto-reconnect**: RobotRepository observes WebSocket state; when disconnected/error with a previous URL, reconnects after 2s delay.

## Environment
- **Android SDK:** `/usr/lib/android-sdk`
- **Java:** OpenJDK 21
- **Default robot host:** 127.0.0.1:12888
