# Bastion Zero

[![Android build](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/android-build.yml/badge.svg)](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/android-build.yml)
[![iOS build](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/ios-build.yml/badge.svg)](https://github.com/FranekJemiolo/bastion-zero/actions/workflows/ios-build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-red.svg)](LICENSE)

**Offline-first survival platform for Android and iOS. No cloud. No towers. No grid.**

📖 Docs: <https://franekjemiolo.github.io/bastion-zero/>

Kotlin Multiplatform shared core · Compose Multiplatform UI · native hardware drivers.

## Features & roadmap

| Stage | Target | Highlights | Status |
| --- | --- | --- | --- |
| 1 · "Burner" MVP | Cheap phones (<$300) | OLED black/red PowerOS, BLE mesh SOS & pins, offline maps + medical wiki, dead reckoning | **Complete** |
| 2 · Flagship fusion | Modern flagships | Kinematic trauma black-box, optical PPG, GNSS spoofing defense, UWB spatial ranging | **Complete** |
| 3 · Tactical Hub | + external gear | LoRa multi-mile bridge, LWIR thermal emissivity engine, Geiger counter stay-time tracker | **Complete** |
| 4 · Bleeding-Edge Networks | Resilient comms | Ultrasonic AFSK modem (air-gapped), Bluetooth 6.0 channel sounding, NTN satellite mesh bridge, Edge-LLM RAG | **In Progress** |

Detailed architecture and validation matrix: [`docs/VISION.md`](docs/VISION.md).  
Step-by-step phases & technical specs: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).  
Development journal & decisions: [`docs/JOURNAL.md`](docs/JOURNAL.md).

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
