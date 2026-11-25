#!/usr/bin/env bash
set -euo pipefail
umask 077

repo=ReinierTutoriales/VireoExplorerTV
secret=VIREO_ANDROID_SIGNING
alias=vireoexplorertv
backup_dir="${1:-"$HOME/.local/share/VireoExplorerTV/signing"}"

secrets="$(gh secret list --repo "$repo" --json name)"
if jq -e --arg name "$secret" 'any(.[]; .name == $name)' <<< "$secrets" >/dev/null; then
  echo "Signing is already configured. Keep using the existing key." >&2
  exit 1
fi

mkdir -p -- "$backup_dir"
chmod 700 -- "$backup_dir"
if [[ ! -e "$backup_dir/release.p12" ]]; then
  [[ -s "$backup_dir/password" ]] || openssl rand -hex 32 > "$backup_dir/password"
  temp_dir="$(mktemp -d "$backup_dir/.key.XXXXXX")"
  trap 'rm -rf -- "$temp_dir"' EXIT
  keytool -genkeypair -keystore "$temp_dir/release.p12" -storetype PKCS12 \
    -storepass:file "$backup_dir/password" -keypass:file "$backup_dir/password" \
    -alias "$alias" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=VireoExplorerTV'
  mv -- "$temp_dir/release.p12" "$backup_dir/release.p12"
fi

certificate="$(keytool -exportcert -keystore "$backup_dir/release.p12" -storetype PKCS12 \
  -storepass:file "$backup_dir/password" -alias "$alias" | openssl dgst -sha256 -r)"
certificate="${certificate%% *}"
base64 < "$backup_dir/release.p12" |
  jq -Rs --rawfile password "$backup_dir/password" --arg alias "$alias" --arg certificate "$certificate" '
    {keystore_base64: gsub("[\r\n]"; ""),
     password: ($password | rtrimstr("\n")),
     alias: $alias,
     certificate_sha256: $certificate}
  ' | gh secret set "$secret" --repo "$repo"

gh secret list --repo "$repo" --json name |
  jq -e --arg name "$secret" 'any(.[]; .name == $name)' >/dev/null
printf 'Signing configured. Back up the keystore and password securely from %s.\n' "$backup_dir"
printf 'Certificate SHA-256: %s\n' "$certificate"
