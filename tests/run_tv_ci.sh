#!/usr/bin/env bash
# Official Android SDK lifecycle, with one isolated AVD per matrix job.
set -euo pipefail
mkdir -p runtime-evidence
exec > >(tee runtime-evidence/host-setup.log) 2>&1
sdk="${ANDROID_HOME:?Android SDK required}"
export PATH="$sdk/platform-tools:$sdk/emulator:$sdk/cmdline-tools/latest/bin:$PATH"
export ANDROID_AVD_HOME="$RUNNER_TEMP/vireo-avd"
mkdir -p "$ANDROID_AVD_HOME"
image="system-images;android-${TV_API};android-tv;${TV_ARCH}"
sdkmanager --install --channel=0 platform-tools emulator "$image"
adb version
emulator -version
emulator -accel-check
free -h
df -h "$RUNNER_TEMP"
# Start the host daemon before the guest; don't reset it during a live install.
adb start-server
echo no | avdmanager create avd --force --name vireo-ci --package "$image" --device tv_1080p
emulator -avd vireo-ci -port 5554 -no-window -gpu swiftshader -feature -Vulkan -no-snapshot -no-audio -no-boot-anim -cores 2 -memory 3072 -accel on > runtime-evidence/emulator.log 2>&1 &
emu_pid=$!
cleanup() {
  status=$?
  trap - EXIT
  timeout 10 adb devices -l > runtime-evidence/final-adb.txt 2>&1 || true
  timeout 15 adb -s emulator-5554 logcat -d -t 3000 > runtime-evidence/final-logcat.txt 2>&1 || true
  timeout 10 adb -s emulator-5554 emu kill || true
  kill "$emu_pid" 2>/dev/null || true
  wait "$emu_pid" 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT
python3 tests/tv_runtime.py --release artifacts/release/app-release.apk --debug artifacts/debug/app-debug.apk --hierarchy artifacts/hierarchy/tv-test-debug.apk --media-fixture /tmp/vireo-ci-smb/vireo-playback.wav --output runtime-evidence --smb-user "$(id -un)"
