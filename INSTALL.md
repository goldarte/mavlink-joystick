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

Requires iOS 18.2 or newer on a 64-bit device. The released IPA is unsigned: it carries no code signature and no provisioning
profile, and iOS only starts apps that are signed for the device. It can be signed
and installed in one step by [iloader](https://iloader.app), a small desktop
application that works with an ordinary Apple ID - free or paid, no Mac and no
Xcode involved. iloader runs on Windows, macOS and Ubuntu; all three are covered
below.

### Install iloader

Take the build for your system from the
[iloader releases](https://github.com/nab138/iloader/releases).

**Windows.** Install `iloader-windows-x64.msi`. The phone is only detected when
the Apple mobile device driver is present, so install **Apple Devices** from the
Microsoft Store (or iTunes) as well. If SmartScreen warns about an unknown
publisher, choose **More info → Run anyway**.

**macOS.** Open `iloader-darwin-universal.dmg` and copy *iloader* to
**Applications**, or run `brew install --cask iloader` (a community-maintained
cask). The build is universal, for Intel and Apple silicon, signed with a
Developer ID certificate and notarized, so it opens without extra steps, and
nothing else has to be installed.

**Ubuntu** (22.04 or newer). Install `usbmuxd`, then the `.deb`:

```shell
sudo apt install usbmuxd
sudo apt install ./iloader-linux-amd64.deb
```

An AppImage (`chmod +x iloader-linux-amd64.AppImage`, then run it) and RPM
builds are published too, for both `amd64` and `arm64`.

### Sign and install the IPA

1. Connect the iPhone by cable, unlock it, and accept **Trust This Computer**. 
2. Start iloader and sign in with an Apple ID. A free account is enough.
3. Select your iPhone in iDevice section.
4. Choose **Import IPA** and select `mavlink-joystick-v2.0-unsigned.ipa`.
   iloader registers the device with the account, creates a development
   certificate and a provisioning profile, re-signs the app and installs it.
5. Launch **Mavlink Joystick** on the phone, then trust the certificate and turn on Developer Mode as described below.

A free Apple ID signs the app for 7 days and allows three installed apps at a
time; a paid Apple Developer Program account signs it for a year. When a free
signature expires, connect the phone again and repeat step 3 with the same IPA
file: the app is replaced in place and its settings survive.

### Trust the developer certificate

iOS refuses to start an app from a development certificate until it is
trusted on the device:

1. Open **Settings → General → VPN & Device Management**.
2. Tap the developer app under **Developer App** and confirm **Trust**.

The entry only exists after the first installation attempt. Launch the app again
once it is trusted.

### Turn on Developer Mode

If launching the app shows **Developer Mode Required**, iOS has to be switched into Developer Mode once before it runs development-signed apps:

1. Open **Settings → Privacy & Security** and turn on **Developer Mode** under **Security**.
2. Tap **Restart** in the alert.
3. After the restart, tap **Enable** in the confirmation dialog and enter the device passcode.

See Apple's [Enabling Developer Mode on a device](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device#Turn-on-Developer-Mode-in-iOS-iPadOS-watchOS-and-visionOS) for details.

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
