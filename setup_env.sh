#!/bin/bash
# A script to correctly set up an Android development environment for Android 16 (API 36).

# Exit on error, print commands
set -euo pipefail
set -x

# --- 1. Install Java (JDK 21) & KVM ---
echo "➡️ Installing OpenJDK 21 and KVM dependencies..."
sudo apt-get update
# openjdk-21-jdk: Required for Android API 36+
# qemu-kvm libvirt...: Required for Hardware Accelerated Emulation
sudo apt-get install -y openjdk-21-jdk qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils

# Verify Java installation
JAVA_21_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

if [ ! -d "$JAVA_21_HOME" ] || [ ! -f "$JAVA_21_HOME/bin/java" ]; then
    echo "❌ ERROR: OpenJDK 21 installation failed."
    exit 1
fi

# Add user to KVM group
echo "➡️ Adding user to 'kvm' group..."
sudo adduser "$USER" kvm || true
echo "✅ Prerequisites installed."


# --- 2. Install Android Command Line Tools ---
echo "➡️ Setting up Android SDK..."

ANDROID_SDK_ROOT="$HOME/Android/sdk"
# Latest Command Line Tools
TOOLS_VERSION="11076708" 
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip"
TOOLS_ZIP="/tmp/android-tools.zip"

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# Download tools
if [ ! -f "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Downloading tools from $TOOLS_URL..."
    wget -q "$TOOLS_URL" -O "$TOOLS_ZIP"
    
    rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    unzip -oq "$TOOLS_ZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools"
    mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    rm "$TOOLS_ZIP"
else
    echo "Tools already downloaded."
fi

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"


# --- 3. Set Environment Variables Permanently ---
echo "➡️ Configuring environment variables..."

if [[ "$SHELL" == */bash ]]; then
    RC_FILE="$HOME/.bashrc"
elif [[ "$SHELL" == */zsh ]]; then
    RC_FILE="$HOME/.zshrc"
else
    RC_FILE="$HOME/.profile"
fi

append_if_missing() {
    CONTENT="$1"
    FILE="$2"
    if ! grep -qF "$CONTENT" "$FILE"; then
        echo "Appending to $FILE: $CONTENT"
        echo "$CONTENT" >> "$FILE"
    fi
}

append_if_missing '' "$RC_FILE"
append_if_missing '# Android & Java Environment' "$RC_FILE"
append_if_missing "export JAVA_HOME=$JAVA_21_HOME" "$RC_FILE"
append_if_missing 'export ANDROID_SDK_ROOT=$HOME/Android/sdk' "$RC_FILE"
append_if_missing 'export ANDROID_HOME=$ANDROID_SDK_ROOT' "$RC_FILE"
# 'emulator' path MUST be before 'platform-tools'
append_if_missing 'export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools' "$RC_FILE"
append_if_missing 'export PATH=$JAVA_HOME/bin:$PATH' "$RC_FILE"
echo "✅ Environment variables configured."


# --- 4. Install SDK Packages (API 36) ---
echo "➡️ Installing SDK packages for Android 36..."

export JAVA_HOME=$JAVA_21_HOME
export ANDROID_HOME=$ANDROID_SDK_ROOT
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# Accept licenses (Crucial for API 36/Preview versions)
yes | "$SDKMANAGER" --licenses > /dev/null || true

# TARGET PACKAGES
PLATFORM_PKG="platforms;android-36"
BUILD_TOOLS_PKG="build-tools;36.0.0"
SYS_IMG_PKG="system-images;android-36;google_apis;x86_64"

echo "Installing essential tools..."
"$SDKMANAGER" "platform-tools" "emulator" "$BUILD_TOOLS_PKG" > /dev/null

echo "Installing Android Platform 36..."
"$SDKMANAGER" "$PLATFORM_PKG" > /dev/null

echo "Downloading System Image for Android 36 (Large download)..."
"$SDKMANAGER" "$SYS_IMG_PKG" > /dev/null

if [ ! -d "$ANDROID_SDK_ROOT/emulator" ]; then
    echo "❌ ERROR: Failed to install emulator."
    exit 1
fi
echo "✅ SDK and Emulator packages installed."


# --- 5. Create Android Virtual Device (AVD) ---
echo "➡️ Creating Android Virtual Device (AVD)..."

AVD_NAME="DebugEmulator36"

if "$AVDMANAGER" list avd | grep -q "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' already exists. Skipping creation."
else
    # Create AVD with Pixel device profile
    echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYS_IMG_PKG" --device "pixel" --force
    echo "✅ AVD '$AVD_NAME' created successfully."
fi

echo "✅🎉 Android Setup Complete (Android 36)!"
echo "--------------------------------------------------------"
echo "1. Apply changes: source $RC_FILE"
echo "2. Run the emulator: emulator -avd $AVD_NAME"
echo "--------------------------------------------------------"
set +x
