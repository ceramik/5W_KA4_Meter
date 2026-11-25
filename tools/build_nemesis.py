#!/usr/bin/env python3
"""Create the Gen5W NEMESIS USB installer from an existing signed APK."""
import argparse
import io
import json
import struct
import zlib
import re
import zipfile
from pathlib import Path

PACKAGE = "hkg.taximeter"
APP = "me.bamtol.ka4taximeter"

def tile(bg, accent):
    # 99px RGBA launcher tile; rounded background and seven-segment meter.
    w=99
    rows=bytearray()
    segments=[(0,0,13,3),(13,3,3,10),(13,16,3,10),(0,26,13,3),(-3,16,3,10),(-3,3,3,10),(0,13,13,3)]
    masks=[102,127,63,63]
    for y in range(w):
        rows.append(0)
        for x in range(w):
            dx=max(abs(x-49)-30,0);dy=max(abs(y-49)-30,0)
            if dx*dx+dy*dy>14*14: color=(0,0,0,0)
            elif 9<=x<=89 and 25<=y<=73: color=(15,15,17,255)
            else: color=(*bg,255)
            for i,mask in enumerate(masks):
                for j,(sx,sy,sw,sh) in enumerate(segments):
                    xx=15+i*19+sx; yy=35+sy
                    if mask&(1<<j) and xx<=x<xx+sw and yy<=y<yy+sh: color=(*accent,255)
            rows.extend(color)
    def chunk(tag,data):
        return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",w,w,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(rows))+chunk(b"IEND",b"")

