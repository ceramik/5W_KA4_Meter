#!/usr/bin/env python3
"""Create the Gen5W NEMESIS USB installer from an existing signed APK."""
import argparse
import io
import re
import zipfile
from pathlib import Path

PACKAGE = "hkg.taximeter"
APP = "me.bamtol.ka4taximeter"

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
"""
    launch = common + """exec /system/bin/am start -n me.bamtol.ka4taximeter/com.veloster.vehicle.MainActivity
"""
    preun = common + """# Keep the Android APK and all data: this hook can also run before upgrades.
# Explicit Android uninstall is a separate user action.
exit 0
"""
    def put(z, name, content):
        info = zipfile.ZipInfo(name)
        info.create_system = 3
        info.external_attr = ((0o100755 if name.endswith(".sh") else 0o100644) << 16)
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, content)
    inner = io.BytesIO()
    with zipfile.ZipFile(inner,"w") as z:
        for name, content in {"NEMESIS.data":meta,"scripts/post.sh":post,"scripts/preun.sh":preun,"bin/launch.sh":launch,"bin/TaxiMeter.apk":apk.read_bytes()}.items():
            put(z,name,content)
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
