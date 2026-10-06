# Bastion Zero — Vision & Validation Specification

> Rooted in the foundational survival architecture ideation ([`bastion_zero.md`](https://github.com/FranekJemiolo/bastion-zero)).

---

## 1. High-Level Vision Statement

A smartphone becomes an **autonomous, zero-infrastructure survival multi-tool and edge-compute node** — operational when cell towers, internet backbones, and municipal power grids have collapsed.

Modern smartphones are packed with independent scientific instruments: dual-band GNSS receivers, barometers, magnetometers, accelerometers, gyroscopes, multi-microphone arrays, BLE, and UWB radios. Consumer operating systems and apps squander this hardware by treating devices as passive cloud terminals. **Bastion Zero reclaims the smartphone as a localized, self-contained instrument cluster and ad-hoc communication relay.**

---

## 2. Core Architectural Principles

1. **Zero Infrastructure:** No cloud dependencies, no remote APIs, no Firebase, no Retrofit, no analytics, no external authentication. The Android manifest requests strictly no `android.permission.INTERNET`.
2. **Battery is Scarcest Resource:** OLED pixel shutdown via pure `#000000` dark mode and `#FF3B30` red monochrome styling (preserving human rhodopsin night vision). Hardware interrupts (`TYPE_SIGNIFICANT_MOTION`) prioritized over continuous CPU polling.
3. **Physics Over Polish:** Repurposing MEMS sensors into reliable emergency instruments: Butterworth structural tilt monitoring, step-and-heading inertial dead reckoning, acoustic threat spectrogram analysis, and microbolometer emissivity compensation.
4. **Graceful Platform Degradation:** Kotlin Multiplatform shared data core; each OS exposes what its hardware APIs permit. iOS participates as an active foreground node while Android handles background mesh services and USB-OTG serial peripherals.
5. **Zero Mesh Trust & Replay Defense:** Every packet cryptographically signed with Ed25519; logical ordering maintained via Lamport clocks; state merged deterministically using LWW-Element-Set CRDTs without wall clocks.
6. **Hub Expandability:** The smartphone serves as the processing brain and tactical display for external physical peripherals: LoRa transceivers, LWIR thermal cameras, software-defined radios (SDR), and USB-C Geiger dosimeters.

---

## 3. Comprehensive Validation Matrix: Initial Vision vs. Implemented Repository

The following matrix validates every requirement and concept outlined in the initial ideation document (`bastion_zero.md`) against the current codebase in `FranekJemiolo/bastion-zero`:

| Subsystem / Feature | Initial Vision Requirement (`bastion_zero.md`) | Current Repository Implementation | Validation Status |
| :--- | :--- | :--- | :--- |
| **Monorepo Architecture** | Kotlin Multiplatform (KMP) shared core, native Android and iOS runners, Python data pipeline, automated CI/CD. | Monorepo layout: `shared`, `androidApp`, `iosApp`, `data_pipeline`, `docs`, `.github/workflows`. | **Validated & Green** |
| **Zero-Cloud Enforcement** | Strict prohibition of cloud SDKs (Retrofit, Firebase, AWS, remote telemetry). | Pre-commit hook `.githooks/pre-commit` and CI audit scripts verify zero forbidden dependencies. No `INTERNET` permission in Android manifest. | **Validated & Green** |
| **PowerOS OLED Launcher** | Pure `#000000` true black background, `#FF3B30` red typography, dynamic throttling based on battery tier and motion. | `BastionTheme.kt`, `BastionColors.kt`, `PowerGovernor.kt`, `PowerPolicy.kt` dynamically adjusting GPS/sensor intervals and UI refresh rates (60Hz down to 15Hz). | **Validated & Green** |
| **MeshLink Protocol** | BLE flood-routing mesh, ultra-compact Protobuf payload (≤200 bytes), TTL hop counts, deduplication cache. | `survival_packet.proto` (Wire 5.1.0), `BluetoothMesh.kt` (`expect/actual`), `MeshRouter.kt` with signature deduplication cache. | **Validated & Green** |
| **Mesh Security & Ordering** | Lamport logical clocks for NTP-less time drift; Ed25519 cryptographic packet signatures; replay attack defense. | `LamportClock.kt`, `Ed25519.kt` (libsodium bindings), `PacketValidator.kt` with sliding window and 500-entry signature cache. Tested in `PacketValidatorTest.kt`. | **Validated & Green** |
| **Conflict-Free Map Sync** | CRDT conflict resolution for offline map pins (e.g. water source status updated by different survivors). | `LwwElementSet.kt` and `MapPinStore.kt` with Last-Write-Wins element sets and grow-only confirmation counters. | **Validated & Green** |
| **Offline Data & Medical Wiki** | Local vector maps and FTS-searchable medical/survival guides without internet access. | `Wiki.sq` (SQLDelight FTS4 virtual tables), `WikiRepository.kt`, `WikiSeed.kt`, and `data_pipeline/wiki_ingestor.py` ETL pipeline. | **Validated & Green** |
| **Inertial Dead Reckoning (PDR)** | Pedestrian Dead Reckoning when GNSS is jammed or unavailable in caves/rubble; avoid double-integration IMU drift. | `InertialDeadReckoning.kt`: Z-axis peak detection step counter, dynamic stride estimation, Kalman heading fusion, and breadcrumb tracking. Tested in `InertialDeadReckoningTest.kt`. | **Validated & Green** |
| **Structural Tilt Monitor** | Detect building foundation collapse post-earthquake/bombing; filter micro-vibrations and thermal drift. | `StructuralTiltMonitor.kt`: 10-second baseline gravity zeroing, 2nd-order Butterworth low-pass filter, thermistor polynomial drift subtraction, sustained >0.5° delta alarm. | **Validated & Green** |
| **Dead Man's Switch (5% Gate)** | Autonomous emergency beacon broadcast before shutdown without voltage-sag crash at 2% battery. | `DeadMansSwitch.kt`: Pre-allocated 50KB RAM buffer, ≤15KB grayscale JPEG payload, cached coordinates/vitals, continuous BLE advertisement lock, CPU halt trigger. | **Validated & Green** |
| **Acoustic Edge Overwatch** | Microphone threat detection for drones and SAR beacons without draining battery. | `AcousticEdgeAI.kt`: Hardware-level decibel gating (>70 dB SPL), 2-second capture buffer, pure Kotlin radix-2 Cooley-Tukey FFT spectrogram generator, harmonic threat classification. | **Validated & Green** |
| **GNSS Anti-Spoofing Defense** | Mathematical detection of malicious GPS spoofing in conflict zones. | `GnssSpoofingDetector.kt`: Automatic Gain Control (AGC) power jump detection, Pseudo Range Rate (PRR) velocity mismatch, clock drift jump detection. | **Validated & Green** |
| **UWB Spatial Ranging** | Centimeter-level 3D spatial vectoring for avalanche/rubble victim location. | `UwbSpatialRanging.kt`: Time-of-Flight (ToF) distance calculation and Angle-of-Arrival (AoA) azimuth/elevation vectoring. | **Validated & Green** |
| **Kinematic Trauma Logger** | Black-box recorder for high-G impacts and vertical fall height; instant lock-screen triage string. | `KinematicTraumaLogger.kt`: >10G impact threshold, free-fall duration drop height ($h = \frac{1}{2}gt^2$), circular memory buffer, standardized lockscreen alert. | **Validated & Complete** |
| **LWIR Thermal Emissivity Engine** | Microbolometer radiation temperature correction, Mylar camo warning, shiny metal scald hazard prevention. | `ThermalImagingEngine.kt`: Stefan-Boltzmann emissivity compensation ($T = \sqrt[4]{T_{rad}^4/\varepsilon}$), material table, scald alert, field soot/tape target-patch protocol. | **Validated & Complete** |
| **Tactical Hub Peripheral Bridge** | LoRa transceiver integration and USB-C Geiger counter dosimeter hazard tracking. | `TacticalHubBridge.kt`: LoRa PHY framing (`0xBA70` sync + CRC16), radiation dose integration, and safe stay-time countdown ($t = (D_{max} - D_{accum}) / \dot{D}$). | **Validated & Complete** |
| **Air-Gapped Ultrasonic Modem** | Acoustic physical layer communication when all RF is jammed or underground. | `UltrasonicModem.kt`: 18–22 kHz Audio Frequency Shift Keying (AFSK) modulation and Goertzel tone energy detection. | **Validated & Complete** |
| **Unified Multi-Transport Arbitrator** | Transparent failover across BLE, LoRa, Ultrasound, and NTN Satellite with SOS preemption. | `UnifiedMeshRouter.kt`: Tiered physical transport arbitration and SOS broadcast. | **Validated & Complete** |
| **Solar-Coupled Power Governor** | Dynamic sensor, camera FPS, and OLED throttling tied to solar panel harvest wattage. | `AutonomousPowerGovernor.kt`: Adaptive duty-cycling based on battery, watts, and temperature. | **Validated & Complete** |
| **Hardware Key Sealing & Anti-Tamper** | Hardware enclave keystore protection and zeroize memory wiping on capture. | `SecureEnclaveKeyManager.kt`: Key generation, X25519 ECDH shared secret, zero-fill wipe. | **Validated & Complete** |
| **Airgap Animated QR Stream Sync** | RF-silent optical synchronization of CRDT maps under strict EW jamming. | `AirgapBundleSync.kt`: Chunked animated QR encoder/decoder with out-of-order reassembly. | **Validated & Complete** |
| **Zero-Light Spatial Wireframe Navigation** | Night navigation in total darkness without visible light using infrared LiDAR/ToF point clouds. | `ZeroLightSpatialMapper.kt`: Obstacle 2.5D bounding boxes, pitfall drop-off detection, walkable corridor vectors. | **Validated & Complete** |
| **Optical Water Turbidity Analyzer** | Field water potability and purification diagnosis via screen lux and optical sensor attenuation. | `WaterTurbidityAnalyzer.kt`: NTU calculation and actionable filtration/boiling/UV protocols. | **Validated & Complete** |
| **Multi-Mic Acoustic Triangulation** | Sound source origin vectoring (gunshots, rotor harmonics, cries) using TDoA cross-correlation. | `AcousticTriangulationEngine.kt`: Microsecond TDoA delay, temperature-compensated sound speed, azimuth & elevation. | **Validated & Complete** |
| **Celestial & Solar Ephemeris Navigation** | Infallible astronomical heading reference when GNSS is jammed/spoofed and magnetometers distort. | `CelestialCompassEngine.kt`: Solar azimuth/elevation, Polaris ephemeris, shadow-stick heading solver. | **Validated & Complete** |
| **Airborne SAR Ghost Transponder** | Controlled low-duty-cycle cellular RF bursts creating electromagnetic breadcrumbs for SAR aircraft. | `SarGhostTransponder.kt`: Duty-cycle governor, safety interlocks (battery >5%, temp <45°C), +23 dBm bursts. | **Validated & Complete** |
| **Distributed Perimeter Defense Coordinator** | Synchronized multi-node tripwire fence correlating acoustic, seismic, and radiological breaches. | `PerimeterDefenseCoordinator.kt`: Correlated threat escalation (GREEN -> YELLOW -> RED), mesh alert dispatch. | **Validated & Complete** |

---

## 4. Extended Vision: Next Horizons & Emerging Technologies

Based on the strategic next steps outlined in the ideation document, Bastion Zero extends beyond consumer smartphone APIs into five bleeding-edge domains:

### Horizon 1: Bluetooth 6.0 Channel Sounding (Democratized Micro-Location)
- **The Challenge:** Ultra-Wideband (UWB) chips are currently restricted to expensive flagship devices (iPhone Pro, Pixel Pro, Galaxy Ultra).
- **The Extension:** Bluetooth 6.0 introduces Phase-Based Ranging (PBR) and Round-Trip Time (RTT) across 79 Bluetooth channels.
- **Bastion Zero Architecture:** Implements a normalized Channel Sounding driver in `BluetoothMesh`. Budget $150–$250 Android devices can achieve centimeter-level spatial distance accuracy without requiring specialized UWB hardware, democratizing rubble/avalanche victim rescue for all survivors.

### Horizon 2: Android 15 NTN (Non-Terrestrial Network) Direct-to-Cell Routing
- **The Challenge:** While peer-to-peer mesh hops work over short distances (100–300 ft BLE, 5 miles LoRa), routing an emergency distress signal outside a regional disaster zone previously required external satellite communicators.
- **The Extension:** Modern smartphones are shipping with Direct-to-Cell 3GPP Rel-17 NTN modems. Android 15 introduces the official `SatelliteManager` API.
- **Bastion Zero Architecture:** Any device that acquires an opportunistic satellite lock acts as an automated gateway for the entire local BLE mesh. When a user with satellite capability passes within range of stranded survivors, the app queues their pending `SurvivalPacket` SOS payloads and uploads them via direct-to-cell satellite bursts.

### Horizon 3: Ultrasonic Acoustic Physical Layer (`UltrasonicModem`)
- **The Challenge:** In subterranean caverns, flooded tunnels, or military electronic warfare environments where high-power RF jammers saturate the electromagnetic spectrum, radio waves cannot propagate.
- **The Extension:** Air-gapped physical data transfer over high-frequency acoustic waves (18 kHz – 22 kHz, above human audibility) utilizing standard smartphone loudspeakers and microphones.
- **Bastion Zero Architecture:** The `UltrasonicModem` implements Audio Frequency Shift Keying (AFSK) with 50–100 baud symbol rates. Even under total RF blackout, compressed 150-byte Bastion Zero SOS packets can be broadcast across rooms or cave chambers using acoustic sound pulses.

### Horizon 4: Edge-LLM Medical RAG (Panicked Conversational Triage)
- **The Challenge:** While the SQLDelight FTS4 medical wiki provides instant lookup, a panicked user dealing with arterial bleeding or severe hypothermia cannot easily formulate exact keyword queries.
- **The Extension:** On-device Small Language Models (SLMs) such as Google Gemma 2B Q4 or Llama 3 8B quantized via ExecuTorch/MediaPipe running entirely on the device's Neural Processing Unit (NPU).
- **Bastion Zero Architecture:** Survivors speak naturally into the phone: *"My partner fell into freezing water and is shivering uncontrollably and slurring words, what do I do?"* The local model queries offline embedded survival knowledge vectors and outputs a synthesized, step-by-step triage guide in real time.

### Horizon 5: Dynamic Geospatial Feature Stripping Pipeline
- **The Challenge:** Global or continental vector map extracts (`.mbtiles`) are typically 30GB–50GB, which exceeds the storage capacity of budget survival devices.
- **The Extension:** Expand `/data_pipeline` with an automated Python microservice utilizing `geopandas` and `osmium`.
- **Bastion Zero Architecture:** When planning a route or expedition, the user specifies a geographic bounding polygon. The pipeline strips non-essential layers (commercial POIs, shopping centers, high-resolution decorative zoom levels) while retaining critical survival geometry (10m topographic contour lines, freshwater streams, mountain passes, medical centers). A 2GB regional extract is compressed to < 40MB for 100% offline edge caching.

---

## 5. Technical Blindspots & Physical Reality Constraints

The original ideation noted several physical edge-cases that standard consumer apps ignore. Bastion Zero mitigates them with rigorous engineering:

1. **Android Doze Mode & iOS Background Execution:**
   - *Android:* Addressed via sticky `ForegroundService`, persistent low-priority notification, acquired `WakeLock`, and hardware `TYPE_SIGNIFICANT_MOTION` wake-up interrupts.
   - *iOS:* Addressed via graceful degradation; foreground active mesh, background passive scanning via service UUID overflow areas.
2. **Inertial Dead Reckoning IMU Drift:**
   - Double-integration of noisy accelerometer data ($\iint a\,dt^2$) diverges exponentially within minutes.
   - Mitigated via Pedestrian Dead Reckoning (PDR): Z-axis peak detection step-counting combined with an Extended Kalman Filter (EKF) heading fusion.
3. **Clock Desynchronization & Replay Attacks:**
   - In extended grid-down disasters, devices lose NTP time servers.
   - Mitigated via Lamport Logical Clocks (ordering based on state transitions rather than wall clocks) and Ed25519 signature verification against a rolling 500-packet deduplication cache.
4. **Thermography Emissivity Hazards:**
   - Thermal cameras measure infrared radiation, not direct temperature. Shiny metals ($\varepsilon \approx 0.10$) act as infrared mirrors, appearing falsely cold despite dangerous scald temperatures. Mylar space blankets ($\varepsilon \approx 0.05$) reflect cold ambient snow, rendering survivors invisible to aerial thermal search drones.
   - Mitigated via `ThermalImagingEngine.kt` emissivity lookup tables and field target-patch guidance.
5. **Battery Depletion Voltage Sag:**
   - Triggering emergency camera and flash writes at 2% battery causes terminal voltage collapse.
   - Mitigated by firing the Dead Man's Switch at a safe 5% battery threshold with pre-allocated 50KB RAM.
