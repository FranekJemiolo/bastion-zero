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
| 2 · Flagship fusion | Modern flagships | Kinematic trauma black-box, optical PPG, wound photogrammetry, GNSS anti-spoofing, UWB ranging | **Complete** |
| 3 · Tactical Hub | + external gear | LoRa multi-mile bridge, LWIR thermal emissivity engine, USB-OTG serial HAL, SDR triangulation, solar AR | **Complete** |
| 4 · Bleeding-Edge Networks | Resilient comms | Ultrasonic AFSK modem (air-gapped), Bluetooth 6.0 channel sounding, Android 15 NTN satellite bridge, on-device Edge-LLM RAG | **Complete** |
| 5 · Autonomous Resilience | Field Hardening | Unified multi-transport router, solar-coupled power governor, enclave key sealing, optical QR airgap sync, tactile glove HUD | **Complete** |

Engineering assessment & readiness review: [`docs/ASSESSMENT.md`](docs/ASSESSMENT.md).  
Phase 5 implementation & hardening specs: [`docs/PHASE_5_IMPLEMENTATION_PLAN.md`](docs/PHASE_5_IMPLEMENTATION_PLAN.md).  
Detailed architecture and validation matrix: [`docs/VISION.md`](docs/VISION.md).  
Step-by-step phases & technical specs: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md).  
Development journal & decisions: [`docs/JOURNAL.md`](docs/JOURNAL.md).

## End-to-End (E2E) Disaster Scenario Testing

Bastion Zero includes two dedicated multi-system end-to-end integration test suites (`DisasterScenarioE2ETest.kt` and `Phase5ResilienceE2ETest.kt`):
- **Scenario 1 (Trauma & Multi-Hop Relay):** Victim sustained 14G impact and 5m drop -> `KinematicTraumaLogger` formats lock-screen triage -> `OpticalVitalsMonitor` records shock pulse -> signed via Ed25519 & Lamport clock into `SurvivalPacket` -> relayed across multiple BLE hops -> framed into LoRa PHY (`0xBA70` + CRC16) -> decoded by base station.
- **Scenario 2 (CBRN Contamination & CRDT Sync):** Geiger dosimeter reads 150 $\mu\text{Sv/h}$ -> triggers acute exclusion zone -> drops CRDT hazard pin in `MapPinStore` -> synced across mesh.
- **Scenario 3 (Subterranean RF Blackout):** Total radio jamming -> `UltrasonicModem` modulates SOS payload into 18–22 kHz AFSK acoustic bursts -> decoded via Goertzel filter.
- **Scenario 4 (Thermal Scald & Burn Triage):** Shiny metal door handle thermal reflection warning -> soot target patch calibration -> `WoundPhotogrammetry` burn sizing and Parkland fluid resuscitation calculation.
- **Scenario 5 (Multi-Transport Cascade Failover):** `UnifiedMeshRouter` dynamically arbitrates between BLE Proximity, LoRa Tactical, Ultrasonic AFSK, and NTN Satellite uplinks; SOS medical distress packets preempt all links concurrently.
- **Scenario 6 (Solar-Coupled Autonomous Power Endurance):** `AutonomousPowerGovernor` throttles hardware sensors (Camera 30->10 FPS, IMU 100->20 Hz, BLE scan 50%->2%, OLED 10%) on low battery/thermal alarm, and dynamically scales runtime up when folding solar panel delivers harvest power.
- **Scenario 7 (Radio-Silent Air-Gapped Optical QR Sync):** `AirgapBundleSync` exports encrypted CRDT map pins into sequential high-density animated QR frames; peer camera ingests frames out-of-order and reassembles payload with zero RF signature.

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
