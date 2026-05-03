# Mavlink Joystick

A clean, dark-themed kotlin app to control Flix/ArduPilot/PX4 drones over MAVLink (UDP).

![](app_screen.png)

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


## Build

- clone this project
- install Android Studio Panda 4 (if not installed)
- open the project Android Studio → Build → Run

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

## MAVLink Messages

| Direction | Message | Purpose |
|---|---|---|
| Send | `MANUAL_CONTROL` (69) | Control at 50 Hz |
| Send | `HEARTBEAT` (0) | Keep link alive |
| Send | `COMMAND_LONG` (76) | ARM/DISARM |
| Receive | `HEARTBEAT` (0) | Armed flag, link health |
| Receive | `ATTITUDE_QUATERNION` (31) | Roll/pitch for horizon |

## Safety Notes

- **Always test in a safe, open area.**
- Throttle holds position when released (real transmitter behaviour).
- All other axes spring back to centre on release.
- The app does **not** enforce geo-fencing or failsafes — those must be configured on the flight controller.

## Project Structure

```
app/src/main/java/com/goldarte/mavlinkjoystick/
├── MainActivity.kt                  # UI wiring, lifecycle
├── ConnectionDialogFragment.kt      # IP/port settings dialog
├── mavlink/
│   └── MavlinkManager.kt           # UDP socket, MAVLink v2 handling
└── views/
    ├── JoystickView.kt             # Custom circular joystick (throttle mode)
    └── ArtificialHorizonView.kt    # AHRS display (sky/ground + pitch ladder)
```
