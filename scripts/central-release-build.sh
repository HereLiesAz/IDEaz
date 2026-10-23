#!/usr/bin/env bash
# Build entry point for IDEaz's central release executors in HereLiesAz/workflows
# (android-play-release / android-github-release). Their purpose profiles run
#   bash scripts/central-release-build.sh aab   -> :app:bundlePlay (policy-safe Play AAB)
#   bash scripts/central-release-build.sh apk   -> :app:bundleRelease + bundletool universal APK
# with any extra Gradle args (Play's -PversionCodeOverride/-PversionName/...) appended.
#
# Signing comes from the executors' KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS /
# KEY_PASSWORD env, which app/build.gradle.kts' `release` signingConfig already reads.
# IDEaz's keystore uses the store password for the key (as the old local workflows
# did), so KEY_PASSWORD falls back to KEYSTORE_PASSWORD when that secret is unset.
#
# Why the APK is fused rather than taken from assembleRelease: :webruntime is an
# install-time dynamic-feature module carrying the ~4.5MB web runtime. A plain
# assembleRelease emits :app's base APK without assets/ideaz-runtime/; only a
# bundletool *universal* APK built from the .aab genuinely fuses it into one
# sideloadable file. See docs/build_pipeline.md.
set -euo pipefail

mode="${1:?usage: central-release-build.sh aab|apk [gradle args...]}"
shift

export KEY_PASSWORD="${KEY_PASSWORD:-${KEYSTORE_PASSWORD:-}}"

# android-github-release passes no Gradle version args; it exports the canonical
# version instead. Play already passes -PversionCodeOverride, which wins.
args=("$@")
if [ -n "${ANDROID_VERSION_CODE:-}" ] && ! printf '%s\n' "${args[@]+"${args[@]}"}" | grep -q '^-PversionCodeOverride='; then
  args+=("-PversionCodeOverride=$ANDROID_VERSION_CODE")
  if [ -n "${VERSION:-}" ]; then args+=("-PversionName=$VERSION"); fi
  if [ -n "${VERSION_BUILD:-}" ]; then args+=("-PversionBuild=$VERSION_BUILD"); fi
fi

case "$mode" in
  aab)
    ./gradlew :app:bundlePlay --no-daemon --stacktrace "${args[@]+"${args[@]}"}"
    ;;
  apk)
    ./gradlew :app:bundleRelease --no-daemon --stacktrace "${args[@]+"${args[@]}"}"
    aab="app/build/outputs/bundle/release/app-release.aab"
    test -s "$aab" || { echo "Expected bundle not found: $aab" >&2; exit 1; }
    if [ -n "${KEYSTORE_FILE:-}" ] && [ -s "$KEYSTORE_FILE" ]; then
      ks="$KEYSTORE_FILE"; ks_pass="$KEYSTORE_PASSWORD"; ks_alias="$KEY_ALIAS"; key_pass="$KEY_PASSWORD"
    else
      # Local verification only: AGP's well-known debug keystore (same as the
      # `debug` signingConfig). Never what the central executor publishes.
      ks="app/debug.keystore"; ks_pass="android"; ks_alias="androiddebugkey"; key_pass="android"
    fi
    bundletool_version="1.18.3"
    bundletool="app/build/tmp/bundletool-${bundletool_version}.jar"
    mkdir -p "$(dirname "$bundletool")"
    test -s "$bundletool" || curl -sSL --fail -o "$bundletool" \
      "https://github.com/google/bundletool/releases/download/${bundletool_version}/bundletool-all-${bundletool_version}.jar"
    out_dir="app/build/outputs/universal/release"
    rm -rf "$out_dir"; mkdir -p "$out_dir"
    java -jar "$bundletool" build-apks \
      --bundle="$aab" --output="$out_dir/app-release.apks" --mode=universal \
      --ks="$ks" --ks-pass="pass:$ks_pass" --ks-key-alias="$ks_alias" --key-pass="pass:$key_pass" \
      --overwrite
    unzip -p "$out_dir/app-release.apks" universal.apk > "$out_dir/IDEaz-universal-release.apk"
    rm -f "$out_dir/app-release.apks"
    # Captured before grep (not piped) so grep -q can't SIGPIPE unzip under pipefail.
    listing="$(unzip -l "$out_dir/IDEaz-universal-release.apk")"
    grep -q 'assets/ideaz-runtime/' <<<"$listing" || {
      echo "::error::universal APK is missing assets/ideaz-runtime/ - the web runtime did not survive fusing" >&2
      exit 1
    }
    ;;
  *)
    echo "Unknown mode: $mode (expected aab or apk)" >&2
    exit 2
    ;;
esac
