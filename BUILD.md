# Build Mavlink Joystick App

This is [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) application, so you can build the app for iOS and Android.

## Build and Run Android Application

- Open this repo in Android Studio (Panda 4 is recommended)
- To build and run the development version of the Android app, use the run configuration from Android Studio toolbar (androidApp)
- To build a release APK, use the `androidRelease` run configuration or run:

```shell
./gradlew :androidApp:assembleRelease
```

The release APK is signed with the local key described below and is written to
`androidApp/build/outputs/apk/release/androidApp-release.apk`.

### Release signing

The signing key and its credentials are gitignored and live outside version
control:

- `androidApp/release.keystore` — the signing key
- `keystore.properties` — the key alias and passwords:

```properties
storeFile=androidApp/release.keystore
storePassword=<password>
keyAlias=mavlinkjoystick
keyPassword=<password>
```

The same values can be provided through the `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`
environment variables (for CI). If neither is available, release builds are
produced unsigned.

Keep a backup of the keystore and its credentials: they are required to install
future updates over the existing app.

### Signing in GitHub Actions

[Android CI](.github/workflows/android.yml) reads the same values from repository
secrets, so CI builds produce the signed `androidApp-release.apk`:

| Secret                    | Value                                         |
| ------------------------- | --------------------------------------------- |
| `RELEASE_KEYSTORE_BASE64` | the keystore file, base64-encoded (see below) |
| `RELEASE_STORE_PASSWORD`  | `storePassword` from `keystore.properties`    |
| `RELEASE_KEY_ALIAS`       | `keyAlias` from `keystore.properties`         |
| `RELEASE_KEY_PASSWORD`    | `keyPassword` from `keystore.properties`      |

Generate the first value on the machine that holds the key:

```shell
base64 -w0 androidApp/release.keystore   # Linux
base64 -i androidApp/release.keystore    # macOS
```

The workflow decodes the keystore into the runner's temporary directory and
passes it to Gradle as `RELEASE_STORE_FILE`, so the keystore is never committed
and never leaves the runner. If the secrets are absent — a pull request from a
fork does not receive them — the release APK is still built, just unsigned, and
the run logs a warning.

## Build and Run iOS Application

- Get something with MacOS
- To build and run the development version of the iOS app, open [iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode and run it from there
- After that you will be able to run iOS app from Android Studio

### iOS artifacts in GitHub Actions

[iOS CI](.github/workflows/ios.yml) builds both Apple targets and uploads:

- `ios-simulator-app` — the Debug app built for the iOS Simulator (`iosSimulatorArm64`)
- `ios-ipa` — an unsigned Release build for devices (`iosArm64`), packaged as an `.ipa`

The `.ipa` is unsigned because CI has no Apple signing credentials. It is a zip
with the app under `Payload/`, so it must be re-signed with a development
certificate — for example by a sideloading tool — before iOS will install it.

## Local tests

Local tests are running during build phase and also can be run separately:

```shell
./gradlew :shared:localTests
```

The HTML report is saved to
`shared/build/reports/tests/testAndroidHostTest/index.html`.