# My Browser

A distraction-optimized web browser for **Android Auto** and phone, with a premium infotainment-style home screen.

Built on the [AABrowser](https://github.com/kododake/AABrowser) pattern: a single full-screen `MainActivity` launched by Android Auto instead of Car App Library templates.

## Features

- **Android Auto + phone** — same app on the car display and on your phone
- **Infotainment home screen** — clock, date, location, weather, Malayalam news headlines, and weather alerts
- **Web browsing** — open YouTube, Google, or any link with an unrestricted `WebView`
- **Auto-hiding controls** — browser chrome stays out of the way while you browse
- **Resume session** — pick up where you left off from the home screen

## Requirements

- Android 10+ (API 29) on phone
- Android Auto head unit for in-car use (Android 15+ recommended for full car Activity projection)
- Android Studio or JDK 17 for building

## Build

1. Clone the repository.
2. Create `local.properties` in the project root (not committed):

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. Build from the project root:

   ```bash
   ./gradlew assembleDebug
   ```

   On Windows:

   ```bat
   gradlew.bat assembleDebug
   ```

4. Install the APK from `app/build/outputs/apk/debug/`.

## Android Auto

1. Install the app on your phone.
2. Connect to Android Auto (USB or wireless).
3. Open **My Browser** from the Android Auto launcher.

> **Safety:** Use only when parked or as a passenger. Browsing while driving is unsafe and may be restricted by your head unit or local laws.

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

## Acknowledgments

- Architecture inspired by [AABrowser](https://github.com/kododake/AABrowser) by kododake
