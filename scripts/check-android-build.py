#!/usr/bin/env python3
"""Inspect the built APK, including its actual permissions and bundled dataset."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--apk", required=True, type=Path)
parser.add_argument("--build-tools", required=True, type=Path)
parser.add_argument("--release", action="store_true")
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent

def run(tool, *arguments):
    return subprocess.check_output([str(args.build_tools / tool), *map(str, arguments)], text=True)

signature = run("apksigner", "verify", "--verbose", "--print-certs", args.apk)
run("zipalign", "-c", "-P", "16", "4", args.apk)
badging = run("aapt2", "dump", "badging", args.apk)
manifest = run("aapt2", "dump", "xmltree", args.apk, "--file", "AndroidManifest.xml")
expected_version = json.loads((root / "package.json").read_text())["version"]
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
assert package, "Package identity is missing."
expected_package = "com.caleb.spotifydash" + ("" if args.release else ".debug")
assert package[1] == expected_package, "Unexpected package identity."
assert package[3] == expected_version + ("" if args.release else "-debug"), "Web and Android release versions differ."
assert "launchable-activity: name='com.caleb.spotifydash.MainActivity'" in badging, "The dashboard launcher is missing."
for permission in ("INTERNET", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE"):
    assert f"android.permission.{permission}" not in badging, f"Unexpected permission: {permission}"
assert re.search(r"android:allowBackup[^\n]*=(?:false|0x0)(?:\s|$)", manifest), "Local history backup must be disabled."
if args.release:
    assert not re.search(r"android:debuggable[^\n]*=(?:true|0xffffffff)", manifest), "Release APK is debuggable."
with zipfile.ZipFile(args.apk) as archive:
    names = archive.namelist()
    assert not any(".local-data/" in n or ".private/" in n or "Streaming_History" in n for n in names), "Unexpected private data path in APK."
    demo_bytes = archive.read("assets/demo.json.gzip")
    assert demo_bytes == (root / "android/app/src/main/assets/demo.json.gzip").read_bytes(), "Bundled demo differs from its reviewed source asset."
    demo = json.loads(gzip.decompress(demo_bytes))
    assert demo["meta"]["source"] == "demo" and demo["meta"]["files"] == ["fictional-demo.json"]
    assert len(demo["events"]) == 11583
    assert not any(set(event) & {"ip_addr", "username", "user_agent"} for event in demo["events"])
certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([a-f0-9]+)", signature)
print(json.dumps({"status": "passed", "package": package[1], "versionCode": int(package[2]), "versionName": package[3],
    "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "certificateSha256": certificate[1] if certificate else None,
    "demoEvents": len(demo["events"]), "networkPermission": False, "broadStoragePermission": False}, indent=2))
