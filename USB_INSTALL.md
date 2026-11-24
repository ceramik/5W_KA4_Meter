# USB installation package: build and install

## GitHub Actions build (no Android Studio needed)

1. Create a repository in your own GitHub account and upload **contents of this project folder** to its repository root (including `.github/workflows/build-apk.yml`).
2. Open Actions > Build KA4 NEMESIS APK > Run workflow. (The workflow also runs after push to main/master.)
3. When the workflow finishes successfully, download `KA4-NEMESIS-TaxiMeter-debug-APK` from the workflow run's Artifacts section.
4. Extract `app-debug.apk` from the downloaded ZIP.
5. Copy `app-debug.apk` onto a FAT32-formatted USB drive. Plug it into the 5W head unit.
6. Open the APK through NEMESIS's file manager / APK installer, if your NEMESIS configuration supports user APK installation. Grant the requested installation authorization.
7. Launch 'KA4 택시 미터기'. On first use, grant location and optionally notification-access permissions.

APK install may be restricted by your 5W firmware or NEMESIS app-install permissions. No promise of compatibility is made without a device test. Debug APK is not signed with a production key; it uses the build runner's debug signing key. Updating from a differently signed version may require uninstalling the previous version (which clears its app data).

## Local build

Requires JDK 17, Android SDK Platform 34, Gradle 8.2.1, and internet access to resolve Android Gradle Plugin 8.2.2.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Test injection (ADB, stationary only)

```bash
adb shell am broadcast -a me.bamtol.ka4taximeter.VEHICLE_SPEED -p me.bamtol.ka4taximeter --ef speed_kmh 35.5
```

The command injects a **simulated** vehicle speed. It does not read the actual KA4 speed bus. Real vehicle-speed bridge sender and NEMESIS launcher integration are not included. The tariff display is a test UI and must not be used for commercial fare collection.
