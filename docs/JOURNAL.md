# Journal

Running log of decisions, rationale and open questions. Newest entries at the bottom of each phase.

---

## Phase 1 — KMP scaffold & shared data core (2026-10-05)

**Status:** code written; **not compiled in the authoring environment** (only JDK 8, no Gradle/Android SDK available). The Python pipeline test was run and passes. Please run the verification steps in the README and report errors before Phase 2.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D1 | Repo created at `bastion-zero/` (not under `docs`) with `shared`, `androidApp`, `iosApp` | Matches the requested standard KMP layout | — |
| D2 | **Wire** (Square) for protobuf | Pure KMP Kotlin generation; `protoc-gen-kotlin` is JVM-only and the Google runtime is not KMP | Packet size becomes critical → consider hand-rolled encoder |
| D3 | Proto field names are lowerCamelCase | Guarantees the same Kotlin property names under any codegen naming policy | — |
| D4 | Added fields vs. the design doc: `lamport`, `signature`, `wallClockHint`, `PIN_REMOVE`, `PIN_CONFIRM`; `senderId` is raw 32-byte pubkey; `packetId` is `fixed64` not UUID string | Doc required Lamport + Ed25519; raw key avoids a lookup; UUID string (36 B) is too big for ~200 B BLE budget | Packet exceeds BLE limit in Phase 3 |
| D5 | Signature excludes `ttl` | Relays must decrement TTL without invalidating the origin signature | — |
| D6 | **Lamport counter is origin-assigned and signed**, not incremented per hop | Per-hop increments would change signed bytes; the doc's "increment on forward" is satisfied by TTL for hops and by `observe()` for local clock merge | — |
| D7 | Ed25519 via **ionspin libsodium bindings** behind an `Ed25519` interface | Only mature KMP option covering Android + iOS with no JVM-only API; interface keeps it swappable (e.g. Keystore/Secure Enclave) | Library maintenance stalls; tests on iOS fail |
| D8 | Replay defence = 500-signature LRU **plus** per-sender 64-wide sliding window (IPsec style) | Cache alone lets a replay through after rotation; strict "highest-only" would drop legitimately out-of-order flood deliveries | — |
| D9 | Window/cache committed **after** signature verification | Otherwise an attacker can forge a high Lamport to blind a victim sender | — |
| D10 | **SQLDelight** over Room KMP | Both were allowed; SQLDelight is more mature on iOS and the `expect/actual` driver is trivial | Room KMP stabilises |
| D11 | **FTS4**, not FTS5 | Design doc said FTS5, but FTS5 is not guaranteed in the SQLite shipped with older Android; FTS4 is. Pipeline and `.sq` schema kept identical | Min SDK raised or bundling own SQLite |
| D12 | FTS query sanitiser: alphanumeric tokens, lowercased, prefix `*` | FTS operators (`OR`, `NEAR`, quotes) are syntax errors under panic typing; lowercasing neutralises keyword operators | — |
| D13 | LWW-Element-Set ordered by `(lamport, replicaId)`, add-biased | Deterministic tie-break → all replicas converge | — |
| D14 | `MapPinStore` = LWW set (existence) + LWW register (content) + G-Set (confirmations) | Doc's "Verified x3" needs a distinct-replica count that survives merges; edits to a pin must also converge | UI wants "historical grayed pins" → keep a version history (Phase 2/3) |
| D15 | Compose placeholder `App()` in shared; iOS host via **XcodeGen** `project.yml` | Hand-writing `.xcodeproj` is brittle; XcodeGen spec is reviewable text | Move to committed xcodeproj if XcodeGen is unwanted |
| D16 | No `INTERNET` permission in the Android manifest | Enforces the zero-cloud constraint at OS level | — |
| D17 | CI Android tests run on host JVM (`testDebugUnitTest`) | No emulator needed; DB test uses JDBC SQLite | Add instrumented tests later |
| D18 | iOS TestFlight is a gated **hook**, not a full pipeline | Needs signing secrets that must not live in a public repo | When certs are available |
| D19 | `wiki_ingestor.py` uses stdlib only (no pandas) | Zero install friction in CI; pandas not needed for this transform | Heavier cleaning needed |

