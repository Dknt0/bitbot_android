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
│   │   ├── plot/
│   │   │   └── PlotRecorder.kt      # @Singleton recorder: channel registry, ring buffers, CSV builder
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
│   │   │   ├── plot/                # Realtime data plot panel (record/freeze/save)
│   │   │   │   ├── PlotScreen.kt    # Record button, legend, channel picker, save CSV/PNG
│   │   │   │   ├── PlotViewModel.kt # Config persistence, recording control, MediaStore save
│   │   │   │   └── components/PlotCanvas.kt  # Custom canvas plot: axes, pan/zoom, decimation
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
- **Polling:** clients call `acquireDataPolling(rateHz)`/`releaseDataPolling(handle)` (refcounted). There is exactly **one** `request_data` loop app-wide running at the **max** requested rate — backend replies are untagged, so parallel loops would corrupt sample cadence. The loop re-reads the current socket each tick and survives disconnects/reconnects.
- **Response:** `{"type":"monitor_data","data":"{\"data\":[N,N,...]}"}`  — double-serialized JSON with flat double array
- **Data layout:** kernel values first, then all device headers sequentially, then extra values
- **Headers** fetched via HTTP `GET /monitor/headers` — returns `HeadersResponseDto { kernel, bus { devices [{ name, type, headers }] }, extra }`
- **State names** from HTTP `GET /monitor/stateslist` — maps state IDs to human-readable names

### Plot Panel
- Third panel (`PanelType.PLOT`): select any number of channels (stable keys `kernel:x` / `device:header` / `extra:x`), record at a configurable rate (10/20/50/100 Hz, horizon 1–60 s) into per-channel ring buffers trimmed to the horizon; recording continues in the background (PlotRecorder is an @Singleton that owns its polling handle).
- X axis = **frontend time in seconds**: each received frame advances time by exactly `1/rateHz` (the configured 10/20/50/100 Hz), whatever the backend's internal loop rate — the app is the clock, which keeps the axis stable. The follow window spans exactly the configured horizon. Each frame's time is stored in a shared x ring (`PlotRecorder.xSeries`); curves are drawn at real x positions. Status bar and CSV (`time_s` column) use the same seconds-since-recording-start axis.
- Channel picker is a full-screen tree: groups (kernel/devices/extra) collapse/expand; tri-state checkbox on a group selects all its channels; the search box switches to a flat cross-group result list.
- Custom `PlotCanvas` on android.graphics.Canvas (same `PlotRenderer` draws the live view and PNG exports): nice-number ticks, per-pixel min/max decimation, drag pans (x/y separately), pinch zooms both axes; FOLLOW / AUTO Y / RESET chips re-enable auto modes.
- Save when stopped: CSV (`kernel_count` + one column per channel, late-added channels have leading empty cells) and PNG snapshot → `Downloads/Bitbot/` via MediaStore (no permission needed).
- Memory: ring buffers are hard-capped at horizon × rate samples; `series()` is a zero-copy view of the deque (main-thread only); UI-state idx updates throttled to 5 Hz while the canvas redraws from the recorder's version flow.
- Settings persist in DataStore: `plot_channels` (ordered JSON keys → curve colors by index), `plot_rate_hz`, `plot_horizon_seconds`.
- DataViewModel throttles `monitorData` via `.sample(100)` so the table stays at 10 Hz even when the recorder polls at 50 Hz.

### Gamepad → Event Mapping (from bitbot_frontend.hpp; the app's default button layout mirrors it; the PowerOn button always sends `enable_record` + `power_on` together, like bitbot_xbox)
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

