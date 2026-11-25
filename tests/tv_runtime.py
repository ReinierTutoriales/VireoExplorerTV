"""Exercise storage, focus, search and SMB on an isolated Android TV emulator."""

import argparse
import os
import re
import statistics
import sys
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.reiniertutoriales.vireoexplorertv"
FIXTURE_DIR = "000-Vireo-CI"
FIXTURE_FILE = "vireo-fixture.txt"
DB_XML = "/data/local/tmp/vireo-window.xml"


class Tv:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args):
        return subprocess.check_output(["adb", "-s", self.serial, *args], text=True, timeout=60)

    def key(self, name, long=False):
        flags = ("--longpress",) if long else ()
        self.adb("shell", "input", "keyevent", *flags, f"KEYCODE_{name}")

    def launch(self):
        self.adb("shell", "am", "force-stop", PACKAGE)
        result = self.adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        if "Status: ok" not in result:
            raise AssertionError(result)
        match = re.search(r"^TotalTime:\s*(\d+)", result, re.MULTILINE)
        return int(match[1]) if match else None

    def tree(self):
        self.adb("shell", "uiautomator", "dump", DB_XML)
        return ET.fromstring(self.adb("shell", "cat", DB_XML))

    def find(self, predicate, label, focused=False):
        for _ in range(5):
            tree = self.tree()
            scopes = [node for node in tree.iter("node") if node.get("focused") == "true"] if focused else [tree]
            for scope in scopes:
                for node in scope.iter("node"):
                    if predicate(node):
                        return node
            time.sleep(0.25)
        labels = [node.get("text") for node in tree.iter("node") if node.get("text")]
        raise AssertionError(f"Missing {label!r}, focused={focused}. Screen: {labels}")

    def text(self, label, focused=False):
        return self.find(lambda node: node.get("text") == label, label, focused)

    def tap(self, node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        self.adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    def dismiss_keyboard(self):
        if "mInputShown=true" in self.adb("shell", "dumpsys", "input_method"):
            self.key("BACK")

    def edit(self, index, value):
        fields = [node for node in self.tree().iter("node") if node.get("class") == "android.widget.EditText"]
        self.tap(fields[index])
        self.adb("shell", "input", "text", value)
        self.dismiss_keyboard()


def storage_and_timing(tv, output):
    tv.adb("shell", "appops", "set", "--uid", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "default")
    tv.launch()
    tv.text("Internal", focused=True)
    tv.key("DPAD_RIGHT")
    tv.key("DPAD_DOWN")
    tv.text("Folders", focused=True)
    tv.key("DPAD_CENTER")
    tv.tap(tv.text("Add folder"))
    time.sleep(1)
    notices = ("Folder picker closed.", "Folder picker unavailable")
    if any(node.get("package") not in (None, PACKAGE, "com.android.systemui") for node in tv.tree().iter("node")):
        tv.key("BACK")
    tv.find(lambda node: (node.get("text") or "").startswith(notices), "Folder picker status")
    tv.text("Add folder")
    print("PASS SAF dismissal returns to Files", flush=True)

    tv.adb("shell", "appops", "set", "--uid", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
    tv.adb("shell", f"mkdir -p /sdcard/{FIXTURE_DIR}; touch /sdcard/{FIXTURE_DIR}/{FIXTURE_FILE}")
    starts = [tv.launch() for _ in range(5)]
    measured = [value for value in starts if value is not None]
    if measured:
        print(f"Release cold startup TotalTime ms: {measured}, median {statistics.median(measured)}", flush=True)

    tv.adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tv.text("Internal", focused=True)
    tv.key("DPAD_CENTER")
    tv.text(FIXTURE_DIR, focused=True)
    tv.key("DPAD_CENTER")
    tv.text(FIXTURE_FILE, focused=True)
    tv.key("DPAD_CENTER", long=True)
    tv.text("Rename")
    tv.text("Delete")
    tv.key("BACK")
    tv.text(FIXTURE_FILE, focused=True)
    tv.key("BACK")
    tv.text(FIXTURE_DIR, focused=True)
    output.mkdir(parents=True, exist_ok=True)
    (output / "release-framestats.txt").write_text(tv.adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats"))
    print("PASS D-pad long press, action dialog and focus restoration", flush=True)

    tv.adb("shell", f"for i in $(seq 1 720); do touch /sdcard/{FIXTURE_DIR}/bulk-$i.txt; done")
    tv.launch()
    started = time.monotonic()
    tv.tap(tv.text("Documents"))
    tv.text("Next results")
    first_page_elapsed = time.monotonic() - started
    tv.tap(tv.text("Next results"))
    tv.find(lambda node: (node.get("text") or "").startswith("Page 2"), "second search page")
    (output / "release-timing.txt").write_text(
        f"Cold startup TotalTime samples (ms): {measured}\n"
        f"Search first-page observation including UI automation (seconds): {first_page_elapsed:.2f}\n"
    )
    print("PASS Search continues to a second page", flush=True)


def smb(tv, fixture, variant, user, password):
    tv.adb("install", "-r", fixture)
    tv.launch()
    tv.tap(tv.text("Local network"))
    tv.tap(tv.text("Connect"))
    tv.text("Server host or IP")
    for index, value in [(0, "10.0.2.2"), (1, "vireoci"), (2, user), (4, password)]:
        tv.edit(index, value)
    tv.tap(tv.text("Connect"))
    tv.text("vireo-smb-fixture.txt")
    print(f"PASS {variant} authenticated SMB directory listing", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--release", required=True)
    parser.add_argument("--debug", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--smb-user", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("A disposable Android TV emulator is required.")
    tv = Tv(args.serial)
    password = os.environ["VIREO_TEST_SMB_PASSWORD"]
    args.output.mkdir(parents=True, exist_ok=True)
    tv.adb("install", "-r", args.release)
    failures = []
    checks = [
        ("storage", lambda: storage_and_timing(tv, args.output)),
        ("release SMB", lambda: smb(tv, args.release, "release", args.smb_user, password)),
        ("debug SMB", lambda: smb(tv, args.debug, "debug", args.smb_user, password)),
    ]
    for label, check in checks:
        try:
            check()
        except Exception:
            failures.append(label)
            trace = traceback.format_exc()
            (args.output / f"{label.replace(' ', '-')}-failure.txt").write_text(trace)
            print(f"FAIL {label}\n{trace}", file=sys.stderr, flush=True)
    if failures:
        raise SystemExit(f"Failed checks: {', '.join(failures)}")
