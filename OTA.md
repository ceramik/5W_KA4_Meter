# OTA setup
Automatic checks in foreground when stopped and not metering, at most hourly. Downloads latest published release if its tag v<versionCode> is newer, checks package ID, version and installed signing certificate, and asks Android installer for confirmation. No root or silent installation. No update on unavailable speed. Automatic checks can be disabled in settings. Android 4.4 TLS/installer support must be tested.
 
## Signing
Create a private key locally (never commit it):
keytool -genkeypair -keystore meter.jks -alias meter -keyalg RSA -keysize 2048 -validity 10000
Repository Settings > Secrets and variables > Actions: METER_KEYSTORE_B64 (base64 of meter.jks), METER_STORE_PASSWORD, METER_KEY_ALIAS, METER_KEY_PASSWORD.
Keep secure backups. Existing debug-signed installs may require one-time migration; export CSV before uninstalling, since uninstall removes data. Future releases use same key. Workflow publishes only with configured signing. Increment versionCode for each release. Existing release tags are never overwritten.
