# Install Mavlink Joystick

The Android and iOS apps are distributed as assets of the GitHub releases in
[github.com/goldarte/mavlink-joystick/releases](https://github.com/goldarte/mavlink-joystick/releases):
there is no store link, so a released build is a file you install yourself. This
page describes installing one. To build the app from the sources instead, see
[BUILD.md](./BUILD.md).

## Release assets

Every release carries one file per platform, named after the release tag:

| Platform | Asset                                 | Signature                            | Installable as is        |
| -------- | ------------------------------------- | ------------------------------------ | ------------------------ |
| Android  | `mavlink-joystick-<tag>.apk`          | signed with the project release key  | yes                      |
| Android  | `mavlink-joystick-<tag>-unsigned.apk` | none                                 | no, must be signed first |
| iOS      | `mavlink-joystick-<tag>-unsigned.ipa` | none                                 | no, must be re-signed    |

`<tag>` is the release tag, so release `v2.0` holds `mavlink-joystick-v2.0.apk`.

The `-unsigned` Android APK is only uploaded when the build that produced the
tag had no signing secrets - a build of a fork, for example. The iOS IPA is
always unsigned, because no Apple signing credentials are kept in CI. The
`-unsigned` marker is kept when the artifact is renamed for the release, so a
signed and an unsigned build cannot be confused.

Download an asset from the [latest release](https://github.com/goldarte/mavlink-joystick/releases/latest) page, or from the command line:

```shell
TAG=v2.0
# The URL of that exact release, which works for prereleases too:
curl -LO "https://github.com/goldarte/mavlink-joystick/releases/download/$TAG/mavlink-joystick-$TAG.apk"

# The same asset from the newest non-prerelease release:
curl -LO "https://github.com/goldarte/mavlink-joystick/releases/latest/download/mavlink-joystick-$TAG.apk"

# A specific release, with the GitHub CLI:
gh release download "$TAG" -R goldarte/mavlink-joystick -p '*.apk'
```

`releases/latest/download/` resolves to the newest release that is not marked as
a pre-release, so it fails for a tag that was published with `--prerelease`.

## Android

Requires Android 7.0 (API 24) or newer. The app is landscape-only.

### Install the APK

1. Download the APK on the phone - in the browser, over USB, or from a cloud
   drive - and open it from the **Downloads** app or a file manager.
2. Android asks for permission to install apps from that source the first time.
   Allow it for the app that opened the file (usually the browser or the file
   manager) and go back.
3. Google Play Protect may warn that the app is from an unknown developer and
   that it has not been scanned. Choose **Install anyway**; the warning appears
   because the app is distributed outside the Play Store, not because the APK
   was tampered with.
4. Open **Mavlink Joystick** from the launcher.

The same file can be installed from a computer with the Android platform tools,
which is useful when the phone has no file manager:

```shell
adb install -r mavlink-joystick-v2.0.apk
```

### Updating

Install the newer APK over the installed app; settings and connection profiles
are kept. Do not uninstall the old version first - that clears the app's data.

An update only installs when it is signed with the same key as the installed
app. If Android refuses with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or
`App not installed`, the installed copy was signed with a different key - your
own build, for example. Uninstall it first and install again.

### If the release only has an unsigned APK

`mavlink-joystick-v2.0-unsigned.apk` cannot be installed as it is: Android
rejects an APK without a signature. Sign it with your own key, using the
`apksigner` from the build-tools of the Android SDK:

```shell
apksigner sign \
  --ks release.keystore --ks-key-alias mavlinkjoystick \
  --out mavlink-joystick-signed.apk \
  mavlink-joystick-v2.0-unsigned.apk
```

The result installs over anything signed with the same key, and never over an
app signed with the project's release key - see [BUILD.md](./BUILD.md) for how
that key is configured.

## iOS

Requires iOS 18.2 or newer on a 64-bit device. Only the device build is
published; a simulator build cannot be installed on an iPhone or an iPad. 18.2 is
the deployment target of the Xcode project, so an older device needs a build with
a lower `IPHONEOS_DEPLOYMENT_TARGET`, see [BUILD.md](./BUILD.md).

### Sign and install with a sideloading tool

The simplest route is a sideloading tool - [SideStore](https://sidestore.io),
[AltStore](https://altstore.io) or [Sideloadly](https://sideloadly.io) - which
re-signs the app and installs it in one step:

1. Install the tool on a computer and connect the phone by cable. On Windows the
   Apple mobile device driver has to be installed as well; it comes with iTunes.
   Unlock the phone and accept **Trust This Computer**.
2. Select the downloaded `.ipa` and the connected device.
3. Sign in with an Apple ID. The tool registers the device with the account,
   creates a development signing certificate and a provisioning profile, and
   installs the app under its own bundle identifier.
4. Launch the app. A free Apple ID is enough, but the signature then expires
   after 7 days and has to be refreshed by the tool; a paid Apple Developer
   Program account keeps it valid for a year. The developer certificate has to
   be trusted once on the device - see below.

The tools also work over Wi-Fi in most setups, which is handy when the phone is
mounted on the vehicle.

### Install with Xcode

With a Mac you can build and install the app yourself instead of re-signing a
release asset - that path signs the app with your own team automatically:

- open [iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode,
- set your team in the **Signing & Capabilities** tab of the target,
- connect the device and run the scheme.

The steps are the ones from [BUILD.md](./BUILD.md); nothing in the release asset
is needed for this route.

### Trust the developer certificate

iOS refuses to start an app from a development certificate until it is
trusted on the device:

1. Open **Settings → General → VPN & Device Management**.
2. Tap the developer app under **Developer App** and confirm **Trust**.

The entry only exists after the first installation attempt. Launch the app again
once it is trusted.

### Re-signing manually

Re-signing by hand is possible with the `codesign` command from the Xcode
command line tools, an Apple development certificate, and a provisioning
profile that covers the device's UDID:

```shell
# 1. Unpack the archive.
unzip mavlink-joystick-v2.0-unsigned.ipa -d ipa

# 2. Embed a development profile that covers this device.
cp Your.mobileprovision ipa/Payload/MavlinkJoystickIOS.app/embedded.mobileprovision

# 3. Sign the app. The entitlements (application-identifier, team identifier,
#    and get-task-allow for development profiles) must come from that profile.
codesign -f -s "Apple Development: you@example.com" \
  --entitlements entitlements.plist ipa/Payload/MavlinkJoystickIOS.app

# 4. Repack, keeping the Payload/ layout.
cd ipa && zip -qr ../mavlink-joystick-signed.ipa Payload
```

The bundle identifier has to match the `application-identifier` entitlement in
the profile, so a profile issued for a different app id cannot be used as it is.
Installing the result still needs Xcode's **Devices and Simulators** window or
the `ideviceinstaller` tool.

## After installing

Both apps listen on UDP port 14550 and connect to the first vehicle that sends a
MAVLink heartbeat, so the phone and the vehicle must be on the same Wi-Fi
network. The listen port and the connection settings are described in the
[README](./README.md), which also lists the safety notes worth reading before
the first flight.

If the vehicle is not discovered:

- check that the phone joined the vehicle's Wi-Fi, not a mobile network;
- on iOS, verify that local network access is allowed for the app in
  **Settings → Privacy & Security → Local Network**;
- on Android, disable any "Wi-Fi assistant" or "auto-switch to mobile data"
  option that moves traffic off the vehicle's network;
- check the listen port in **Settings → CONNECTION → Listen Port** against the
  one the vehicle sends to.
