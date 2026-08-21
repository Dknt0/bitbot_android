# Bitbot Android — Development Environment Setup

Guide for setting up an Android development environment on Ubuntu 24.04 LTS.

## What You Need

| Component | Version | Required |
|---|---|---|
| OpenJDK | 21 | Yes |
| Android SDK Command-Line Tools | 12.0 | Yes |
| Android SDK Platform | 34 | Yes |
| Android Build Tools | 34.0.0 | Yes |
| Android Platform Tools (adb) | latest | Yes |
| Gradle | 8.6 (via wrapper) | Yes |

## 1. Install OpenJDK 21

```bash
sudo apt install openjdk-21-jdk
```

Verify:

```bash
java -version
# openjdk version "21.0.x"
```

## 2. Install Android SDK

### 2.1 Download command-line tools

```bash
mkdir -p ~/Software/Android/cmdline-tools
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip
mkdir -p ~/Software/Android/cmdline-tools/latest
mv cmdline-tools/* ~/Software/Android/cmdline-tools/latest/
```

### 2.2 Accept licenses

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
ANDROID_HOME=$HOME/Software/Android \
  sh $HOME/Software/Android/cmdline-tools/latest/bin/sdkmanager --licenses
```

Type `y` for each prompt.

### 2.3 Install SDK packages

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
ANDROID_HOME=$HOME/Software/Android \
  sh $HOME/Software/Android/cmdline-tools/latest/bin/sdkmanager \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "platform-tools"
```

## 3. Set Environment Variables

Add to `~/.zshrc` (or `~/.bashrc` if using bash):

```bash
## Android SDK
export ANDROID_HOME=$HOME/Software/Android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

Reload:

```bash
source ~/.zshrc
```

Verify:

```bash
adb version
sdkmanager --version
```

## 4. Generate Gradle Wrapper JAR

The `gradle-wrapper.jar` is excluded from git. If it's missing after cloning:

```bash
cd /tmp
wget https://services.gradle.org/distributions/gradle-8.6-bin.zip
unzip gradle-8.6-bin.zip
cd <project-root>
/tmp/gradle-8.6/bin/gradle wrapper --gradle-version 8.6
```

## 5. Configure Project SDK Path

Create `local.properties` in the project root (this file is gitignored):

```properties
sdk.dir=/home/<your-username>/Software/Android
```

## 6. Build

```bash
# Debug
./gradlew assembleDebug

# Release (requires keystore, see section 7)
./gradlew assembleRelease
```

Output paths:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 7. Release Signing

The release build type requires a signing keystore. Create one:

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias bitbot \
  -dname "CN=Bitbot, OU=Dev, O=Dknt, L=Beijing, ST=Beijing, C=CN"
```

Create `key.properties` in the project root (gitignored):

```properties
storeFile=release.jks
storePassword=<your-password>
keyAlias=bitbot
keyPassword=<your-password>
```

> **Keep `release.jks` and `key.properties` safe.** Without the keystore, you cannot sign updates. Future installs would require uninstalling the app first.

## 8. Install on Device

### Phone setup

1. **Settings > About phone** — tap **Build number** 7 times to enable Developer Options
2. **Settings > Developer Options** — enable **USB debugging**
3. **Settings > Developer Options** — enable **Install via USB** (required on some OEMs)
4. Connect phone via USB

### Install

```bash
adb devices    # should show device, not "unauthorized"
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Troubleshooting

| Error | Fix |
|---|---|
| `unauthorized` | Tap **Allow** on the USB debugging popup on the phone |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall first: `adb uninstall com.bitbot` |
| `INSTALL_FAILED_USER_RESTRICTED` | Enable **Install via USB** in Developer Options |

## SDK Directory Layout

```
~/Software/Android/
├── cmdline-tools/latest/bin/sdkmanager
├── build-tools/34.0.0/
├── platforms/android-34/
└── platform-tools/adb
```
