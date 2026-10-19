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

## Build and Run iOS Application

- Get something with MacOS
- To build and run the development version of the iOS app, open [iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode and run it from there
- After that you will be able to run iOS app from Android Studio

## Local tests

Local tests are running during build phase and also can be run separately:

```shell
./gradlew :shared:localTests
```

The HTML report is saved to
`shared/build/reports/tests/testAndroidHostTest/index.html`.