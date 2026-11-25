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

    @staticmethod
    def center(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        return (x1 + x2) // 2, (y1 + y2) // 2

    def select(self, label):
        attempts = set()
        for _ in range(10):
            tree = self.tree()
            target = next((node for node in tree.iter("node") if node.get("text") == label), None)
            focused = next((node for node in tree.iter("node") if node.get("focused") == "true"), None)
            if target is None or focused is None:
                break
            if target in list(focused.iter("node")):
                self.key("DPAD_CENTER")
                return
            x, y = self.center(focused)
            tx, ty = self.center(target)
            directions = [("RIGHT" if tx > x else "LEFT", abs(tx - x)),
                          ("DOWN" if ty > y else "UP", abs(ty - y))]
            for direction, distance in sorted(directions, key=lambda item: -item[1]):
                attempt = (x, y, direction)
                if distance > 8 and attempt not in attempts:
                    attempts.add(attempt)
                    self.key(f"DPAD_{direction}")
                    break
            else:
                break
        self.tap(self.text(label))

    def tap(self, node):
        x, y = self.center(node)
        self.adb("shell", "input", "tap", str(x), str(y))

    def dismiss_keyboard(self):
        if "mInputShown=true" in self.adb("shell", "dumpsys", "input_method"):
            self.key("BACK")

    def edit(self, index, value):
        for _ in range(10):
            fields = [node for node in self.tree().iter("node") if node.get("class") == "android.widget.EditText"]
            if len(fields) > index and fields[index].get("focused") == "true":
                self.adb("shell", "input", "text", value)
                self.dismiss_keyboard()
                return
            self.key("TAB")
        raise AssertionError(f"Cannot focus SMB input field {index}.")


def picker_cancellation(tv):
    tv.adb("shell", "appops", "set", "--uid", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "default")
    tv.launch()
    tv.text("Internal", focused=True)
    tv.key("DPAD_RIGHT")
    tv.key("DPAD_DOWN")
    tv.text("Folders", focused=True)
    tv.key("DPAD_CENTER")
    tv.select("Add folder")
    time.sleep(1)
    notices = ("Folder picker closed.", "Folder picker unavailable")
    resumed = tv.adb("shell", "dumpsys", "activity", "activities")
    if any(PACKAGE not in line for line in resumed.splitlines() if "topResumedActivity=" in line):
        tv.key("BACK")
    tv.find(lambda node: (node.get("text") or "").startswith(notices), "Folder picker status")
    tv.text("Add folder")
    print("PASS SAF dismissal returns to Files", flush=True)


def storage_and_timing(tv, output):
    tv.adb("shell", "appops", "set", "--uid", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
    tv.adb("shell", f"mkdir -p /sdcard/{FIXTURE_DIR}; touch /sdcard/{FIXTURE_DIR}/{FIXTURE_FILE}")
    starts = [tv.launch() for _ in range(5)]
    measured = [value for value in starts if value is not None]
    if measured:
        print(f"Release cold startup TotalTime ms: {measured}, median {statistics.median(measured)}", flush=True)
    (output / "release-startup.txt").write_text(f"Cold startup TotalTime samples (ms): {measured}\n")

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


def search_pagination(tv, output):
    tv.adb("shell", "appops", "set", "--uid", PACKAGE, "MANAGE_EXTERNAL_STORAGE", "allow")
    tv.adb("shell", "mkdir", "-p", f"/sdcard/{FIXTURE_DIR}")
    tv.adb("shell", f"for i in $(seq 1 720); do touch /sdcard/{FIXTURE_DIR}/bulk-$i.txt; done")
    tv.launch()
    started = time.monotonic()
    tv.select("Documents")
    tv.text("Next results")
    first_page_elapsed = time.monotonic() - started
    tv.select("Next results")
    tv.find(lambda node: (node.get("text") or "").startswith("Page 2"), "second search page")
    (output / "release-search.txt").write_text(
        f"Search first-page observation including UI automation (seconds): {first_page_elapsed:.2f}\n"
    )
    print("PASS Search continues to a second page", flush=True)


def smb(tv, fixture, variant, user, password):
    tv.adb("install", "-r", fixture)
    tv.launch()
    tv.select("Local network")
    tv.select("Manual SMB connection")
    tv.text("Server host or IP")
    for index, value in [(0, "10.0.2.2"), (1, "vireoci"), (2, user), (4, password)]:
        tv.edit(index, value)
    tv.key("DPAD_DOWN")
    tv.text("Connect", focused=True)
    tv.key("DPAD_CENTER")
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
        ("picker", lambda: picker_cancellation(tv)),
        ("storage", lambda: storage_and_timing(tv, args.output)),
        ("search", lambda: search_pagination(tv, args.output)),
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
            try:
                (args.output / f"{label.replace(' ', '-')}-window.xml").write_text(ET.tostring(tv.tree(), encoding="unicode"))
            except (OSError, subprocess.SubprocessError, ET.ParseError):
                pass
    if failures:
        raise SystemExit(f"Failed checks: {', '.join(failures)}")
