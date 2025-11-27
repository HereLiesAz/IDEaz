#!/bin/bash
# A script to correctly set up a basic Android development environment.

# Exit on error, print commands
set -euo pipefail
set -x

# --- 1. Install Java Development Kit (JDK) 17 ---
echo "➡️ Installing OpenJDK 17..."
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# Verify Java installation
JAVA_17_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
if [ ! -d "$JAVA_17_HOME" ] || [ ! -f "$JAVA_17_HOME/bin/java" ]; then
    echo "❌ ERROR: OpenJDK 17 installation failed or was not found at the expected path."
    echo "Please check your system's package manager and Java installation."
    exit 1
fi
echo "✅ OpenJDK 17 installed successfully."


# --- 2. Install Android Command Line Tools ---
echo "➡️ Setting up Android SDK..."

# Define paths
ANDROID_SDK_ROOT="$HOME/Android/sdk"
echo "SDK Root will be: $ANDROID_SDK_ROOT"

TOOLS_VERSION="11076708"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${TOOLS_VERSION}_latest.zip"
TOOLS_ZIP="/tmp/android-tools.zip"

# Create parent directory for cmdline-tools
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
if [ ! -d "$ANDROID_SDK_ROOT/cmdline-tools" ]; then
    echo "❌ ERROR: Failed to create SDK directory at $ANDROID_SDK_ROOT/cmdline-tools."
    exit 1
fi

# Download and place the tools in their final destination
echo "Downloading tools from $TOOLS_URL..."
wget -q "$TOOLS_URL" -O "$TOOLS_ZIP"
if [ ! -f "$TOOLS_ZIP" ]; then
    echo "❌ ERROR: Failed to download Android command line tools."
    exit 1
fi

# Unzip and restructure the directory
echo "Unzipping tools..."
rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
unzip -oq "$TOOLS_ZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools"
mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
rm "$TOOLS_ZIP"

# Verify tools installation
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$SDKMANAGER" ]; then
    echo "❌ ERROR: sdkmanager not found after installation."
    exit 1
fi
echo "✅ Android command line tools installed successfully."


# --- 3. Set Environment Variables Permanently ---
echo "➡️ Configuring environment variables..."

# Auto-detect the user's shell configuration file
if [[ "$SHELL" == */bash ]]; then
    RC_FILE="$HOME/.bashrc"
elif [[ "$SHELL" == */zsh ]]; then
    RC_FILE="$HOME/.zshrc"
else
    RC_FILE="$HOME/.profile"
fi
echo "Updating shell configuration at: $RC_FILE"

# Use a function to avoid adding duplicate lines
append_if_missing() {
    CONTENT="$1"
    FILE="$2"
    case "$(cat "$FILE")" in
        *"$CONTENT"*)
            echo "Content already exists in $FILE: $CONTENT"
            ;;
        *)
            echo "Appending to $FILE: $CONTENT"
            echo "$CONTENT" >> "$FILE"
            ;;
    esac
}

# Add environment variables
append_if_missing '' "$RC_FILE"
append_if_missing '# Android & Java Environment' "$RC_FILE"
append_if_missing "export JAVA_HOME=$JAVA_17_HOME" "$RC_FILE"
append_if_missing 'export ANDROID_SDK_ROOT=$HOME/Android/sdk' "$RC_FILE"
append_if_missing 'export ANDROID_HOME=$ANDROID_SDK_ROOT' "$RC_FILE"
append_if_missing 'export PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools' "$RC_FILE"
append_if_missing 'export PATH=$JAVA_HOME/bin:$PATH' "$RC_FILE"
echo "✅ Environment variables configured."


# --- 4. Install SDK Packages ---
echo "➡️ Installing SDK packages (platform-tools, build-tools, platforms)..."
# Export variables for the current session to use sdkmanager
export JAVA_HOME=$JAVA_17_HOME
export ANDROID_HOME=$ANDROID_SDK_ROOT
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# The `yes` command automatically accepts licenses.
yes | "$SDKMANAGER" --licenses > /dev/null || true

# Install essential packages
echo "Installing platform-tools..."
"$SDKMANAGER" "platform-tools" > /dev/null
echo "Installing build-tools..."
"$SDKMANAGER" "build-tools;34.0.0" > /dev/null
echo "Installing Android 36 platform..."
"$SDKMANAGER" "platforms;android-36" > /dev/null

# Verify installation of platform-tools
if [ ! -d "$ANDROID_SDK_ROOT/platform-tools" ]; then
    echo "❌ ERROR: Failed to install platform-tools."
    exit 1
fi
echo "✅ SDK packages installed."


echo "✅🎉 Android development environment setup complete!"
echo "Please run 'source $RC_FILE' or restart your terminal to apply the changes."
set +x