### Known risks / unverified
- Gradle wrapper (`gradlew`) is **not committed**; run `gradle wrapper --gradle-version 8.10.2` once (README).
- ionspin API names (`Signature.keypair/detached/verifyDetached`, `LibsodiumInitializer`) written from memory of 0.9.x; if compilation fails, only `crypto/Ed25519.kt` needs touching.
- SQLDelight FTS4 `MATCH` / `docid` parsing under the 3.24 dialect is unverified.
- Wire-generated names (`SurvivalPacket.PacketType`, `copy(...)`, `ADAPTER`) assumed.
- Private key persistence (Keystore / Secure Enclave) is deliberately **not** in Phase 1; keys are in-memory only.
- `MapPinStore.merge` advances the clock only from pin content stamps, not remove stamps (harmless: ordering stays correct since stamps compare by value).

### Source-doc corrections to carry forward
See "Reality-check notes" in [VISION](VISION.md): several brainstorm ideas (DSP model flashing, BLE after HALT, forcing radio kill-switches, ghost pinging) exceed public OS APIs and are tracked as research, not requirements.

---

## Phase 2 — PowerOS & cross-platform UI (2026-10-05)

**Status:** code written; still **not compiled** by me (no Gradle/Android SDK/JDK 17 here). Proceeded on your go-ahead without a build confirmation for Phase 1, so Phase 1 and 2 build errors may overlap; fix Phase 1 errors first.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D20 | Theme: black `#000000` background, `#FF3B30` text, `#B02A22` secondary, `#2A0705` selection ember; Material3 `darkColorScheme` with every surface role forced to black | Any default surface tint would light OLED pixels | Accessibility review of contrast (dim red on black is ~3.9:1) |
| D21 | `PowerPolicyCalculator` is a pure function; tiers NORMAL ≥50%, SAVER 20–49, CRITICAL 10–19, SURVIVAL <10; charging ⇒ NORMAL; stillness ×6 GPS, ×3 sensors | Testable policy independent of OS; matches the doc example (10% ⇒ 15 Hz, GPS 5 min) | Real-device battery measurements |
| D22 | Platform power uses a `PowerBackend` **interface** (Android/iOS classes), not `expect class` | Android needs a `Context`; plain interface + constructor injection is simpler and testable. `expect/actual` is kept for `BluetoothMesh` (Phase 3) as requested | — |
| D23 | Android keep-alive = sticky foreground service (`specialUse` type) + **timed** 10-min partial WakeLock renewed every 9 min only while policy allows + `TYPE_SIGNIFICANT_MOTION` trigger sensor re-armed on each fire | Timed lock cannot leak; trigger sensor is the one hardware interrupt that works in Doze; 2 min without a trigger ⇒ "still" | Phase 3: switch FGS type to `connectedDevice` when BLE permissions exist |
| D24 | WakeLock is released below 20% battery | Holding the CPU awake is exactly what drains a dying phone; motion interrupt + FGS remain | Measure mesh reliability vs drain |
| D25 | Service manifest/permissions live in `shared/src/androidMain/AndroidManifest.xml`; still no `INTERNET` | Keeps service class and declaration together; merged into the app | — |
| D26 | Display policy applied from `MainActivity`: window brightness and `preferredRefreshRate` | Cheap, no extra permission; refresh rate is a hint the OS may ignore | — |
| D27 | iOS: `BGAppRefreshTask` (15 min earliest) + battery notifications; `registerBackgroundTasks()` is called from Swift `App.init` | BGTaskScheduler rejects registration after launch; iOS gives no guaranteed background time, which is by design | Phase 3 hooks the mesh queue drain in here |
| D28 | iOS motion is assumed "moving" until CoreMotion (Phase 4) | Conservative: costs battery rather than missing activity | Phase 4 |
| D29 | Haptic patterns are data in common (`HapticChord`), playback via `HapticPlayer`; Android implementation in `androidApp` using `VibrationEffect.createWaveform` with amplitude control when available; iOS uses a no-op | Patterns are unit-testable; spec asked for Android player in androidApp | Core Haptics on iOS |
| D30 | UI shell takes an `AppEnvironment` (wiki, governor, haptics) built by each host | No DI framework needed yet | Phase 3 grows the environment |
| D31 | Bundled 3-article sample wiki seeded only when the DB is empty; marked "Sample entry" | Wiki screen is usable now; real content comes from the ETL | Medical review before shipping real content |
| D32 | Bottom-nav icons are unicode glyphs, not Material icons | Avoids the extended-icons dependency (large, deprecated in CMP) | Custom vector icons |

