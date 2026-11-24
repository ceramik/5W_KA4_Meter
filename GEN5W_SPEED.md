# Gen5W common speed adapter (0.4.2)

No model-specific CAN IDs. Attempts the OEM read-only Binder getter:
- service: com.hkmc.micom.bcm
- interface: com.hkmc.bcm.IBCMService
- method: getSpeed_KMHUnit()
- reference: https://github.com/GRT47/HKG_5W_TurzxMonitor/blob/main/package_turzx_monitor/src_java/com/hkg/turzxmonitor/TurzxMonitorMain.java

Priority: valid fresh GNSS > BCM > existing external speed broadcast > unavailable (fare pauses). Poll 500 ms on worker, reconnect backoff 5 s. Vehicle readings expire after 2 s; GPS after 4 s (based on fix monotonic timestamp). GPS fixes with reported horizontal accuracy worse than 50 m are rejected and GPS is invalidated immediately. Zero is accepted only from a valid reading. Existing package ID and broadcast action retained for upgrade compatibility.

The OEM interface may be absent from a normal APK classloader or restricted by firmware/UID. This adapter is best effort, not guaranteed on every NEMESIS vehicle. No privilege escalation or CAN writes. If restricted, use existing broadcast bridge from a permitted NEMESIS-side process. Actual sender/package and firmware-specific access are not yet validated. Test stationary first: Settings > GPS / speed diagnostics, compare BCM and GPS while a passenger observes on a subsequent drive.

Existing action: me.bamtol.ka4taximeter.VEHICLE_SPEED, float extra speed_kmh. Example test only: adb shell am broadcast -a me.bamtol.ka4taximeter.VEHICLE_SPEED -p me.bamtol.ka4taximeter --ef speed_kmh 0.0
