# Implementation Plan & Staged Roadmap

Stack: **Kotlin Multiplatform** shared core, **Compose Multiplatform** UI, native `expect/actual` hardware drivers.  
Repo: `FranekJemiolo/bastion-zero`. Master constraint: **Zero cloud dependencies throughout.**

---

## 1. Roadmap & Status Overview

| Phase | Architecture Scope | Status | Deliverables & Test Evidence |
| :--- | :--- | :--- | :--- |
| **Phase 1** | KMP Scaffolding & Shared Data Core | **Complete** | Protobuf `SurvivalPacket`, Lamport logical clock, Ed25519 signing, LWW-Element-Set CRDT, SQLDelight offline wiki. |
| **Phase 2** | PowerOS & Tactical UI | **Complete** | Pitch-black `#000000` / `#FF3B30` Compose theme, `PowerGovernor` policy engine, Doze mode workarounds, PWM haptic chords. |
| **Phase 3** | MeshLink Ad-Hoc BLE Mesh | **Complete** | Flood-routing protocol, TTL hop countdown, duplicate signature cache, `expect/actual BluetoothMesh`. |
| **Phase 4** | Sensor HAL & Edge Intelligence | **Complete** | PDR dead reckoning, Butterworth structural tilt monitor, 5% battery Dead Man's Switch, FFT acoustic threat detection, GNSS anti-spoofing, UWB ranging, trauma black-box, optical PPG, LoRa PHY, ultrasonic modem. |
| **Phase 5** | Autonomous Resilience & Field Hardening | **Complete** | `UnifiedMeshRouter` multi-transport arbitrator, `AutonomousPowerGovernor` solar coupling, `SecureEnclaveKeyManager`, `AirgapBundleSync` animated QR stream, `TacticalFieldHud` glove mode. |
| **Phase 6** | Tactical Edge Sensor Weaponization | **Complete** | `ZeroLightSpatialMapper` wireframe navigation, `WaterTurbidityAnalyzer` optical NTU potability, `AcousticTriangulationEngine` multi-mic TDoA, `CelestialCompassEngine` unjammable nav, `SarGhostTransponder`, `PerimeterDefenseCoordinator`. |
| **Phase 7** | Field Operational Readiness & Tactical Hardware | **Complete** | `UsbSerialHostDriver` (CDC-ACM/FTDI/CP210x/CH34x), `GnssRawMeasurementIngestor`, `OfflineVectorMapEngine`, `MapSnappingEngine`, `AcousticNeuralClassifier`, `EdgeRagSemanticRouter`, `HardwarePanicTrigger`, `MeshtasticProtocolBridge`, `SlottedRebroadcastSuppression`, `AirgapApkBeacon`. |

---

## 2. Completed Baseline (Phases 1–4)

### Phase 1 — KMP Scaffolding & Shared Data Core
- Multiplatform modules: `shared` (commonMain, androidMain, iosMain), `androidApp`, `iosApp` (XcodeGen spec).
- Compact Protobuf serialization using Wire 5.1.0 (`survival_packet.proto`).
- Lamport logical clock incremented on transmit/forwarding to guarantee deterministic packet ordering without NTP servers.
- Ed25519 asymmetric signing via libsodium bindings; rolling cache of 500 signatures to block network replay loops.
- LWW-Element-Set CRDT (`MapPinStore.kt`) resolving concurrent offline pin edits across mesh encounters.
- SQLDelight 2.0.2 full-text search database (`Wiki.sq`) seeded from `data_pipeline/wiki_ingestor.py`.

### Phase 2 — PowerOS & Tactical UI
- Global Jetpack Compose `BastionTheme` forcing pure `#000000` black (turning off OLED emissive elements) and `#FF3B30` red typography (preserving rhodopsin night vision).
- Three-tab bottom navigation: **Mesh Map**, **Sensor Hub**, and **Offline Wiki**.
- `PowerGovernor` dynamically switching between `FULL_POWER`, `CONSERVATIVE`, `SURVIVAL`, and `EMERGENCY` tiers based on battery percentage and accelerometer motion states.
- Android foreground service with `WakeLock` and hardware `Sensor.TYPE_SIGNIFICANT_MOTION` wake-up interrupts.
- Stealth haptic chord player using `VibrationEffect.Waveform` (rattlesnake hazard, purr all-clear, double thud medical SOS).