## Debug Variant
`assembleDebug` produces a second app installed **alongside** the release app: applicationId `com.bitbot.debug`, label "Bitbot Debug" (`applicationIdSuffix` in the debug build type). It carries `WS-TX` logcat tags for every outgoing button/velocity frame and panel-activity transitions — filter with `adb logcat -s WS-TX`.

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
- **Pilot command gating**: `PilotViewModel.setPanelActive()` (driven by PilotScreen's `DisposableEffect`) starts/stops the 100 Hz velocity loop — the ViewModel outlives panel switches (nav-entry scoped), so Data/Plot panels must not keep streaming `set_vel_*`. No events are sent on deactivate: several apps may share one backend (one piloting, others monitoring) and a monitor must never publish velocity.
- **PilotScreen layout**: Landscape gamepad — left Yaw joystick, right Move joystick (fixed), E-STOP fixed at bottom center, plus user-configurable buttons positioned freely over the panel (normalized x/y → `BiasAlignment`). Buttons persist as JSON in DataStore key `button_layout` (`ButtonConfig` list via `ButtonLayoutCodec`); falls back to `ButtonLayouts.defaultLayout()` until the user saves a layout.
- **Configurable buttons**: press sends value `1` (Down), release sends `2` (Up), detected via `PressInteraction` on the button's `interactionSource`. Buttons whose event maps to a `PolicyMode` switch the local mode (velocity limits) and render highlighted when active. Which events may become buttons is decided by the allow-list `Constants.ButtonEvents.isButtonEvent` (fixed set + `enable_.+_policy` regex).
- **Button editor** (`button_editor` route, Tune icon on pilot top bar): only meaningful while connected — the add-list comes from `RobotRepository.availableEvents()` (server `/setting/control/get`, fetched on `connect()`; hardcoded fallback). Edits are in-memory until Save writes to DataStore; `PilotViewModel` observes the DataStore key so changes apply on return.
- **Numeric settings fields** (`DecimalField`/`IntField` in `ui/components/NumberFields.kt`): keep raw text state, select-all on focus, commit parsed values on change, reformat only when unfocused — never round-trip text through the stored value.
- **Velocity config**: 18 DataStore keys (3 policies × 3 axes × pos/neg), `SettingsUiState.velConfig` is a `Map<String, Double>`, `PilotViewModel` caches 6 values per active policy mode
- **scaleVelocity**: `scaleVelocity(input, posLimit, negLimit)` — input > 0 → input × posLimit, input < 0 → input × negLimit, input == 0 → 0.0. Guards against -0.0 via `if (result == 0.0) 0.0`.
- **Computed velocities** in `PilotUiState` (`velX`, `velY`, `velW`) updated every 100Hz tick, used by debug panel
- **Start button** acts on release (kernel `start` only fires on key Up); all buttons send Down+Up pairs
- **Data panel** fetches headers via HTTP (`/monitor/headers`) and acquires data polling at 10 Hz in `DataViewModel` init (released in onCleared); the shared poll loop handles reconnects internally, and `monitorData` is consumed through `.sample(100)` to keep table updates at 10 Hz.
- **PanelSwitcher**: Tap-to-toggle FAB at bottom-left, switches between Pilot and Data panels. Uses `AnimatedVisibility` with horizontal slide.
- **PanelHostScreen**: Wraps PilotScreen, DataScreen and PlotScreen as switchable composables. Route: `panel_host/{initialPanel}`. All navigations use `launchSingleTop` — a double-tap must never stack duplicate panel entries, each of which owns ViewModels (velocity loop / polling) that only stop when popped.
- **Data panel polling** is gated like the pilot loop: `DataViewModel.setPanelActive()` acquires/releases the shared poll handle on composition, so covered or zombie entries never keep `request_data` running.
- **Data table**: LazyColumn with stickyHeader, fixed column widths (Name=110dp, Values=64dp, Mode=28dp), shared horizontal ScrollState for sync scrolling. Kernel stats bar shows deduplicated labels in top bar.
- **HomeViewModel** observes DataStore as a `Flow` (not `.first()`) so settings changes from Settings screen are reflected immediately.
- **fetchHeaders()** uses `withContext(Dispatchers.IO)` to avoid `NetworkOnMainThreadException` when called from `viewModelScope` (which uses `Dispatchers.Main`).
- **Auto-reconnect**: RobotRepository observes WebSocket state; when disconnected/error with a previous URL, reconnects after 2s delay.

## Environment
- **Android SDK:** `/usr/lib/android-sdk`
- **Java:** OpenJDK 21
- **Default robot host:** 127.0.0.1:12888
