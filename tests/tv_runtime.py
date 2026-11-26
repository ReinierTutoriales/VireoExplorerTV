"""Exercise storage, focus, search and SMB on an isolated Android TV emulator."""

import argparse
import base64
import os
import re
import statistics
import sys
import subprocess
import time
import traceback
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.reiniertutoriales.vireoexplorertv"
FIXTURE_DIR = "000-Vireo-CI"
FIXTURE_FILE = "vireo-fixture.txt"


class Tv:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args):
        return subprocess.check_output(["adb", "-s", self.serial, *args], text=True, timeout=60)

    def setup_command(self, *args, timeout=10):
        return subprocess.check_output(
            ["adb", "-s", self.serial, *args], text=True,
            stderr=subprocess.STDOUT, timeout=timeout)

    def wait_ready(self, timeout=240):
        deadline = time.monotonic() + timeout
        consecutive = 0
        last = "not ready"
        while time.monotonic() < deadline:
            try:
                if (self.setup_command("get-state").strip() == "device"
                        and self.setup_command("shell", "getprop", "sys.boot_completed").strip() == "1"
                        and self.setup_command("shell", "pm", "path", "android").strip().startswith("package:")):
                    consecutive += 1
                    if consecutive >= 2:
                        print("ADB transport, Android boot and package manager ready", flush=True)
                        return
                else:
                    consecutive = 0
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                consecutive = 0
                last = str(error)
            time.sleep(2)
        raise TimeoutError(f"Emulator readiness deadline exceeded: {last}")

    def configure_runtime(self):
        # Apply only after Android framework services are responsive.
        self.wait_ready()
        for name in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.setup_command("shell", "settings", "put", "global", name, "0")
            if float(self.setup_command("shell", "settings", "get", "global", name).strip()) != 0:
                raise AssertionError(f"Animation setting not applied: {name}")
        print("Runtime animation settings verified", flush=True)

    def install(self, apk):
        self.wait_ready()
        result = self.setup_command("install", "--no-streaming", "-r", apk, timeout=90)
        if "Success" not in result.splitlines():
            raise AssertionError(f"APK installation failed: {result}")

    def screenshot(self, path):
        data = subprocess.check_output(["adb", "-s", self.serial, "exec-out", "screencap", "-p"], timeout=60)
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise AssertionError("Invalid screenshot PNG")
        path.write_bytes(data)

    def key(self, name, long=False):
        flags = ("--longpress",) if long else ()
        self.adb("shell", "input", "keyevent", *flags, f"KEYCODE_{name}")

    def storage_access(self, allowed):
        api = int(self.adb("shell", "getprop", "ro.build.version.sdk").strip())
        if api >= 30:
            self.adb("shell", "appops", "set", "--uid", PACKAGE,
                     "MANAGE_EXTERNAL_STORAGE", "allow" if allowed else "default")
        else:
            for permission in ("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"):
                self.adb("shell", "pm", "grant" if allowed else "revoke",
                         PACKAGE, f"android.permission.{permission}")

    def launch(self):
        self.adb("shell", "am", "force-stop", PACKAGE)
        result = self.adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
        if "Status: ok" not in result:
            raise AssertionError(result)
        match = re.search(r"^TotalTime:\s*(\d+)", result, re.MULTILINE)
        return int(match[1]) if match else None

    def tree(self):
        # Instrumentation runs in a separate package; no fixed idle wait or stale XML.
        last = ""
        for _ in range(5):
            last = self.adb("shell", "am", "instrument", "-w",
                            "com.reiniertutoriales.vireotest/.Hierarchy")
            match = re.search(r"^INSTRUMENTATION_RESULT: hierarchy=([A-Za-z0-9+/=]+)$",
                              last, re.MULTILINE)
            if match and "INSTRUMENTATION_CODE: -1" in last:
                root = ET.fromstring(base64.b64decode(match[1], validate=True))
                if next(root.iter("node"), None) is not None:
                    return root
            time.sleep(0.25)
        raise AssertionError(f"No fresh instrumentation hierarchy: {last}")

    def find(self, predicate, label, focused=False, timeout=None):
        deadline = time.monotonic() + timeout if timeout is not None else None
        for _ in range(120 if deadline is not None else 5):
            tree = self.tree()
            scopes = [node for node in tree.iter("node") if node.get("focused") == "true"] if focused else [tree]
            for scope in scopes:
                for node in scope.iter("node"):
                    if predicate(node):
                        return node
            if deadline is not None and time.monotonic() >= deadline:
                break
            time.sleep(0.25)
        labels = [node.get("text") for node in tree.iter("node") if node.get("text")]
        raise AssertionError(f"Missing {label!r}, focused={focused}. Screen: {labels}")

    def text(self, label, focused=False):
        return self.find(lambda node: node.get("text") == label, label, focused)

    @staticmethod
    def center(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        return (x1 + x2) // 2, (y1 + y2) // 2

    def select(self, label, long=False, strict=False):
        attempts = set()
        for _ in range(10):
            tree = self.tree()
            target = next((node for node in tree.iter("node") if node.get("text") == label), None)
            focused = next((node for node in tree.iter("node") if node.get("focused") == "true"), None)
            if target is None or focused is None:
                break
            if target in list(focused.iter("node")):
                self.key("DPAD_CENTER", long=long)
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
        if long or strict:
            raise AssertionError(f"Cannot reach {label!r} using D-pad (long={long})")
        print(f"Touch fallback for {label!r}; not a D-pad pass", flush=True)
        self.tap(self.text(label))

    def tap(self, node):
        x, y = self.center(node)
        self.adb("shell", "input", "tap", str(x), str(y))

    def dismiss_keyboard(self):
        if "mInputShown=true" in self.adb("shell", "dumpsys", "input_method"):
            self.key("BACK")

    def submit_smb(self):
        # edit() already dismisses the IME. A second Back can close the SMB dialog.
        self.text("Server host or IP")
        for direction in ("DPAD_DOWN", "DPAD_DOWN", "DPAD_DOWN", "TAB", "TAB", "TAB", "TAB"):
            self.key(direction)
            for focused in self.tree().iter("node"):
                if focused.get("focused") == "true" and any(node.get("text") == "Connect" for node in focused.iter("node")):
                    print(f"SMB Connect focused through {direction}", flush=True)
                    self.key("DPAD_CENTER")
                    return
        raise AssertionError("Cannot reach Connect from the SMB password field.")

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
    tv.storage_access(False)
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
    tv.storage_access(True)
    tv.adb("shell", f"mkdir -p /sdcard/{FIXTURE_DIR}; touch /sdcard/{FIXTURE_DIR}/{FIXTURE_FILE}")
    starts = [tv.launch() for _ in range(5)]
    measured = [value for value in starts if value is not None]
    if measured:
        print(f"Release cold startup TotalTime ms: {measured}, median {statistics.median(measured)}", flush=True)
    (output / "release-startup.txt").write_text(f"Cold startup TotalTime samples (ms): {measured}\n")

    tv.adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tv.text("Internal", focused=True)
    tv.screenshot(output / "home-storage.png")
    tv.key("DPAD_CENTER")
    tv.text(FIXTURE_DIR, focused=True)
    tv.screenshot(output / "folder-grid.png")
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


def storage_mutations(tv, output):
    """Exercise actual mutations, not just the availability of action buttons."""
    name = "000-Vireo-CI-mutations"
    root = f"/sdcard/{name}"
    source = "source.txt"
    renamed = "source.txt-renamed"
    payload = "Vireo mutation integrity fixture"
    tv.storage_access(True)
    # All paths are constant and restricted to this disposable emulator fixture.
    tv.adb("shell", f"rm -rf {root}; mkdir -p {root}/destination; printf '%s' '{payload}' > {root}/{source}")
    tv.launch()
    tv.select("Internal")
    tv.select(name)

    def check_file(path):
        actual = tv.adb("shell", "cat", path)
        if actual != payload:
            raise AssertionError(f"Unexpected content in {path}: {actual!r}")

    def check_absent(path):
        parent, name = path.rsplit("/", 1)
        # List the parent so a shell predicate failure cannot stand in for a
        # successful check, and a missing/unreadable directory fails the test.
        entries = tv.adb("shell", "ls", "-1a", parent).splitlines()
        if name in entries:
            raise AssertionError(f"Expected {path} to be absent; directory contains {entries!r}")

    def action(item, label):
        tv.select(item, long=True)
        tv.select(label)

    action(source, "Rename")
    field = tv.find(lambda node: node.get("class") == "android.widget.EditText", "rename field")
    if field.get("focused") != "true":
        raise AssertionError("Rename input does not have initial focus")
    tv.key("MOVE_END")
    tv.adb("shell", "input", "text", "-renamed")
    tv.dismiss_keyboard()
    tv.select("OK")
    tv.text(renamed)
    check_absent(f"{root}/{source}")
    check_file(f"{root}/{renamed}")

    action(renamed, "Copy")
    tv.select("destination")
    tv.select("Paste here")
    tv.text(renamed)
    check_file(f"{root}/{renamed}")
    check_file(f"{root}/destination/{renamed}")
    tv.key("BACK")

    action(renamed, "Create ZIP")
    tv.select("Create")
    archive = f"{renamed}.zip"
    tv.text(archive)
    local_zip = output / "mutation-fixture.zip"
    tv.adb("pull", f"{root}/{archive}", str(local_zip))
    with zipfile.ZipFile(local_zip) as zipped:
        if zipped.namelist() != [renamed] or zipped.read(renamed) != payload.encode():
            raise AssertionError("ZIP archive does not preserve source content")
    local_zip.unlink()
    check_file(f"{root}/{renamed}")

    action(renamed, "Delete")
    tv.text("Delete file?")
    tv.select("Cancel")
    check_file(f"{root}/{renamed}")
    action(renamed, "Delete")
    tv.text("Delete file?")
    tv.select("Delete")
    # Wait for the refreshed listing, then verify that only the selected file was deleted.
    for _ in range(10):
        if not any(node.get("text") == renamed for node in tv.tree().iter("node")):
            break
        time.sleep(0.25)
    check_absent(f"{root}/{renamed}")
    check_file(f"{root}/destination/{renamed}")

    action(archive, "Extract ZIP")
    tv.select("Extract")
    tv.text(renamed)
    tv.select(renamed)
    tv.text(renamed)
    check_file(f"{root}/{renamed}/{renamed}")
    tv.key("BACK")

    # Move the archive into a different folder; source must disappear and bytes remain valid.
    action(archive, "Move")
    tv.select("destination")
    tv.select("Paste here")
    tv.text(archive)
    check_absent(f"{root}/{archive}")
    tv.adb("pull", f"{root}/destination/{archive}", str(local_zip))
    with zipfile.ZipFile(local_zip) as zipped:
        if zipped.read(renamed) != payload.encode():
            raise AssertionError("Moved archive content changed")
    local_zip.unlink()
    print("PASS release rename, copy, ZIP creation/extraction, delete/cancel and move", flush=True)


def cleanup_review(tv):
    tv.storage_access(True)
    candidate = "/sdcard/000-vireo-review.tmp"
    protected = "/sdcard/000-vireo-keep.txt"
    tv.adb("shell", f"printf disposable > {candidate}; printf keep > {protected}")
    tv.launch()
    tv.text("Internal", focused=True)
    tv.key("DPAD_LEFT")
    tv.select("Cleaner", strict=True)
    tv.text("Applications")
    tv.select("Cleanup")
    tv.select("Scan storage & clean files")
    tv.select(Path(candidate).name)
    tv.select("Delete selected (1)")
    tv.text("Delete selected items?")
    tv.select("Cancel")
    if tv.adb("shell", "cat", candidate).strip() != "disposable":
        raise AssertionError("Cancel changed the cleanup candidate")
    tv.select("Delete selected (1)")
    tv.select("Delete")
    tv.find(lambda node: (node.get("text") or "").startswith("Freed:"), "cleanup result")
    if Path(candidate).name in tv.adb("shell", "ls", "-1a", "/sdcard").splitlines():
        raise AssertionError("Confirmed cleanup did not remove the selected file")
    if tv.adb("shell", "cat", protected).strip() != "keep":
        raise AssertionError("Cleanup modified an unselected file")
    print("PASS cleanup review, cancellation and confirmed deletion", flush=True)


def search_pagination(tv, output):
    tv.storage_access(True)
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


def media_playback(tv, label, output):
    tv.select("vireo-playback.wav")
    pattern = re.compile(r"^(\d+):(\d{2}) / 0:40$")
    def position():
        node = tv.find(lambda n: pattern.fullmatch(n.get("text") or "") is not None,
                       "40-second audio timeline")
        m = pattern.fullmatch(node.get("text"))
        return int(m[1]) * 60 + int(m[2])
    deadline = time.monotonic() + 15
    while position() < 2:
        if time.monotonic() > deadline:
            raise AssertionError("Playback did not advance")
        time.sleep(0.5)
    tv.key("MEDIA_PLAY_PAUSE")
    tv.text("Play")
    tv.screenshot(output / f"{label.replace(' ', '-')}-audio-paused.png")
    before = position()
    time.sleep(1.5)
    if abs(position() - before) > 1:
        raise AssertionError("Paused audio continued advancing")
    tv.key("MEDIA_FAST_FORWARD")
    if position() < before + 8:
        raise AssertionError("Audio seek did not advance by approximately ten seconds")
    tv.key("MEDIA_PLAY_PAUSE")
    tv.text("Pause")
    tv.key("BACK")
    tv.text("vireo-playback.wav")
    print(f"PASS {label} audio playback, pause, seek, resume and exit", flush=True)


def video_playback(tv, label, output):
    tv.select("vireo-video.mp4", strict=True)
    # The first hierarchy dump can finish after the four-second auto-hide.
    # Pause is idempotent and reveals controls even when the surface has focus.
    deadline = time.monotonic() + 15
    while True:
        tv.key("MEDIA_PAUSE")
        labels = [n.get("text") for n in tv.tree().iter("node") if n.get("text")]
        if any(text.startswith("ERROR_CODE_") for text in labels):
            raise AssertionError(f"{label} playback error before pause: {labels}")
        if "Play" in labels and "Info" in labels and "Fit" in labels:
            break
        if time.monotonic() >= deadline:
            raise AssertionError(f"{label} could not pause and reveal video controls: {labels}")
        time.sleep(0.25)
    tv.select("Info", strict=True)
    started = time.monotonic()
    deadline = started + 30
    while True:
        labels = [n.get("text") for n in tv.tree().iter("node") if n.get("text")]
        if "Video rendered" in labels:
            break
        if any(text.startswith("ERROR_CODE_") for text in labels):
            raise AssertionError(f"{label} playback error: {labels}")
        if time.monotonic() >= deadline:
            raise AssertionError(f"{label} first frame exceeded 30s: {labels}")
        time.sleep(0.25)
    print(f"{label} first frame observation after opening Info: {time.monotonic() - started:.2f}s", flush=True)
    tv.select("Info", strict=True)
    tv.select("Subtitles", strict=True)
    tv.text("Close tracks", focused=True)
    tv.key("DPAD_DOWN")
    tv.text("Subtitles off", focused=True)
    tv.key("DPAD_DOWN")
    tv.find(lambda n: "vireo-video.srt" in (n.get("text") or ""),
            "focused sidecar subtitle track", focused=True)
    tv.key("DPAD_CENTER")
    tv.text("Play", focused=True)
    tv.select("Info", strict=True)
    tv.find(lambda n: (n.get("text") or "").startswith("✓ application/x-media3-cues"),
            "selected subtitle track")
    tv.key("MEDIA_PLAY")
    tv.text("Subtitle cue active")
    tv.key("MEDIA_PAUSE")
    tv.screenshot(output / f"{label.replace(' ', '-')}-video-subtitle.png")
    tv.text("Play")
    tv.select("Info", strict=True)
    tv.select("Fit", strict=True)
    tv.text("Zoom")
    tv.select("Zoom", strict=True)
    tv.text("Stretch")
    tv.select("Stretch", strict=True)
    tv.text("Fit")
    tv.key("MEDIA_PLAY")
    # OK must pause after the overlay hides, even if a hierarchy capture
    # would miss the brief visible playback controls.
    time.sleep(5)
    if any(n.get("text") == "Pause" for n in tv.tree().iter("node")):
        raise AssertionError("Video overlay did not hide")
    tv.key("DPAD_CENTER")
    tv.text("Play")
    tv.key("BACK")
    tv.text("vireo-video.mp4")
    print(f"PASS {label} video rendered, subtitle cue, aspect modes, auto-hide and OK pause", flush=True)


def multitrack_playback(tv, label, output):
    tv.select("vireo-multitrack.mkv", strict=True)
    deadline = time.monotonic() + 30
    while True:
        tv.key("MEDIA_PAUSE")
        labels = [n.get("text") for n in tv.tree().iter("node") if n.get("text")]
        if any(text.startswith("ERROR_CODE_") for text in labels):
            raise AssertionError(f"{label} MKV playback error: {labels}")
        if "Play" in labels and "Info" in labels:
            break
        if time.monotonic() >= deadline:
            raise AssertionError(f"{label} MKV controls unavailable: {labels}")
        time.sleep(0.25)
    tv.select("Info", strict=True)
    deadline = time.monotonic() + 30
    while True:
        labels = [n.get("text") for n in tv.tree().iter("node") if n.get("text")]
        if any(text.startswith("ERROR_CODE_") for text in labels):
            raise AssertionError(f"{label} MKV decode error: {labels}")
        if "Video rendered" in labels and any(text.startswith("✓ audio/mp4a-latm") for text in labels):
            break
        if time.monotonic() >= deadline:
            raise AssertionError(f"{label} MKV H264/AAC not ready: {labels}")
        time.sleep(0.25)
    tv.select("Info", strict=True)
    tv.select("Subtitles", strict=True)
    tv.text("Close tracks", focused=True)
    tv.key("DPAD_DOWN")
    tv.text("Subtitles off", focused=True)
    tv.key("DPAD_DOWN")
    tv.find(lambda n: bool(n.get("text")) and n.get("text") not in ("Subtitles off", "Close tracks"),
            "focused embedded subtitle", focused=True)
    tv.key("DPAD_CENTER")
    tv.text("Play", focused=True)
    tv.select("Info", strict=True)
    tv.key("MEDIA_PLAY")
    tv.text("Subtitle cue active")
    tv.key("MEDIA_PAUSE")
    tv.screenshot(output / f"{label.replace(' ', '-')}-mkv-embedded.png")
    tv.select("Info", strict=True)
    tv.key("BACK")
    tv.text("vireo-multitrack.mkv")
    print(f"PASS {label} Matroska H264/AAC and embedded subtitle cue", flush=True)


def image_viewing(tv, label, output):
    name = "vireo-image.png"
    tv.select(name)
    started = time.monotonic()
    tv.find(lambda node: node.get("content-desc") == name, "decoded image", timeout=30)
    print(f"{label} image observed after {time.monotonic() - started:.2f}s including automation", flush=True)
    tv.select("Zoom +")
    tv.text("Pan")
    tv.select("Rotate")
    tv.screenshot(output / f"{label.replace(' ', '-')}-image.png")
    tv.select("Back")
    tv.text(name)
    print(f"PASS {label} image decode, zoom and return", flush=True)


def local_media(tv, fixture, output):
    tv.storage_access(True)
    tv.adb("shell", "mkdir", "-p", "/sdcard/000-Vireo-media")
    tv.adb("push", fixture, "/sdcard/000-Vireo-media/vireo-playback.wav")
    for name in ("vireo-video.mp4", "vireo-video.srt", "vireo-multitrack.mkv", "vireo-image.png"):
        tv.adb("push", str(Path(fixture).parent / name), f"/sdcard/000-Vireo-media/{name}")
    tv.launch()
    tv.select("Internal")
    tv.select("000-Vireo-media")
    tv.text("vireo-video.mp4")
    tv.screenshot(output / "local-media-grid.png")
    media_playback(tv, "local release", output)
    video_playback(tv, "local release", output)
    multitrack_playback(tv, "local release", output)
    image_viewing(tv, "local release", output)


def smb(tv, fixture, variant, user, password, output):
    tv.install(fixture)
    tv.adb("shell", "pm", "clear", PACKAGE)
    tv.launch()
    tv.select("Local network")
    tv.select("Manual SMB connection")
    tv.text("Server host or IP")
    for index, value in [(0, "10.0.2.2"), (1, "vireoci"), (2, user), (4, password)]:
        tv.edit(index, value)
    tv.submit_smb()
    tv.text("vireo-smb-fixture.txt")
    print(f"PASS {variant} authenticated SMB directory listing", flush=True)
    tv.screenshot(output / f"{variant}-smb-grid.png")
    media_playback(tv, f"{variant} SMB", output)
    video_playback(tv, f"{variant} SMB", output)
    multitrack_playback(tv, f"{variant} SMB", output)
    image_viewing(tv, f"{variant} SMB", output)


def initial_install(tv, hierarchy, release):
    try:
        tv.install(hierarchy)
        tv.install(release)
    except subprocess.TimeoutExpired as error:
        # The device may still be committing the session. The CI launcher
        # can retry setup on a clean guest using this dedicated exit status.
        raise SystemExit(75) from error


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--release", required=True)
    parser.add_argument("--debug", required=True)
    parser.add_argument("--hierarchy", required=True)
    parser.add_argument("--media-fixture", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--smb-user", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("A disposable Android TV emulator is required.")
    tv = Tv(args.serial)
    password = os.environ["VIREO_TEST_SMB_PASSWORD"]
    args.output.mkdir(parents=True, exist_ok=True)
    try:
        tv.configure_runtime()
        initial_install(tv, args.hierarchy, args.release)
    except (Exception, SystemExit):
        trace = traceback.format_exc()
        (args.output / "setup-failure.txt").write_text(trace)
        print(trace, file=sys.stderr, flush=True)
        for name, command in [
            ("adb-devices", ["adb", "devices", "-l"]),
            ("boot-state", ["adb", "-s", args.serial, "shell", "getprop", "sys.boot_completed"]),
            ("package-manager", ["adb", "-s", args.serial, "shell", "pm", "path", "android"]),
            ("setup-logcat", ["adb", "-s", args.serial, "logcat", "-b", "all", "-d"]),
            ("disk-space", ["adb", "-s", args.serial, "shell", "df", "-h", "/data"]),
            ("installer-state", ["adb", "-s", args.serial, "shell", "dumpsys", "package", "installs"]),
        ]:
            try:
                result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, timeout=10, check=False)
                (args.output / f"{name}.txt").write_text(result.stdout)
            except (OSError, subprocess.SubprocessError) as error:
                (args.output / f"{name}.txt").write_text(str(error))
        raise
    failures = []
    checks = [
        ("picker", lambda: picker_cancellation(tv)),
        ("storage", lambda: storage_and_timing(tv, args.output)),
        ("search", lambda: search_pagination(tv, args.output)),
        ("cleanup review", lambda: cleanup_review(tv)),
        ("storage mutations", lambda: storage_mutations(tv, args.output)),
        ("local media", lambda: local_media(tv, args.media_fixture, args.output)),
        ("release SMB", lambda: smb(tv, args.release, "release", args.smb_user, password, args.output)),
        ("debug SMB", lambda: smb(tv, args.debug, "debug", args.smb_user, password, args.output)),
    ]
    for label, check in checks:
        try:
            check()
            tv.screenshot(args.output / f"{label.replace(' ', '-')}-success.png")
        except Exception:
            failures.append(label)
            trace = traceback.format_exc()
            (args.output / f"{label.replace(' ', '-')}-failure.txt").write_text(trace)
            print(f"FAIL {label}\n{trace}", file=sys.stderr, flush=True)
            try:
                (args.output / f"{label.replace(' ', '-')}-window.xml").write_text(ET.tostring(tv.tree(), encoding="unicode"))
            except (OSError, subprocess.SubprocessError, ET.ParseError, AssertionError) as evidence_error:
                print(f"Hierarchy capture failed for {label}: {evidence_error}", file=sys.stderr, flush=True)
            try:
                (args.output / f"{label.replace(' ', '-')}-logcat.txt").write_text(tv.adb("logcat", "-d", "-t", "1500"))
                (args.output / f"{label.replace(' ', '-')}-window-state.txt").write_text(tv.adb("shell", "dumpsys", "window"))
            except (OSError, subprocess.SubprocessError) as evidence_error:
                print(f"Diagnostic capture failed for {label}: {evidence_error}", file=sys.stderr, flush=True)
    if failures:
        raise SystemExit(f"Failed checks: {', '.join(failures)}")
