#!/usr/bin/env bash
# Source from Android build and emulator jobs to use the same command-line tools.
android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$android_sdk" ]]; then
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK directory." >&2
  return 1
fi

android_cli="$android_sdk/cmdline-tools/latest/bin"
if [[ ! -x "$android_cli/sdkmanager" || ! -x "$android_cli/avdmanager" ]]; then
  android_cli=
  while IFS= read -r candidate; do
    if [[ -x "$candidate/sdkmanager" && -x "$candidate/avdmanager" ]]; then
      android_cli="$candidate"
      break
    fi
  done < <(find "$android_sdk/cmdline-tools" -mindepth 2 -maxdepth 2 -type d -name bin 2>/dev/null | sort -Vr)
fi

if [[ -z "$android_cli" ]]; then
  echo "Android SDK Command-Line Tools with sdkmanager and avdmanager are required in $android_sdk." >&2
  return 1
fi

export ANDROID_HOME="$android_sdk"
export PATH="$android_cli:$android_sdk/platform-tools:$android_sdk/emulator:$PATH"