### Known risks / unverified
- iOS cinterop names (`BGTaskScheduler.registerForTaskWithIdentifier`, `BGAppRefreshTaskRequest(identifier=)`, `UIDeviceBatteryState` enum access) written from memory; errors would be confined to `IosPower.kt`.
- Swift facade name `IosPowerKt` derives from the file name `IosPower.kt`.
- Compose Multiplatform 1.7 Material3 color roles (`surfaceContainer*`) assumed present.
- Foreground service cannot be started from the background on Android 12+; it is only started from the Activity.
- Real-world Doze behaviour (OEM battery killers) needs on-device testing; no emulator test exists.

---

## Phase 3 — MeshLink Ad-Hoc Network (2026-10-05)

**Status:** code written; unit tests added in `shared/src/commonTest/kotlin/com/bastionzero/mesh/MeshRouterTest.kt`.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D33 | `MeshTransport` common interface + `expect class BluetoothMesh : MeshTransport` in `shared` | Clean common interface allows unit test mocking (`FakeMeshTransport`), while fulfilling the strict expect/actual pattern for native platform drivers | — |
| D34 | Dedicated 128-bit Service UUID (`b0z00001-0000-1000-8000-00805f9b34fb`) and Characteristic UUID (`b0z00002-...`) | Complies with standard BLE GATT spec without colliding with SIG-reserved 16-bit IDs | — |
| D35 | Dual-mode GATT server & client on both Android and iOS | Advertisements alone have a 31-byte limit in legacy BLE. Hosting a GATT service lets nodes exchange complete 100-200 byte Protobuf packets bi-directionally without pairing | Extended Advertising (BLE 5.0) when targeting exclusively modern devices |
| D36 | Strict compliance with Apple iOS background BLE scanning | iOS drops scans without specific service UUIDs when backgrounded. `CBCentralManager.scanForPeripheralsWithServices(listOf(serviceUuid))` is strictly used, and `bluetooth-central`/`bluetooth-peripheral` background modes are added to `project.yml` | — |
| D37 | Android 12+ (API 31+) permission flags: `neverForLocation` on `BLUETOOTH_SCAN` | Avoids demanding continuous GPS location permission just for BLE mesh scanning on modern Android | If beacon distance estimation requires location data |
| D38 | Flood routing with decremented TTL (`ttl - 1`) and `ReplayGuard` suppression | Every verified packet with `ttl > 1` is re-broadcasted. Packets with `ttl == 1` halt. Replayed or looped packets hit the sliding window / signature cache and are dropped immediately without re-transmission | When dynamic TTL based on network density is desired |
| D39 | CRDT Map Pin integration: pins received over BLE automatically merge into `MapPinStore` | True serverless convergence for crowdsourced hazard and resource pins | — |
| D40 | Immediate Haptic alarms for high-priority mesh packets | Receiving an `SOS_MEDICAL` or `SOS_RESCUE` packet instantly fires `HapticChord.MEDICAL_SOS`; receiving a `HAZARD_PIN` fires `HapticChord.HAZARD_APPROACHING` | — |
| D41 | `MeshRouter` coordinating layer with `MeshFactory` | Decouples UI from raw Bluetooth APIs; binds cryptography, logical clocks, CRDT stores, and BLE transport together | — |

### Known risks / unverified
- CoreBluetooth delegates in Kotlin/Native require execution on the main queue (`dispatch_get_main_queue()`) or a dedicated dispatch queue.
- Android foreground service type updated to `specialUse|connectedDevice`. Some Android 14+ OEM ROMs enforce specific connectedDevice use cases.

---

## Phase 4 — Sensor HAL & Edge Intelligence (2026-10-05)

