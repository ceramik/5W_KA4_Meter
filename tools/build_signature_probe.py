import sys, zipfile, io
from pathlib import Path
d=Path(sys.argv[1])
script=r'''#!/system/bin/sh
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
LOG=/data/local/tmp/gen5w-signature-probe.log
echo "Gen5W signature probe 1.0" > "$LOG"
report(){ echo "$*"; echo "$*" >> "$LOG"; }
report "SDK: $(getprop ro.build.version.sdk)"
report "UID: $(id)"
for KIND in samekey testkey; do
    PKG=me.bamtol.gen5wprobe.$KIND
    APK=/data/nemesis/apps/hkg.taximeter.probe/bin/$KIND.apk
    TMP=/data/local/tmp/gen5w-probe-$KIND.apk
    report "=== $KIND ==="
    report "Before: $(/system/bin/pm path "$PKG" 2>&1)"
    if /system/bin/cat "$APK" > "$TMP"; then
        chmod 644 "$TMP"
        report "$(ls -l "$APK" "$TMP" 2>&1)"
        RESULT="$(/system/bin/pm install -r "$TMP" 2>&1)"
        report "pm exit=$?: $RESULT"
        report "After: $(/system/bin/pm path "$PKG" 2>&1)"
        rm -f "$TMP"
    else
        report "Probe copy failed"
    fi
done
report "PackageParser diagnostics:"
/system/bin/logcat -d -v brief -s PackageParser:V PackageManager:V dalvikvm:W AndroidRuntime:E >> "$LOG" 2>&1
for U in /mnt/media_rw/usb0 /storage/usb0; do
    if grep " $U " /proc/mounts >/dev/null 2>&1; then
        /system/bin/cat "$LOG" > "$U/gen5w-signature-probe.log" 2>/dev/null
    fi
done
sync
report "Done. Internal log: $LOG"
'''
def put(z,n,b):
    i=zipfile.ZipInfo(n);i.create_system=3;i.external_attr=(0o100755 if n.endswith(".sh") else 0o100644)<<16;i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,b)
buf=io.BytesIO()
with zipfile.ZipFile(buf,"w") as z:
    put(z,"NEMESIS.data","[nemesis]\nversion=1.0.0\nid=hkg.taximeter.probe\nname=Gen5W Signature Probe\ntypes=application\n\n[app.0]\nkind=sh\npath=bin/probe.sh\nuid=root\n")
    put(z,"bin/probe.sh",script)
    for k in ("samekey","testkey"):put(z,"bin/"+k+".apk",(d/(k+".apk")).read_bytes())
with zipfile.ZipFile(d/"Gen5W_Signature_Probe_USB.zip","w") as z:
    put(z,"NEMESIS.data","[nemesis]\nversion=1.0\ntype=install\npath=packages/hkg.taximeter.probe.zip\n")
    put(z,"packages/hkg.taximeter.probe.zip",buf.getvalue())
