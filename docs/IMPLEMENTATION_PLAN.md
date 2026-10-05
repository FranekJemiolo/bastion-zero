# Implementation plan

Stack: **Kotlin Multiplatform** shared core, **Compose Multiplatform** UI, native `expect/actual` hardware drivers. Repo: `FranekJemiolo/bastion-zero`. Zero cloud dependencies throughout.

Phases are gated: the next starts only after the previous is confirmed compiling and working.

## Status

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | KMP scaffold & shared data core | **Complete** |
| 2 | PowerOS & cross-platform UI | **Complete** |
| 3 | MeshLink BLE (expect/actual) | **Complete** |
| 4 | Sensor HAL & edge intelligence | **Complete** |

## Phase 1 — KMP scaffolding & shared data core

- Modules `shared`, `androidApp`, `iosApp` (XcodeGen spec); `/data_pipeline`, `/docs`; README; CI for Android APK, iOS (+TestFlight hook), MkDocs Pages.
- Protobuf `SurvivalPacket` (Wire), **Lamport clock**, **Ed25519 signing**, replay defence (signature cache of 500 + per-sender sliding window).
- **LWW-Element-Set** CRDT + `MapPinStore` (pins, LWW content, grow-only confirmations).
- **SQLDelight** offline wiki (FTS4) + `wiki_ingestor.py`.
- *Example:* two phones edit the same pharmacy pin offline; after a BLE sync both converge on the later edit, deterministically.

## Phase 2 — PowerOS & cross-platform UI

- Shared `MaterialTheme`: `#000000` background, `#FF3B30` text.
- Bottom nav: **Mesh Map**, **Sensor Hub**, **Offline Wiki** (wiki wired to Phase 1 repository).
- `PowerGovernor` (shared policy: battery/motion → polling intervals, refresh rate). Android: sticky `ForegroundService`, `WakeLock`s, `TYPE_SIGNIFICANT_MOTION` trigger sensor. iOS: `BGTaskScheduler` where permitted.
- Android haptic chords via `VibrationEffect.Waveform` (rattlesnake = hazard, purr = all-clear, double thud = medical SOS).
- *Example:* at 10% battery the governor stretches GPS polling to minutes and drops UI refresh.

## Phase 3 — MeshLink (expect/actual)

- `expect` `BluetoothMesh` in `shared`; flood routing with TTL, using Phase 1 validator and CRDT.
- Android: `BluetoothLeAdvertiser` / `BluetoothLeScanner` under a foreground service.
- iOS: CoreBluetooth; foreground active, background passive (service-UUID scan, overflow area).
- *Example:* an SOS hops through two strangers' phones; each drops duplicates before waking the CPU.

## Phase 4 — Sensor HAL & edge intelligence

- `expect class SensorProvider` (internal sensors; Android USB-OTG later).
- **Dead reckoning:** Z-axis peak step detection, stride from height, complementary/Kalman heading fusion (not raw double integration).
- **Structural tilt monitor:** baseline gravity calibration, Butterworth low-pass, thermistor drift compensation, sustained >0.5° delta trigger.
- **Dead Man's Switch:** 50 KB pre-allocated buffer, ≤15 KB grayscale JPEG, cached GPS/vitals, BLE beacon lock at 5% battery.
- **Acoustic edge-AI:** level-gated (>70 dB) 2 s capture → FFT spectrogram → TinyML/CoreML interface (model later).

## Staged product rollout (later)

1. **"Burner" MVP** (<$300 Android): PowerOS, BLE mesh, offline maps + wiki, basic dead reckoning.
2. **Flagship fusion:** LiDAR dark nav, on-device CV, multi-mic vectoring, trauma logs, UWB.
3. **Tactical Hub:** USB-OTG HAL, LoRa bridge, thermal/SDR/Geiger/solar integrations.

## Missed-by-original-design (now first-class)

Android Doze, IMU double-integration drift, NTP-less clock drift + replay attacks, acoustic false positives, offline map storage budgeting (bounding-box + LOD stripping), CRDT conflicts, voltage-sag at ~2% battery.
