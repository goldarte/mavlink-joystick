# DroneJoystick — MAVLink RC Android App

A clean, dark-themed Android controller for ArduPilot/PX4 drones over MAVLink (UDP).

---

## Features

| Feature | Detail |
|---|---|
| **Left stick** | Throttle (Y, no spring) + Yaw (X, spring) — Mode 2 |
| **Right stick** | Pitch (Y, spring) + Roll (X, spring) |
| **Artificial Horizon** | Live roll/pitch from ATTITUDE MAVLink message |
| **Arm status** | Parsed from HEARTBEAT, colour-coded |
| **ARM / DISARM** | One tap; long-press = instant disarm |
| **Connection dialog** | Tap ⚙ LINK to set target IP + UDP port |
| **Link indicator** | Green = heartbeat received < 3 s ago |

---

## Build

### Prerequisites
- Android Studio Giraffe (2023.3) or newer
- Android SDK 34
- Kotlin 1.9+

### Steps
```bash
git clone <this-repo>
cd DroneJoystick
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio → Build → Run.

---

## Connecting

### SITL (Simulator)
```bash
# Start ArduCopter SITL
sim_vehicle.py -v ArduCopter --out=udp:192.168.x.x:14550
```
Open the app → ⚙ LINK → set IP to your PC's IP, port 14550.

### Real drone via Wi-Fi telemetry (ESP32 MAVLink bridge)
Default IP: **192.168.4.1**, port **14550**.  
The AP SSID is usually `ArduPilot` or `Telemetry`.

### Mission Planner / QGroundControl forwarder
Enable UDP output in your GCS and point it at your phone's IP.

---

## MAVLink Messages

| Direction | Message | Purpose |
|---|---|---|
| Send | `RC_CHANNELS_OVERRIDE` (70) | Control at 20 Hz |
| Send | `HEARTBEAT` (0) | Keep link alive |
| Send | `COMMAND_LONG` (76) | ARM/DISARM |
| Receive | `HEARTBEAT` (0) | Armed flag, link health |
| Receive | `ATTITUDE` (30) | Roll/pitch for horizon |

Channel mapping (Mode 2):
- **CH1** = Roll
- **CH2** = Pitch  
- **CH3** = Throttle
- **CH4** = Yaw

---

## Safety Notes

- **Always test in a safe, open area.**
- Throttle holds position when released (real transmitter behaviour).
- All other axes spring back to centre on release.
- The app does **not** enforce geo-fencing or failsafes — those must be configured on the flight controller.

---

## Project Structure

```
app/src/main/java/com/dronejoystick/
├── MainActivity.kt                  # UI wiring, lifecycle
├── ConnectionDialogFragment.kt      # IP/port settings dialog
├── mavlink/
│   └── MavlinkManager.kt           # UDP socket, MAVLink v1 encode/decode
└── views/
    ├── JoystickView.kt             # Custom circular joystick (throttle mode)
    └── ArtificialHorizonView.kt    # AHRS display (sky/ground + pitch ladder)
```
