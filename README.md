# Mavlink Joystick

User-friendly joystick for controlling vehicles and robots via MAVLink protocol. Supports Flix/ArduPilot/PX4 drones out-of-the-box.

![](app_screen.png)

## Features

- **MAVLink v2 over UDP** - control any vehicle or robot with MANUAL_CONTROL message support from your Android smartphone via WiFi
- **Customizable sticks** - size, appearance and even curve for each axis can be changed
- **Artificial horizon and compass** - intuitive orientation visualization from ATTITUDE_QUATERNION message
- **Status bar** - armed/voltage/mode display
- **Mavlink console** - for low-level autopilot setup

## Build

- Clone this project
- Install Android Studio Panda 4 (if not installed)
- Open the project in Android Studio → Build → Run

## Connect to drone

App listens 14550 udp port by default and connects to first drone, which sends MAVLink heartbeat. You can change listen port in connection settings.

## Safety Notes

- **Always test in a safe, open area.**
- Throttle holds position when released (real transmitter behaviour).
- All other axes spring back to centre on release.
- The app does **not** enforce geo-fencing or failsafes — those must be configured on the flight controller.