### Phase 3 — MeshLink Ad-Hoc BLE Mesh
- Expect/actual `BluetoothMesh` abstraction for cross-platform peer-to-peer discovery and packet exchange.
- Android implementation using `BluetoothLeAdvertiser` and `BluetoothLeScanner` under a sticky foreground service.
- iOS implementation using `CoreBluetooth` adhering to Apple's background BLE scanning restrictions (active foreground, passive background via service UUID).
- Mesh router with hop-count TTL reduction and automatic deduplication to prevent broadcast storms.

### Phase 4 — Sensor HAL & Edge Intelligence
- **Pedestrian Inertial Dead Reckoning (`InertialDeadReckoning.kt`):** Z-axis peak detection step-counting, dynamic stride length estimation, complementary/Kalman heading fusion, and map breadcrumbs.
- **Structural Tilt Monitor (`StructuralTiltMonitor.kt`):** 10-second baseline gravity calibration, 2nd-order Butterworth low-pass filter, thermistor polynomial drift subtraction, and sustained >0.5° delta collapse siren.
- **Dead Man's Switch (`DeadMansSwitch.kt`):** Pre-allocated 50KB RAM buffer, ≤15KB grayscale JPEG snapshot, cached GPS/vitals, and continuous BLE advertisement lock triggered at 5% battery to prevent terminal voltage-sag crashes.
- **Acoustic Edge-AI (`AcousticEdgeAI.kt`):** Low-power decibel level gating (>70 dB SPL), 2-second capture buffer, pure Kotlin radix-2 Cooley-Tukey FFT spectrogram generator, and drone/helicopter acoustic classifier.
- **GNSS Spoofing Defense (`GnssSpoofingDetector.kt`):** Evaluates Automatic Gain Control (AGC) power surges and Pseudo Range Rate (PRR) velocity mismatches to catch military-grade GPS spoofers.
- **UWB Spatial Ranging (`UwbSpatialRanging.kt`):** Centimeter-level 3D vectoring for locating buried survivors under avalanche snow or rubble.

---

## 3. Extended Phase 5 — Advanced Trauma & Optical Triage

### 5.1 Kinematic Trauma Black-Box Logger (`KinematicTraumaLogger.kt`)
- **Objective:** Functions as an autonomous black-box for the human body during falls down ravines, avalanches, or structural impacts.
- **Detection Logic:**
  - High-G impact trigger: Total acceleration vector magnitude $|a| = \sqrt{a_x^2 + a_y^2 + a_z^2} \ge 10.0G$.
  - Free-fall state: Detects near-zero G state ($|a| < 0.25G$) preceding impact.
  - Drop height physics: Computes vertical fall distance $h = \frac{1}{2} g (\Delta t_{fall})^2$.
  - Rolling RAM buffer: Maintains last 100 motion telemetry frames without disk I/O.
  - Standardized Lock-Screen Triage Alert: Generates concise string for first responders:
    `"CRITICAL TRAUMA ALERT: Patient sustained 14.5G impact and 4.9m (16.1ft) vertical drop. BlackBox code: BB-SEVERE_TRAUMA."`
- **Verification:** Tested in `KinematicTraumaLoggerTest.kt`.

### 5.2 Optical Vitals & Photoplethysmography (PPG)
- **Objective:** Estimate pulse rate and blood oxygen saturation ($SpO_2$) using phone camera and LED flash.
- **Algorithm:** Measures capillary light absorption modulation during pulse cycles ($AC/DC$ ratio of red and infrared/green pixel channels).
- **Interface:** `OpticalVitalsMonitor.kt` emitting heart rate (BPM) and estimated $SpO_2$ percentage into the `SurvivalPacket` medical payload.

### 5.3 AR Burn & Wound Photogrammetry
- **Objective:** Millimeter-accurate wound surface area measurement using ARKit/ARCore depth parallax.
- **Logic:** Color-space segmentation of wounded tissue against healthy skin, estimating Total Body Surface Area (TBSA) percentage for the Parkland fluid resuscitation formula.

---

## 4. Extended Phase 6 — Tactical Hub Hardware Expansion

### 6.1 Tactical Hub Peripheral Bridge (`TacticalHubBridge.kt`)
- **Objective:** Interface external transceivers and sensors via USB-OTG serial (Android) and Bluetooth serial nodes.
- **LoRa Transceiver Integration:**
  - Encapsulates binary `SurvivalPacket` into robust LoRa PHY frames with sync word `0xBA70` and CRC16.
  - Expands communication radius from 300 feet (BLE) to 3–5 miles per hop over 868/915 MHz ISM radio bands.
