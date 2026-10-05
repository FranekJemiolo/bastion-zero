# Bastion Zero

[![Android build](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/android-build.yml/badge.svg)](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/android-build.yml)
[![iOS build](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/ios-build.yml/badge.svg)](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/ios-build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-red.svg)](LICENSE)

**Offline-first survival platform for Android and iOS. No cloud. No towers. No grid.**

📖 Docs: <https://franekjemiolo.github.io/bastion-zero/>

Kotlin Multiplatform shared core · Compose Multiplatform UI · native hardware drivers.

## Features & roadmap

| Stage | Target | Highlights |
| --- | --- | --- |
| 1 · "Burner" MVP | Cheap phones | OLED black/red PowerOS, BLE mesh SOS & pins, offline maps + medical wiki, dead reckoning |
| 2 · Flagship fusion | High-end phones | LiDAR dark nav, on-device CV, trauma logs, UWB |
| 3 · Tactical Hub | + external gear | LoRa bridge, thermal, SDR, Geiger, solar AR (Android USB-OTG) |

Build phases and status: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md). Decisions: [`docs/JOURNAL.md`](docs/JOURNAL.md). Vision: [`docs/VISION.md`](docs/VISION.md).

**Current:** Phase 3 — MeshLink BLE Ad-Hoc Network (expect/actual for Android **Current:** Phase 2 — OLED theme, three-tab shell, PowerGovernor (Android foreground service / iOS background refresh), haptic chords, on top of the Phase 1 core. iOS CoreBluetooth, flood routing with TTL decrement, CRDT pin sync, emergency SOS haptics).

## Screenshots

Screenshots will be added once the UI is verified on a device.

## Layout

```
shared/        KMP: mesh, crypto, crdt, db, UI (commonMain) + android/ios actuals
androidApp/    Android host
iosApp/        iOS host (XcodeGen spec)
data_pipeline/ Python build-time ETL (offline wiki DB)
docs/          MkDocs site source
```

## Build & verify

Requires JDK 17+, Android SDK (API 35). iOS needs Xcode 15+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
gradle wrapper --gradle-version 8.10.2      # once; commit the generated wrapper
./gradlew :shared:testDebugUnitTest         # core logic + wiki DB tests (host JVM)
./gradlew :androidApp:assembleDebug         # APK
./gradlew :shared:iosSimulatorArm64Test     # same tests on iOS (macOS)
cd iosApp && xcodegen generate && open BastionZero.xcodeproj
cd data_pipeline && python3 -m unittest     # ETL test
```

## Master constraint

Zero cloud dependencies: no Retrofit, no Firebase, no remote APIs, no `INTERNET` permission.

## License

MIT
