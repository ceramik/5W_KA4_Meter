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
    meta = f"[nemesis]\nversion={version}\nid={PACKAGE}\nname=5W TaxiMeter\ntypes=application\npostinstall=scripts/post.sh\npreuninstall=scripts/preun.sh\n\n[app.0]\nkind=sh\npath=bin/launch.sh\nuid=root\n"
    common = """#!/system/bin/sh
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export ANDROID_ROOT=/system
export ANDROID_DATA=/data
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
"""
    post = common + """set -e
PKG=hkg.taximeter
APK=
for D in /data/nemesis/apps/$PKG /data/nemesis/packages/$PKG /data/local/tmp/nemesis/stage/$PKG; do
    if [ -s "$D/bin/TaxiMeter.apk" ]; then APK="$D/bin/TaxiMeter.apk"; break; fi
done
[ -n "$APK" ] || { echo "TaxiMeter APK not found"; exit 1; }
# Package manager copies APK internally. Never uninstall or clear data on failure.
RESULT="$(/system/bin/pm install -r "$APK" 2>&1)" || { echo "$RESULT"; exit 1; }
echo "$RESULT"
case "$RESULT" in *Success*) ;; *) exit 1;; esac
/system/bin/pm path me.bamtol.ka4taximeter
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
    STAGE=/data/local/tmp/hkg-taximeter-mod-$
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
    launch = common + """exec /system/bin/am start -n me.bamtol.ka4taximeter/com.veloster.vehicle.MainActivity
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
        for name, content in {"NEMESIS.data":meta,"scripts/post.sh":post,"scripts/preun.sh":preun,"bin/launch.sh":launch,"bin/TaxiMeter.apk":apk.read_bytes()}.items():
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