- **CBRN Dosimeter & Geiger Counter Integration:**
  - Decodes real-time radiation dose rate ($\mu\text{Sv/h}$).
  - Accumulates total absorbed dose and calculates safe stay-time remaining:
    $$t_{stay} = \frac{D_{max} - D_{accum}}{\dot{D}}$$
  - Automatically raises `ACUTE_EXCLUSION_ZONE` alert when dose rate exceeds $100\,\mu\text{Sv/h}$.
- **Verification:** Tested in `TacticalHubBridgeTest.kt`.

### 6.2 LWIR Thermal Imaging & Emissivity Engine (`ThermalImagingEngine.kt`)
- **Objective:** Physics-based Long-Wave Infrared thermography processing for external microbolometer dongles (FLIR, InfiRay, Topdon).
- **Emissivity Compensation:**
  - Corrects apparent radiant temperature using Stefan-Boltzmann radiation law:
    $$T_{true} = \left(\frac{T_{rad}^4}{\varepsilon}\right)^{1/4}$$
  - Standard material emissivity table: Human skin ($\varepsilon = 0.98$), Water ($\varepsilon = 0.96$), Organic soil/wood ($\varepsilon = 0.90$), Concrete ($\varepsilon = 0.92$), Polished metal ($\varepsilon = 0.10$), Mylar ($\varepsilon = 0.05$).
- **Safety Mitigations:**
  - **Scald Burn Hazard Warning:** Alerts when shiny metal ($\varepsilon < 0.20$) appears falsely cool due to reflecting ambient background.
  - **Mylar Camouflage Warning:** Warns aerial SAR operators that Mylar space blankets reflect freezing snow, concealing human body heat.
  - **Field Target-Patch Protocol:** Recommends applying electrical tape or campfire carbon soot ($\varepsilon = 0.95$) to shiny surfaces to force an accurate reading.
- **Verification:** Tested in `ThermalImagingEngineTest.kt`.

---

## 5. Phase 7 — Field Operational Readiness & Tactical Hardware Integration

*(Detailed plan and architectural specifications: [`PHASE_7_IMPLEMENTATION_PLAN.md`](PHASE_7_IMPLEMENTATION_PLAN.md))*

- **Physical USB-C OTG Host Serial Driver (`UsbSerialHostDriver.kt`):** Android `UsbManager` bulk endpoints (CDC-ACM, FTDI, CP210x, CH34x) enabling plug-and-play with physical LoRa, SDR, and Geiger peripherals.
- **Raw GNSS Measurement Ingestion (`GnssRawMeasurementIngestor.kt`):** Hooks into `GnssMeasurementsEvent` streaming real-time AGC (dB) and clock drift into `GnssSpoofingDetector` for mathematical Electronic Warfare defense.
- **Offline Vector Terrain & Map Snapping (`OfflineVectorMapEngine.kt`, `MapSnappingEngine.kt`):** Zero-cloud offline vector contour elevations, ridge trails, water springs, shelters, and orthogonal point-to-polyline snapping bounding IMU dead reckoning drift.
- **Neural Acoustic Classifier & Semantic Triage (`AcousticNeuralClassifier.kt`, `EdgeRagSemanticRouter.kt`):** 2-layer quantized mel-spectrogram network classifying gunshots, drone rotors, and cries; paired with natural-language panic triage outputting prioritized TCCC procedures.
- **Hardware Panic Trigger (`HardwarePanicTrigger.kt`):** 5 rapid physical key clicks within 2.5s for silent, blind SOS triggering without waking screen.
- **Meshtastic Protocol Interop & Slotted Suppression (`MeshtasticProtocolBridge.kt`, `SlottedRebroadcastSuppression.kt`):** Bidirectional transcoding between Bastion Zero and civilian Meshtastic networks, coupled with slotted jitter delay and duplicate overhearing cancellation.
- **Air-Gapped APK Distribution (`AirgapApkBeacon.kt`):** Zero-infrastructure Wi-Fi Direct and local hotspot beacon with dense QR pairing metadata for field APK installation in dead zones.

---

## 6. Verification & Quality Gates

Each milestone requires:
1. Multiplatform compilation passing on both Android (JVM/Dalvik) and iOS (Native/ARM64).
2. Complete test coverage in `shared/src/commonTest/`.
3. Zero-cloud dependency verification via `.githooks/pre-commit`.
4. All GitHub Actions workflows (`Android build`, `iOS build`, `Docs (GitHub Pages)`) running green.
