#!/bin/bash
# A script to correctly set up a basic Android development environment with Emulator support.

# Exit on error, print commands
set -euo pipefail
set -x

# --- 1. Install Java (JDK 21) & KVM (Hardware Acceleration) ---
echo "➡️ Installing OpenJDK 21 and KVM dependencies..."
sudo apt-get update
# openjdk-21-jdk: Required for latest Android SDK tools and Gradle
# qemu-kvm libvirt...: Required for Hardware Accelerated Emulation
sudo apt-get install -y openjdk-21-jdk qemu-kvm libvirt-daemon-system libvirt-clients bridge-utils

# Verify Java installation
# Standard path for OpenJDK 21 on Debian/Ubuntu systems
JAVA_21_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

if [ ! -d "$JAVA_21_HOME" ] || [ ! -f "$JAVA_21_HOME/bin/java" ]; then
    echo "❌ ERROR: OpenJDK 21 installation failed or path not found."
    echo "Expected location: $JAVA_21_HOME"
    exit 1
fi

# Add user to KVM group to allow running emulator without sudo
echo "➡️ Adding user to 'kvm' group..."
sudo adduser "$USER" kvm || true
echo "✅ Prerequisites installed."


# --- 2. Install Android Command Line Tools ---
echo "➡️ Setting up Android SDK..."

ANDROID_SDK_ROOT="$HOME/Android/sdk"
# Using latest command line tools
TOOLS_VERSION="11076708" 
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip"
TOOLS_ZIP="/tmp/android-tools.zip"

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# Download tools if not already present
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
# IMPORTANT: 'emulator' must come BEFORE 'platform-tools' in PATH to avoid binary conflicts
append_if_missing 'export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools' "$RC_FILE"
append_if_missing 'export PATH=$JAVA_HOME/bin:$PATH' "$RC_FILE"
echo "✅ Environment variables configured."


# --- 4. Install SDK Packages & Emulator Images ---
echo "➡️ Installing SDK packages, Emulator, and System Images..."

# Export variables for current session so sdkmanager works immediately
export JAVA_HOME=$JAVA_21_HOME
export ANDROID_HOME=$ANDROID_SDK_ROOT
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# Accept licenses
yes | "$SDKMANAGER" --licenses > /dev/null || true

# Define System Image: Android 14 (API 34) with Google APIs (Standard for UI testing)
SYS_IMG="system-images;android-34;google_apis;x86_64"

echo "Installing essential packages..."
"$SDKMANAGER" "platform-tools" "emulator" "build-tools;34.0.0" "platforms;android-34" > /dev/null

echo "Downloading System Image (This is large ~1.5GB, please wait)..."
"$SDKMANAGER" "$SYS_IMG" > /dev/null

if [ ! -d "$ANDROID_SDK_ROOT/emulator" ]; then
    echo "❌ ERROR: Failed to install emulator."
    exit 1
fi
echo "✅ SDK and Emulator packages installed."


# --- 5. Create Android Virtual Device (AVD) ---
echo "➡️ Creating Android Virtual Device (AVD)..."

AVD_NAME="DebugEmulator"

# Check if AVD already exists
if "$AVDMANAGER" list avd | grep -q "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' already exists. Skipping creation."
else
    # Create the AVD using the pixel device profile
    echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYS_IMG" --device "pixel" --force
    echo "✅ AVD '$AVD_NAME' created successfully."
fi

echo "✅🎉 Android Development Environment (JDK 21) Setup Complete!"
echo "--------------------------------------------------------"
echo "1. Apply changes: source $RC_FILE"
echo "2. Run the emulator: emulator -avd $AVD_NAME"
echo "--------------------------------------------------------"
set +x
