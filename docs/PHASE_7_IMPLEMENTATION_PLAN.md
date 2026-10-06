# Phase 7 Implementation Plan: Field Operational Readiness & Tactical Hardware Integration

**Document Version:** 1.0.0  
**Target Milestone:** Phase 7 — Physical USB-C OTG Host Drivers, Raw GNSS Ingestion, Offline Vector Terrain & Map Snapping, Neural Edge Triage, Hardware Panic Triggers, Meshtastic Interop & Air-Gapped APK Distribution  
**Author:** Principal Systems Architect & Mobile Systems Engineer  
**Status:** Approved for Implementation  

---

## 1. Objectives & Executive Scope

Following the completion of Stages 1 through 6, Bastion Zero possesses a verified mathematical and algorithmic survival foundation across Kotlin Multiplatform and native platforms. 

**Phase 7** transitions the platform from algorithmic abstraction to **hardened field operational readiness**:

1. **Physical USB-C OTG Host Serial Driver (`UsbSerialHostDriver`):** Bridges Android's native `UsbManager` (supporting CDC-ACM, FTDI, CP210x, and CH34x chipsets) directly to `UsbSerialPeripheralBridge` and `TacticalHubBridge`, enabling plug-and-play with real physical LoRa transceivers (Meshtastic, LilyGO T-Beam, Heltec), RTL-SDR dongles, and Geiger dosimeters.
2. **Raw GNSS Measurement Ingestion (`GnssRawMeasurementIngestor`):** Integrates with Android's `GnssMeasurementsEvent.Callback` and `GnssStatus.Callback` to stream real Automatic Gain Control (AGC in dB), Carrier-to-Noise density ($C/N_0$), and Doppler carrier phase directly into `GnssSpoofingDetector`.
3. **Offline Vector Terrain & Contour Engine (`OfflineVectorMapEngine`):** Renders high-contrast vector contour elevations, hydrology (streams/springs), and emergency trail paths directly onto the pure OLED tactical canvas from bundled/extracted offline vector geometries.
4. **Dead Reckoning Map Snapping (`MapSnappingEngine`):** Snaps the step-and-heading Kalman filter displacement vectors from `InertialDeadReckoning` to offline terrain trails and topological ridge lines, eliminating unbounded drift.
5. **On-Device Edge Neural Classifier & Panicked Triage (`AcousticNeuralClassifier`, `EdgeRagSemanticRouter`):** Quantized spectrogram classifier for drone/rotor and ballistic acoustics, paired with natural language emergency intent triage for panicked field operators.
6. **Physical Hardware Key Panic Triggering (`HardwarePanicTrigger`):** Silent emergency SOS triggers via physical volume key sequences (e.g. 5 rapid clicks) without unlocking the device or illuminating the OLED screen.
7. **Meshtastic Protocol Interoperability & Slotted Rebroadcast (`MeshtasticProtocolBridge`, `SlottedRebroadcastSuppression`):** Bidirectional packet transcoding between Bastion Zero Protobuf and Meshtastic radio frames, coupled with slotted backoff suppression to prevent RF collision storms in dense survivor encampments.
8. **Air-Gapped Direct APK Field Distribution (`AirgapApkBeacon`):** Offline Wi-Fi Direct / local hotspot server enabling stranded civilians to beam and install the signed `BastionZero.apk` peer-to-peer without internet access or app store connectivity.
9. **Comprehensive Phase 7 End-to-End Test Suite (`Phase7OperationalE2ETest`):** Validating all operational subsystems working in concert under simulated disaster and electronic warfare conditions.

---

## 2. Technical Specifications & Architecture

### 2.1 USB-C OTG Host Driver & GNSS Raw Ingestion (`com.bastionzero.hal`)
* Interface `UsbSerialHostDriver` with `expect/actual` abstractions.
* Android `actual` negotiates USB device permissions via `UsbManager`, configures baud rates (9600 to 115200), and handles endpoints (bulk in/out) for FTDI FT232R, Silicon Labs CP2102, Prolific PL2303, and Qinheng CH340.
* Interface `GnssRawMeasurementIngestor` hooks into `GnssMeasurementsEvent` to compute AGC drop/spike anomalies and clock drift jumps.

### 2.2 Offline Vector Terrain & Map Snapping (`com.bastionzero.nav`, `com.bastionzero.ui`)
* `OfflineVectorMapEngine`: Parses topological vector contour lines, elevation gradients, trails, and water points; renders them into lightweight path segments for Compose Multiplatform.
* `MapSnappingEngine`: Projects $(x, y)$ dead-reckoning step vectors onto nearest trail segments using orthogonal projection ($P_{proj} = A + \frac{(P - A) \cdot (B - A)}{|B - A|^2} (B - A)$) with a dynamic distance threshold ($r \le 25\text{ m}$).

### 2.3 Acoustic Neural Classifier & Panicked Triage (`com.bastionzero.acoustic`, `com.bastionzero.rag`)
* `AcousticNeuralClassifier`: Quantized weights matrix and non-linear activation evaluating 2D mel-spectrogram energy distribution across 16 frequency bins to classify gunshot vs. drone rotor vs. wind vs. distress yell.
* `EdgeRagSemanticRouter`: Fast offline intent extraction matching natural language distress descriptions to immediate TCCC tactical field guides.

### 2.4 Hardware Panic Trigger & Tactical Lockscreen (`com.bastionzero.hal`, `com.bastionzero.ui`)
* `HardwarePanicTrigger`: Detects 5 rapid hardware clicks within a 2.5-second time window, firing `HapticChord.MEDICAL_SOS` and generating a pre-signed `SurvivalPacket` with highest logical clock.

### 2.5 Meshtastic Bridge & Slotted Suppression (`com.bastionzero.net`)
* `MeshtasticProtocolBridge`: Encapsulates Bastion Zero packets into Meshtastic `PortNum.TEXT_MESSAGE_APP` and `PortNum.POSITION_APP` frames with 4-byte node IDs.
* `SlottedRebroadcastSuppression`: Delays packet relay by a pseudo-random slotted interval:
  $$\Delta t = T_{slot} \cdot (1 + \text{hash}(\text{packetId}) \pmod K)$$
  Cancels rebroadcast if identical packet hash is overheard from $\ge 2$ neighbors within the window.

### 2.6 Air-Gapped APK Distribution (`com.bastionzero.airgap`)
* `AirgapApkBeacon`: Manages local offline distribution state, QR pairing token, and payload chunks for air-gapped app delivery.
