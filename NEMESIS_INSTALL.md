# NEMESIS USB installation
Download 5W_TaxiMeter_NEMESIS_USB.zip from GitHub Releases, extract the outer ZIP to the root of a FAT32 USB drive. Keep NEMESIS.data and packages/hkg.taximeter.zip together. Use NEMESIS to install while parked. Open the TaxiMeter application entry in NEMESIS.

The installer uses pm install -r, preserving Android app data when certificates match. It never automatically uninstalls on a signature mismatch. Package preuninstall is deliberately non-destructive because it may run during a NEMESIS upgrade; removing the NEMESIS entry does not remove the installed Android app. Explicit Android uninstall deletes app data. Export CSV first.

The APK is the same signed release APK when packaging an existing release. No re-signing. App OTA updates the Android APK, not the NEMESIS wrapper. No automatic UI launch at ignition and no changes to system partitions. Optional app boot service remains controlled by app settings. BCM access, installer behavior and launcher integration still require real Gen5W testing.

USB descriptor and inner package metadata follow the public example in https://github.com/GRT47/HKG_5W_TurzxMonitor/blob/main/package_turzx_monitor/build_package.py and NEMESIS.data.

## HMG-API launcher (0.5.4)
The USB install attempts to register a launcher tile and optional star-key action using installed HMG-API >=1.0.0. Four 99px icons are packaged. The contribution references the existing Android activity without declaring APK ownership, so removing the contribution should not remove trip data. This launcher-only schema and the actual firmware need in-car verification; if rejected or HMG-API is absent, use the NEMESIS application entry. Existing OTA alone does not install the launcher wrapper; use the updated USB package once.

## 0.5.5 installation recovery
Postinstall copies the APK to a readable file under /data/local/tmp before pm install -r, records the PackageManager result in /data/local/tmp/taximeter-install.log, and checks pm path. Launch retries installation if the APK is missing or the activity does not exist. Existing data is never automatically deleted. The exact cause of an installation failure still requires the device log.
