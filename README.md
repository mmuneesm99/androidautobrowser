# My Browser

A distraction-optimized web browser for **Android Auto** and phone, with a premium infotainment-style home screen.

## Download

[![Download APK](https://img.shields.io/github/v/release/mmuneesm99/androidautobrowser?label=Download%20APK&logo=android&color=3DDC84)](https://github.com/mmuneesm99/androidautobrowser/releases/latest)

| | |
|---|---|
| **Latest APK** | [app-debug.apk](https://github.com/mmuneesm99/androidautobrowser/releases/latest/download/app-debug.apk) |
| **All releases** | [GitHub Releases](https://github.com/mmuneesm99/androidautobrowser/releases) |

**Install on phone**

1. Download the APK on your device (or copy it via USB).
2. Open the file and tap **Install** (allow installs from your browser or file manager if prompted).
3. Or from a computer with [ADB](https://developer.android.com/tools/adb):

   ```bash
   adb install -r app-debug.apk
   ```

No build tools required for this path — use the [Setup](#setup) section below only if you want to compile from source.

## Features

- **Android Auto + phone** — same app on the car display and on your phone
- **Infotainment home screen** — clock, date, location, weather, Malayalam news headlines, and weather alerts
- **Web browsing** — open YouTube, Google, or any link with an unrestricted `WebView`
- **Auto-hiding controls** — browser chrome stays out of the way while you browse
- **Resume session** — pick up where you left off from the home screen

## Requirements

- **Phone:** Android 10+ (API 29)
- **Android Auto:** compatible head unit (Android 15+ on the phone is recommended for full car Activity projection)
- **Development:** JDK 17 and Android Studio (Ladybug or newer), or JDK 17 + Android SDK command-line tools

## Setup

### 1. Install tools

1. Install [Android Studio](https://developer.android.com/studio).
2. In Android Studio, open **SDK Manager** and install:
   - **Android SDK Platform 35** (Android 15)
   - **Android SDK Build-Tools** (latest)
   - **Android SDK Platform-Tools**
3. Ensure **JDK 17** is selected under **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.

### 2. Get the project

```bash
git clone https://github.com/mmuneesm99/androidautobrowser.git
cd androidautobrowser
```

Or download and extract the repository ZIP, then open that folder.

### 3. Configure the Android SDK path

Create `local.properties` in the project root (this file is not committed to git):

**Windows**

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

**macOS**

```properties
sdk.dir=/Users/YourName/Library/Android/sdk
```

**Linux**

```properties
sdk.dir=/home/YourName/Android/Sdk
```

Android Studio usually creates this file automatically when you first open the project.

### 4. Open in Android Studio

1. **File → Open** and select the project folder.
2. Wait for Gradle sync to finish (the wrapper downloads Gradle 8.9 on first run).
3. Connect your phone with **USB debugging** enabled, or start an emulator (API 29+).

### 5. Build and install

**From Android Studio**

1. Select your device or emulator in the toolbar.
2. Click **Run** (green play button), or **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

**From the command line**

```bash
./gradlew assembleDebug
```

Windows:

```bat
gradlew.bat assembleDebug
```

Install the debug APK manually:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 6. First run on the phone

1. Open **My Browser** on the device.
2. When prompted, allow **Location** so the home screen can show weather and your city.
3. Grant **Internet** access (required for browsing, weather, and news).
4. Use the home screen tiles to open **Chrome** (Google) or **Resume** your last page.

### 7. Set up Android Auto

1. Install **Android Auto** on your phone from the Play Store (if not preinstalled).
2. Enable **Developer mode** in Android Auto (tap the version number several times in Settings) if you need to test unreleased builds.
3. Install **My Browser** on the same phone.
4. Connect the phone to the car via **USB** or **wireless Android Auto**.
5. On the car launcher, open **My Browser**.

> **Safety:** Use only when parked or as a passenger. Browsing while driving is unsafe and may be restricted by your head unit or local laws.

### Troubleshooting

| Issue | What to try |
|-------|-------------|
| Gradle sync fails | Confirm `local.properties` points to a valid SDK; use JDK 17. |
| App not in Android Auto | Reconnect USB, restart Android Auto, confirm the app installed successfully on the phone. |
| Weather / news not loading | Check internet connection; allow location or wait a few seconds for IP-based fallback. |
| Location shows “unavailable” | Enable GPS and grant location permission in system Settings → Apps → My Browser. |

## Build (release)

For a signed release APK, configure signing in Android Studio (**Build → Generate Signed Bundle / APK**) or add a `release` signing config in `app/build.gradle.kts`. Then run:

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/`

## Project structure

```
app/src/main/java/com/androidautobrowser/browser/
├── MainActivity.kt              # Home screen + browser
├── BrowserDestination.kt        # YouTube / Chrome shortcuts
├── info/                        # Weather, news, network helpers
├── location/                    # GPS + geocoding
└── web/                         # WebView configuration
```

## License

MIT — see [LICENSE](LICENSE).