def build(apk, version, output):
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}", version):
        raise ValueError("Invalid package version")
    apk = Path(apk)
    if not zipfile.is_zipfile(apk):
        raise ValueError("Not an APK archive")
    with zipfile.ZipFile(apk) as z:
        if not {"AndroidManifest.xml", "classes.dex"} <= set(z.namelist()) or z.testzip():
            raise ValueError("Invalid APK contents")
    meta = f"[nemesis]\nversion={version}\nid={PACKAGE}\nname=5W TaxiMeter\ntypes=application\npostinstall=scripts/post.sh\npreuninstall=scripts/preun.sh\n\n[app.0]\nkind=sh\npath=bin/launch.sh\nuid=root\n\n[app.1]\nkind=sh\npath=bin/diagnose.sh\nuid=root\n"
    common = """#!/system/bin/sh
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export ANDROID_ROOT=/system
export ANDROID_DATA=/data
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
"""
    common += """export BOOTCLASSPATH=${BOOTCLASSPATH:-/system/framework/core.jar:/system/framework/conscrypt.jar:/system/framework/okhttp.jar:/system/framework/core-junit.jar:/system/framework/bouncycastle.jar:/system/framework/ext.jar:/system/framework/framework.jar:/system/framework/framework2.jar:/system/framework/telephony-common.jar:/system/framework/voip-common.jar:/system/framework/mms-common.jar:/system/framework/android.policy.jar:/system/framework/services.jar:/system/framework/apache-xml.jar:/system/framework/webviewchromium.jar:/system/framework/automotive.jar}
PKG=hkg.taximeter
APP=me.bamtol.ka4taximeter
LOG=/data/local/tmp/taximeter-install.log
# Always keep an internal log; USB is an additional best-effort copy.
report() {
    echo "$*"
    echo "$*" >> "$LOG" 2>/dev/null
}
export_log() {
    for U in /storage/usb* /mnt/media_rw/usb* /mnt/usb* /media/usb* /run/media/* /storage/USB* /mnt/USB*; do
        [ -d "$U" ] || continue
        [ -w "$U" ] || continue
        # Permit mounted filesystems and aliases only, not empty mountpoint directories.
        grep " $U " /proc/mounts >/dev/null 2>&1 || {
            REAL="$(readlink -f "$U" 2>/dev/null)"
            [ -n "$REAL" ] && grep " $REAL " /proc/mounts >/dev/null 2>&1 || continue
        }
        if cp "$LOG" "$U/taximeter-install.log" 2>/dev/null; then
            sync
            echo "USB log: $U/taximeter-install.log"
        fi
    done
}
begin_log() {
    umask 022
    echo "TaxiMeter installer diagnostic 0.5.11: $1" > "$LOG" 2>/dev/null
    report "TaxiMeter installer diagnostic 0.5.11: $1"
    report "UID: $(id 2>&1)"
    report "Android SDK: $(getprop ro.build.version.sdk)"
    report "Script: $0"
    report "pm: $(ls -l /system/bin/pm 2>&1)"
    report "pm path: $(/system/bin/pm path "$APP" 2>&1)"
    report "Mounts: $(cat /proc/mounts 2>/dev/null)"
    trap 'export_log' EXIT
}
find_apk() {
    APK=
    for D in /data/local/tmp/nemesis/stage/$PKG /data/nemesis/apps/$PKG /data/nemesis/packages/$PKG; do
        if [ -s "$D/bin/TaxiMeter.apk" ]; then APK="$D/bin/TaxiMeter.apk"; report "APK: $APK"; return 0; fi
    done
    report "TaxiMeter APK not found"; return 1
}
installed() {
    case "$(/system/bin/pm path "$APP" 2>/dev/null)" in package:*) return 0;; *) return 1;; esac
}
install_apk() {
    find_apk || return 1
    TMP=/data/local/tmp/taximeter-install.apk
    rm -f "$TMP"
    cp "$APK" "$TMP" || return 1
    chmod 644 "$TMP" || { rm -f "$TMP"; return 1; }
    report "Source bytes: $(wc -c < "$APK") / copied bytes: $(wc -c < "$TMP")"
    if command -v sha256sum >/dev/null 2>&1; then report "$(sha256sum "$APK" "$TMP")"; elif command -v busybox >/dev/null 2>&1; then report "$(busybox sha256sum "$APK" "$TMP" 2>&1)"; fi
    RESULT="$(/system/bin/pm install -r "$TMP" 2>&1)"
    STATUS=$?
    rm -f "$TMP"
    report "pm install exit=$STATUS: $RESULT"
    report "PackageParser diagnostics:"
    /system/bin/logcat -d -t 250 -v brief 2>/dev/null | grep -iE "PackageParser|PackageManager|certificate|taximeter" >> "$LOG"
    /system/bin/logcat -d -t 250 -v brief 2>/dev/null | grep -iE "PackageParser|certificate" | tail -20
    [ "$STATUS" = 0 ] || return 1
    case "$RESULT" in *Success*) ;; *) return 1;; esac
    installed || { echo "APK not registered after install"; return 1; }
}
"""
    post = common + """umask 022
begin_log postinstall
if ! install_apk; then
    echo "APK installation failed; see $LOG"
    exit 1
fi
/system/bin/pm path "$APP"
# Register only a launcher contribution; Android APK remains owned by PackageManager.
D="${APK%/bin/TaxiMeter.apk}"
if [ -f /system/framework/hmgapi-hook.jar ] && [ -d /data/hmgapi ]; then
    KEY=/data/hmgapi/local-keys/hkg-taximeter.key
    mkdir -p /data/hmgapi/local-keys
    chmod 700 /data/hmgapi/local-keys
    hmg() { CLASSPATH=/system/framework/hmgapi-hook.jar /system/bin/app_process /system/bin "$@"; }
    if [ -x /system/bin/hmgapid ]; then
        /system/bin/hmgapid ping >/dev/null 2>&1 || /system/bin/hmgapid daemon </dev/null >/dev/null 2>&1 &
    fi
    if [ ! -s "$KEY" ]; then
        (umask 077; hmg hmgapi.engine.Signing keygen "$KEY") || { echo "HMG-API key generation failed; NEMESIS launcher remains available"; exit 0; }
    fi
    STAGE=/data/local/tmp/hkg-taximeter-mod-$$
    umask 077
    mkdir -p "$STAGE"
    cp "$D/hmgapi_mod/"* "$STAGE/"
    if hmg hmgapi.engine.Signing sign "$STAGE" "$KEY" && hmg hmgapi.engine.Engine install "$STAGE" --from-nemesis --force; then
        if [ -x /system/bin/hmgapid ]; then
            /system/bin/hmgapid engine apply || echo "HMG-API apply failed"
        else
            hmg hmgapi.engine.Engine apps || :
            hmg hmgapi.engine.Engine publish-state || :
        fi
        echo "HMG-API launcher registration attempted successfully"
    else
        echo "HMG-API launcher registration failed; use NEMESIS app menu"
    fi
    rm -rf "$STAGE"
else
    echo "HMG-API not installed; NEMESIS app menu remains available"
fi
exit 0
"""
    launch = common + """begin_log launch
if ! installed; then
    echo "APK missing; attempting recovery"
    install_apk || { echo "Installation failed; see $LOG"; exit 1; }
fi
RESULT="$(/system/bin/am start -n "$APP/com.veloster.vehicle.MainActivity" 2>&1)"
report "am start: $RESULT"
case "$RESULT" in *"Error type 3"*|*"does not exist"*)
    echo "Activity missing; retrying package installation"
    install_apk || exit 1
    /system/bin/am start -n "$APP/com.veloster.vehicle.MainActivity"
    ;; esac
"""
    diagnose = common + """begin_log manual
report "Package directories:"
for D in /data/local/tmp/nemesis/stage/$PKG /data/nemesis/apps/$PKG /data/nemesis/packages/$PKG; do
    report "$(ls -ld "$D" "$D/bin" "$D/bin/TaxiMeter.apk" 2>&1)"
done
report "HMG-API: $(ls -l /system/framework/hmgapi-hook.jar 2>&1)"
report "Package manager: $(/system/bin/pm list packages "$APP" 2>&1)"
report "Attempting install without deleting data"
if install_apk; then
    report "APK install verified"
    report "Activity: $(/system/bin/am start -n "$APP/com.veloster.vehicle.MainActivity" 2>&1)"
else
    report "APK installation failed"
fi
report "Internal log: $LOG"
"""
    preun = common + """# Keep the Android APK and all data: this hook can also run before upgrades.
# Explicit Android uninstall is a separate user action.
# Skip launcher removal during a staged NEMESIS upgrade.
STAGE=/data/local/tmp/nemesis/stage/hkg.taximeter/NEMESIS.data
CALLER="$(cat /proc/$PPID/cmdline 2>/dev/null)"
case "$CALLER" in *uninstall*) ;; *) if grep -q "^id=hkg.taximeter$" "$STAGE" 2>/dev/null; then exit 0; fi ;; esac
if [ -f /system/framework/hmgapi-hook.jar ] && [ -d /data/hmgapi/mods/hkg-taximeter ]; then
    CLASSPATH=/system/framework/hmgapi-hook.jar /system/bin/app_process /system/bin hmgapi.engine.Engine remove hkg-taximeter --from-nemesis || :
    if [ -x /system/bin/hmgapid ]; then /system/bin/hmgapid engine apply || :; fi
fi
exit 0
"""
    def put(z, name, content):
        info = zipfile.ZipInfo(name)
        info.create_system = 3
        info.external_attr = ((0o100755 if name.endswith(".sh") else 0o100644) << 16)
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, content)
    activity={"package":APP,"class":"com.veloster.vehicle.MainActivity"}
    mod={"id":"hkg-taximeter","version":version,"name":"5W 택시 미터기","hmgapi":">=1.0.0","repo":"https://github.com/ceramik/5W_KA4_Meter","description":"Launch installed TaxiMeter from Gen5W menu","provides":{"launcher":[{"id":"taximeter","title":"택시 미터기",**activity,"icon":"icon.png","icon_night":"icon_night.png","icon_disabled":"icon_disabled.png","icon_disabled_night":"icon_disabled_night.png"}],"star_key":[{"id":"open","title":"택시 미터기","activity":activity}]}}
    icons={"icon.png":tile((57,42,29),(255,101,68)),"icon_night.png":tile((25,22,22),(255,101,68)),"icon_disabled.png":tile((72,72,72),(162,162,162)),"icon_disabled_night.png":tile((30,30,30),(112,112,112))}
    inner = io.BytesIO()
    with zipfile.ZipFile(inner,"w") as z:
        for name, content in {"NEMESIS.data":meta,"scripts/post.sh":post,"scripts/preun.sh":preun,"bin/launch.sh":launch,"bin/diagnose.sh":diagnose,"bin/TaxiMeter.apk":apk.read_bytes()}.items():
            put(z,name,content)
        put(z,"hmgapi_mod/mod.json",json.dumps(mod,ensure_ascii=False,indent=2))
        for name,data in icons.items(): put(z,"hmgapi_mod/"+name,data)
    with zipfile.ZipFile(output,"w") as z:
        put(z,"NEMESIS.data",f"[nemesis]\nversion=1.0\ntype=install\npath=packages/{PACKAGE}.zip\n")
        put(z,f"packages/{PACKAGE}.zip",inner.getvalue())
    with zipfile.ZipFile(output) as z:
        assert z.testzip() is None
        with zipfile.ZipFile(io.BytesIO(z.read(f"packages/{PACKAGE}.zip"))) as i:
            assert i.testzip() is None
            assert i.read("bin/TaxiMeter.apk")==apk.read_bytes()
    print(output)

if __name__ == "__main__":
    p=argparse.ArgumentParser()
    p.add_argument("apk")
    p.add_argument("version")
    p.add_argument("output")
    args=p.parse_args()
    build(args.apk,args.version,args.output)