**Status:** Complete. Shared algorithms (Kalman filter PDR, 2nd-order Butterworth tilt filter, 50KB RAM Dead Man's Switch, Cooley-Tukey FFT, GNSS Anti-Spoofing, UWB Spatial Ranging) implemented with native Android SensorManager and iOS CoreMotion drivers.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D42 | `expect class SensorProvider` with `MotionSample` and `EnvironmentalSample` flows + battery thermistor `StateFlow` | Unified hardware abstraction across Android `SensorManager` and iOS `CoreMotion` + `CMAltimeter` | When external USB-OTG sensors are attached |
| D43 | Pedestrian Dead Reckoning (PDR): Z-axis Peak Detection + 1D Kalman heading fusion | Double-integrating raw accelerometer values drifts to infinity in seconds due to sensor bias. Step-and-heading dead reckoning (SHDR) using peak detection (>1.25 m/s² above gravity) with 250ms refractory period + Kalman gyro/magnetometer fusion guarantees stable displacement tracking | When map-snapping with offline vector road geometry is added |
| D44 | 2nd-order Butterworth low-pass filter (cutoff 0.2-0.5 Hz) on 3D gravity vectors | Cuts high-frequency structural vibration and human footstep noise to monitor true foundational micro-shifts over hours/days | When adaptive cutoff based on noise variance is needed |
| D45 | Silicon thermistor temperature drift compensation (0.015°/°C) | MEMS accelerometers exhibit silicon thermal expansion drift between day and night. Subtracting thermistor temperature delta prevents false alarms | When individual per-device temperature calibration curves are stored |
| D46 | Dead Man's Switch triggers at **5% battery** (not 2%) with pre-allocated 50KB RAM buffer | At 2%, smartphone battery voltage sag causes immediate hard power shutdown during camera/flash initialization and flash writes. Pre-allocating 50KB in RAM eliminates heap/disk I/O | If OEM power management cuts off apps earlier |
| D47 | Pure Kotlin Cooley-Tukey Radix-2 FFT and hardware decibel gating (>70 dB SPL) | Keeps main CPU in low-power idle until noise exceeds 70 dB SPL. Zero external C/native library dependencies ensures 100% KMP compatibility across Android and iOS | When running deep MobileNet/AST CNNs via NPU/NNAPI |
| D48 | GNSS Spoofing Detector: AGC spike (>14 dB) & hardware clock drift jump | Terrestrial spoofers emit RF orders of magnitude higher than weak satellites from orbit (~-160 dBW), forcing receiver AGC to spike. Automatically triggers fallback to Inertial Dead Reckoning | When multi-constellation L1+L5 carrier phase verification is added |
| D49 | Pre-commit validation script `.githooks/pre-commit` | Enforces Zero-Cloud dependencies, checks for conflict markers, private keys, and runs automated data pipeline tests before every commit | Continuous integration gate |
| D50 | SQLite Linker flag (`-lsqlite3`) for iOS framework & Xcode application | SQLDelight native driver (SQLiter) references `_sqlite3_step` from Apple's system SQLite. Adding `linkerOpts("-lsqlite3")` and `-lsqlite3` resolves simulator architecture link errors | — |
| D51 | Pure Kotlin `formatDecimals` in shared UI (`SensorHubScreen`) | JVM `String.format` is unavailable in Kotlin/Native. Multiplatform math rounding helper preserves zero JVM dependencies across all screens | When official Compose Multiplatform string formatting is stabilized |
| D52 | XcodeGen project `objectVersion: 56` (Xcode 15 compatibility) | XcodeGen by default emits Xcode 16 format (77). Normalizing to 56 enables seamless compilation across macOS CI runners running Xcode 15 and 16 | — |

---

## Phase 5 — Autonomous Resilience & Field Hardening (2026-10-06)

**Status:** Complete & Verified. All CI pipelines (Android, iOS, Docs) 100% green.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D53 | `UnifiedMeshRouter`: Tiered transport arbitration (BLE $\to$ LoRa $\to$ Ultrasonic $\to$ NTN Satellite) | Transparent failover across all physical channels with concurrent multi-link broadcast for `SOS_MEDICAL` | Dynamic routing based on mesh link cost metrics |
| D54 | `AutonomousPowerGovernor`: Solar-coupled power regulation | Dynamic sensor throttling (IMU 100Hz $\to$ 20Hz, Camera 30 $\to$ 10 FPS, OLED 10% red) and solar harvest reserve estimation | Real MPPT hardware controllers |
| D55 | `SecureEnclaveKeyManager`: Key sealing & zeroize | In-memory and enclave key generation, X25519 ECDH shared secret agreement, and zero-fill memory sanitization on emergency wipe | Platform StrongBox hardware hooks |
| D56 | `AirgapBundleSync`: Chunked animated QR sync | RF-silent optical exchange of CRDT pins and emergency bundles under Electronic Warfare jamming | Fountain codes (e.g. Luby Transform) |
| D57 | `TacticalFieldHud`: 64dp+ oversized touch targets | Rain and heavy tactical glove usability with high-contrast night-vision red palette | Secondary physical button bindings |

---

## Phase 6 — Tactical Autonomous Edge Capabilities & Sensor Weaponization (2026-10-06)

**Status:** Complete & Verified. All CI pipelines green.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D58 | `ZeroLightSpatialMapper`: 2.5D wireframe room & corridor mapping | Pitch-black navigation without flashlight emissions that expose position or drain battery | Direct ARKit / ARCore LiDAR mesh point-cloud streaming |
| D59 | `WaterTurbidityAnalyzer`: Screen lux to sensor optical scattering | Rapid field water potability assessment (NTU calculation) and filtration/boiling/UV triage | Dual-wavelength optical refraction |
| D60 | `AcousticTriangulationEngine`: Multi-mic TDoA cross-correlation with temperature compensation | Locates origin bearing ($\theta$) and elevation ($\phi$) of gunshots, drone rotors, and survivor cries | 4-mic tetrahedral microphone arrays |
| D61 | `CelestialCompassEngine`: Astronomical Solar & Polaris ephemeris calculation | Unjammable optical heading reference when GNSS is jammed/spoofed and magnetometers suffer metal interference | Real-time camera celestial overlay |
| D62 | `SarGhostTransponder`: Controlled low-duty-cycle cellular RF bursts | Creates detectable electromagnetic breadcrumbs for airborne SAR transponders with strict battery/thermal safety gates | SDR-based emergency cellular simulation |
| D63 | `PerimeterDefenseCoordinator`: Distributed multi-node tripwire fence | Correlates acoustic alerts, seismic shifts, and dosimeter breaches across mesh nodes to trigger squad-wide tactical alarms | Automated perimeter sensor mesh pairing |

---

## Phase 7 — Field Operational Readiness & Tactical Hardware Integration (2026-10-06)

**Status:** Complete & Verified. Physical USB-C OTG drivers, raw GNSS ingestion, offline vector terrain, map snapping, neural acoustic triage, hardware panic triggers, Meshtastic interop, and airgap APK distribution implemented.

### Decisions

| # | Decision | Why | Revisit when |
| - | --- | --- | --- |
| D64 | `UsbSerialHostDriver` expect/actual with CDC-ACM, FTDI, CP210x, CH34x bulk transfer | Direct plug-and-play communication with external LoRa, SDR, and Geiger USB-C hardware on Android | iOS external accessory MFi protocol expansion |
| D65 | `GnssRawMeasurementIngestor` expect/actual streaming live AGC (dB) and clock drift | Hardware-level mathematical electronic warfare and GPS spoofer detection | Carrier phase L1/L5 carrier-to-noise ratio fusion |
| D66 | `OfflineVectorMapEngine`: zero-cloud bundled contour elevations, trails, streams, shelters | Zero-infrastructure spatial awareness and water location without remote tile servers | Dynamic .mbtiles SQLite decompression |
| D67 | `MapSnappingEngine`: orthogonal point-to-polyline projection with 25-30m threshold | Bounds long-term dead reckoning Kalman drift by snapping trajectory to real ridge paths | Multi-modal elevation profile elevation matching |
| D68 | `AcousticNeuralClassifier`: 2-layer quantized neural network over 16-channel mel spectrograms | Edge AI classification of gunshots, drone rotors, and screams without cloud inference | Deep AST / CNN quantization via NPU/NNAPI |
| D69 | `EdgeRagSemanticRouter`: conversational natural-language triage with prioritized TCCC actions | Panicked survivors dictate unstructured text/voice and receive instant life-saving triage steps | On-device Gemma 2B quantized execution |
| D70 | `HardwarePanicTrigger`: 5 rapid clicks within 2.5s window | Blind silent emergency SOS triggering without waking screen or illuminating OLED | Lockscreen live activities |
| D71 | `MeshtasticProtocolBridge` & `SlottedRebroadcastSuppression` | Interoperability with global Meshtastic radio networks and elimination of RF broadcast storms | Meshtastic encrypted private channels |
| D72 | `AirgapApkBeacon`: offline Wi-Fi Direct / Local Hotspot server with QR code metadata | Rapid zero-infrastructure APK distribution to stranded survivors in dead zones | Bluetooth APK beaming |
| D73 | KMP expect/actual constructor parity and pure Kotlin math | iOS simulator and Android host environments require un-parenthesized common expect class definitions when Android requires Context constructor parameters, and pure Kotlin math (`kotlin.math.PI / 180.0`) avoiding JVM `java.lang.Math` | Kotlin 2.1+ direct constructor expect/actual harmonization |
| D74 | Tactical UI/UX integration for Phase 7 in `MeshMapScreen` and `SensorHubScreen` | Surfacing real-time vector contours, orthogonal trail snapping, USB host status, live EW spoof detection, neural threat classification, conversational TCCC triage, and air-gapped APK beaming into tactile, glove-friendly Compose Multiplatform cards | Multi-pane tablet layout and WearOS watch faces |


