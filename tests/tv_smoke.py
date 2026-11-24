"""Exercise the main Android TV routes with remote keys on an isolated emulator."""

import argparse
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.reiniertutoriales.vireoexplorertv"


def run_smoke(adb, serial, expect_picker_stub):
    def device(*args):
        return subprocess.check_output([adb, "-s", serial, *args], encoding="utf-8", timeout=30)

    def key(code):
        device("shell", "input", "keyevent", code)

    def hierarchy():
        device("shell", "uiautomator", "dump", "/sdcard/vireo-smoke.xml")
        return ET.fromstring(device("shell", "cat", "/sdcard/vireo-smoke.xml"))

    def expect(label, focused=False):
        expected = label.casefold()
        for _ in range(3):
            tree = hierarchy()
            for node in tree.iter("node"):
                if focused and node.get("focused") != "true":
                    continue
                if any(child.get("text", "").casefold() == expected for child in node.iter("node")):
                    print(f"PASS {'focused ' if focused else ''}{label}")
                    return
            time.sleep(0.5)
        raise AssertionError(f"Expected {'focus on ' if focused else ''}{label!r}")

    device("shell", "am", "force-stop", PACKAGE)
    result = device(
        "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LEANBACK_LAUNCHER", "-n", f"{PACKAGE}/.MainActivity",
    )
    if "Status: ok" not in result:
        raise AssertionError(result)
    expect("Internal", focused=True)
    home_labels = {node.get("text") for node in hierarchy().iter("node")}
    required = {"Storage", "Categories", "Videos", "Audio", "Photos", "Documents", "ZIP archives", "APK files"}
    if missing := required - home_labels:
        raise AssertionError(f"Missing home labels: {sorted(missing)}")

    key("KEYCODE_DPAD_DOWN")
    expect("Local network", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("Back", focused=True)
    key("KEYCODE_DPAD_RIGHT")
    expect("Connect", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("Connect SMB")
    key("KEYCODE_BACK")
    # A second Back is needed on devices that raise the on-screen keyboard.
    try:
        expect("Connect", focused=True)
    except AssertionError:
        key("KEYCODE_BACK")
        expect("Connect")
    key("KEYCODE_BACK")
    expect("Internal", focused=True)

    key("KEYCODE_DPAD_RIGHT")
    expect("USB", focused=True)
    key("KEYCODE_DPAD_DOWN")
    expect("Folders", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("Files", focused=True)
    expect("Add folder")
    key("KEYCODE_BACK")
    expect("Internal", focused=True)

    key("KEYCODE_DPAD_DOWN")
    key("KEYCODE_DPAD_DOWN")
    expect("Videos", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("Files", focused=True)
    expect("Videos")
    expect("Choose an authorized folder to filter its contents.")
    key("KEYCODE_BACK")
    expect("Internal", focused=True)

    def focus_home_rail():
        key("KEYCODE_DPAD_LEFT")
        for _ in range(3):
            focused = [node for node in hierarchy().iter("node") if node.get("focused") == "true"]
            if any(child.get("text") == "Home" for node in focused for child in node.iter("node")):
                return
            key("KEYCODE_DPAD_UP")
        raise AssertionError("Could not reach Home in the navigation rail")

    focus_home_rail()
    for _ in range(3):
        key("KEYCODE_DPAD_DOWN")
    expect("Cleaner", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("Apps & storage")
    expect("Back", focused=True)
    key("KEYCODE_BACK")
    expect("Internal", focused=True)

    focus_home_rail()
    for _ in range(5):
        key("KEYCODE_DPAD_DOWN")
    expect("Favorites", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("No favorite folders yet.")
    key("KEYCODE_DPAD_UP")
    expect("Transfers", focused=True)
    key("KEYCODE_DPAD_CENTER")
    expect("No transfers in progress.")
    key("KEYCODE_BACK")
    expect("Internal", focused=True)

    if expect_picker_stub:
        key("KEYCODE_DPAD_CENTER")
        expect("No folder authorized. If your TV lacks a folder picker, use SMB network.")



if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--expect-picker-stub", action="store_true")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use an isolated emulator serial.")
    run_smoke(args.adb, args.serial, args.expect_picker_stub)
