# Phase 6 Implementation Plan: Tactical Autonomous Edge Capabilities & Sensor Weaponization

**Document Version:** 1.0.0  
**Target Milestone:** Phase 6 — Advanced Optical Diagnostics, Multi-Mic Acoustic Triangulation, Celestial Ephemeris, SAR Transponder & Tactical Perimeter Defense  
**Author:** Principal Systems Architect & Mobile Systems Engineer  
**Status:** Approved for Implementation  

---

## 1. Objectives & Executive Scope

Stages 1 through 5 of Bastion Zero transformed a standard smartphone into an autonomous, hardened survival operating system:
- **Stage 1:** Burner MVP foundation (zero-cloud, Ed25519, Lamport clocks, BLE mesh, CRDT map pins, offline SQLite wiki).
- **Stage 2:** Flagship sensor fusion (kinematic trauma black-box, camera optical PPG, wound photogrammetry, thermal imaging).
- **Stage 3:** Tactical Hub peripherals (LoRa PHY bridge, dosimeter radiation tracking, SDR signal triangulation, solar AR).
- **Stage 4:** Bleeding-edge networks (ultrasonic AFSK modem, Bluetooth 6.0 channel sounding, Android 15 NTN satellite bridge, on-device Edge-LLM RAG).
- **Stage 5:** Autonomous resilience & field hardening (unified multi-transport arbitrator, solar-coupled power governor, hardware-sealed key enclave, optical QR sync, tactical glove HUD).

**Phase 6** fulfills the final edge-sensor capabilities envisioned in the original architectural specification (`bastion_zero.md`), weaponizing the smartphone's raw physical sensors for extreme wilderness survival and urban catastrophe environments:

1. **Zero-Light Spatial Wireframe Navigation (`ZeroLightSpatialMapper`):** Converts raw depth point clouds (LiDAR / ToF / structured light) into spatial topological wireframes and obstacle corridors without emitting detectable visible light.
2. **Optical Water Turbidity & Particulate Analyzer (`WaterTurbidityAnalyzer`):** Leverages screen lux emissions and ambient light sensors / optical camera receptors to calculate Nephelometric Turbidity Units (NTU) and deliver actionable field water purification protocols.
3. **Multi-Microphone Acoustic Triangulation Engine (`AcousticTriangulationEngine`):** Employs Time Difference of Arrival (TDoA) cross-correlation across device microphone arrays to compute bearing azimuth and elevation angles toward acoustic threats (gunshots, rotor blades, survivor distress calls).
4. **Celestial & Solar Ephemeris Navigation (`CelestialCompassEngine`):** Provides unjammable astronomical navigation using solar position and North Star (Polaris) ephemeris when GNSS is jammed or spoofed and magnetometers suffer structural magnetic distortion.
5. **Airborne SAR Ghost Transponder (`SarGhostTransponder`):** Schedules controlled, ultra-low-duty-cycle cellular emergency connection bursts creating RF breadcrumbs for airborne Search & Rescue (SAR) IMSI-catchers while enforcing strict battery/thermal safety gates.
6. **Distributed Tactical Perimeter & Tripwire Coordinator (`PerimeterDefenseCoordinator`):** Integrates multiple distributed Bastion Zero handsets into an ad-hoc perimeter tripwire fence, correlating acoustic anomalies, structural seismic shifts, and dosimeter radiation breaches.
7. **Comprehensive End-to-End Test Suite (`Phase6TacticalE2ETest`):** Validates all Phase 6 subsystems working in concert under realistic multi-faceted disaster scenarios.

---

## 2. Phase 6 System Architecture

```
+--------------------------------------------------------------------------------------------------+
|                            PHASE 6: TACTICAL EDGE SENSOR WEAPONIZATION                           |
+--------------------------------------------------------------------------------------------------+
                                                 |
         +---------------------------------------+---------------------------------------+
         |                                       |                                       |
+--------v-----------------------+  +------------v-------------------+  +----------------v---------------+
|     OPTICAL & SPATIAL SUITE    |  |    ACOUSTIC & CELESTIAL NAV    |  |     TACTICAL COMMS & DEFENSE   |
|                                |  |                                |  |                                |
| 1. ZeroLightSpatialMapper      |  | 3. AcousticTriangulationEngine |  | 5. SarGhostTransponder         |
|    - LiDAR / ToF point-clouds  |  |    - Multi-mic TDoA delay      |  |    - Airborne SAR RF bursts    |
|    - Obstacle wireframes       |  |    - Azimuth / elevation math  |  |    - Low-duty-cycle governor   |
|    - Drop-off / corridor alert |  |    - Speed-of-sound temp comp  |  |                                |
|                                |  |                                |  | 6. PerimeterDefenseCoordinator |
| 2. WaterTurbidityAnalyzer      |  | 4. CelestialCompassEngine      |  |    - Multi-node tripwire fence |
|    - Screen lux to sensor      |  |    - Solar & Polaris ephemeris |  |    - Acoustic + seismic fusion |
|    - NTU scattering equation   |  |    - Unjammable optical ref    |  |    - Mesh-wide alert broadcast |
|    - Field purification triage |  |    - Parallax / shadow-stick   |  |                                |
+--------------------------------+  +--------------------------------+  +--------------------------------+
                                                 |
         +---------------------------------------+---------------------------------------+
                                                 v
               +-------------------------------------------------------------------+
               |             E2E VERIFICATION & VALIDATION (Phase 6 Suite)          |
               |      Subterranean Cave Escape · Hostile EW Drone Defense ·        |
               |             Stranded Mountain Search & Camp Perimeter             |
               +-------------------------------------------------------------------+
```

---

## 3. Technical Component Specifications

### 3.1 Zero-Light Spatial Wireframe Navigation (`com.bastionzero.optical.ZeroLightSpatialMapper`)
* **Purpose:** Enables navigation through pitch-black collapsed buildings, subterranean tunnels, and dense nocturnal canopies without emitting visible light beams that expose operator position or drain batteries.
* **Physics & Math:**
  - Input: 3D point cloud $(x, y, z)$ sampled from infrared Time-of-Flight / LiDAR sensors.
  - Plane Segmentation: Fits ground floor plane using RANSAC ($ax + by + cz + d = 0$).
  - Obstacle Extraction: Filters points above ground plane ($> 0.15\text{ m}$) and below head height ($< 2.2\text{ m}$).
  - Cluster Bounding Boxes: Computes minimum bounding boxes and clearance vectors.
  - Drop-off / Pitfall Warning: Detects sudden floor drop-offs ($> 0.5\text{ m}$ elevation step) in the forward trajectory vector.
* **Output:** Lightweight 2.5D wireframe segments rendered on the `#000000` / `#FF1744` OLED tactical canvas.

### 3.2 Optical Water Turbidity & Particulate Analyzer (`com.bastionzero.optical.WaterTurbidityAnalyzer`)
* **Purpose:** Determines water potability and required purification techniques for scavenged water in field environments.
* **Physics & Math:**
  - Screen emits calibrated white/optical flash illumination $I_0$.
  - Receiver (ambient light sensor / camera receptor) measures attenuated transmitted intensity $I$ through sample container.
  - Optical Transmittance: $T = I / I_0$.
  - Turbidity Formula:
    $$\text{NTU} \approx \alpha \cdot \ln(1 / T)$$
    where $\alpha$ is a calibrated extinction factor based on optical path length ($d = 5.0\text{ cm}$).
* **Actionable Field Guidance:**
  - $< 1.0\text{ NTU}$ (Clear): Safe for direct UV purification or chemical halogenation.
  - $1.0 - 5.0\text{ NTU}$ (Slightly Turbid): Mechanical filtration (0.1 micron) required before disinfection.
  - $5.0 - 50.0\text{ NTU}$ (Cloudy / High Particulate): Flocculation / settling required; boiling required for $> 3\text{ minutes}$.
  - $> 50.0\text{ NTU}$ (Heavy Mud / Contaminated): Extreme danger; emergency sand/charcoal pre-filter mandated.

### 3.3 Multi-Microphone Acoustic Triangulation Engine (`com.bastionzero.acoustic.AcousticTriangulationEngine`)
* **Purpose:** Detects sound origin vectors (gunshots, rotor blade passes, human distress screams) using microsecond time-difference-of-arrival across device microphone arrays.
* **Physics & Math:**
  - Temperature-Compensated Speed of Sound:
    $$c(T) = 331.3 \cdot \sqrt{1 + \frac{T}{273.15}} \quad (\text{m/s})$$
  - Time Difference of Arrival (TDoA): Computes cross-correlation delay $\Delta t_{12}$ between Mic 1 (top) and Mic 2 (bottom) separated by baseline distance $d \approx 0.15\text{ m}$:
    $$\cos(\theta) = \frac{c(T) \cdot \Delta t_{12}}{d}$$
  - Azimuth & Elevation Mapping: For a 3-mic array (Top, Bottom-Left, Bottom-Right), solves the 2D planar bearing $\theta \in [0^\circ, 360^\circ)$ and elevation angle $\phi$.
  - Confidence metric based on normalized cross-correlation peak height ($R_{xy} \in [0, 1]$).

### 3.4 Celestial & Solar Ephemeris Navigation (`com.bastionzero.nav.CelestialCompassEngine`)
* **Purpose:** Provides absolute true-North bearing when GNSS is jammed or spoofed and local metal/rebar renders magnetometers untrustworthy.
* **Physics & Math:**
  - Solar Declination ($\delta$) and Equation of Time ($EoT$):
    Calculated from UTC epoch timestamp and Julian Day offset.
  - Solar Hour Angle ($H$) and Elevation ($h$):
    $$\sin(h) = \sin(\phi) \sin(\delta) + \cos(\phi) \cos(\delta) \cos(H)$$
  - Solar Azimuth ($\text{Az}_\odot$):
    $$\cos(\text{Az}_\odot) = \frac{\sin(\delta) - \sin(\phi) \sin(h)}{\cos(\phi) \cos(h)}$$
  - Polaris Heading:
    For Northern Hemisphere, Polaris provides true geographic North within $0.7^\circ$ offset throughout the sidereal day.
  - Shadow-Stick Validation: Projects shadow angle against device orientation to extract operator heading.

### 3.5 Airborne SAR Ghost Transponder (`com.bastionzero.hal.SarGhostTransponder`)
* **Purpose:** Creates intermittent RF breadcrumbs for airborne Search and Rescue aircraft equipped with cellular IMSI-catchers / transponders without requiring SIM card registration or cellular network connection.
* **Protocols & Safety Constraints:**
  - Transmit Duty Cycle: Strict low-duty-cycle bursts (e.g. 500 ms active ping every 15 minutes).
  - Battery & Thermal Interlocks: Hard lockout if battery $< 5\%$ or battery temperature $> 45^\circ\text{C}$ to prevent battery depletion or thermal runaways.
  - Payload Encoding: Synthesizes emergency call connection setup bursts containing node identity, trauma state, and coordinates.

### 3.6 Distributed Tactical Perimeter & Tripwire Coordinator (`com.bastionzero.tactical.PerimeterDefenseCoordinator`)
* **Purpose:** Links multiple deployed handsets into a synchronized early-warning defensive perimeter.
* **Fusion Logic:**
  - Tripwire Nodes report local detections:
    - Acoustic threats ($> 75\text{ dB}$, rotor/gunshot signature)
    - Structural seismic shifts ($> 0.5^\circ$ tilt or vibration spike)
    - Radiation boundary breaches ($> 5.0\ \mu\text{Sv/h}$)
  - Multi-Sensor Correlation: Coincident alerts from $\ge 2$ nodes upgrade threat posture from `PERIMETER_CLEAR` $\to$ `PERIMETER_WARNING` $\to$ `PERIMETER_BREACH`.
  - Dispatches high-priority tactile haptic warnings and BLE mesh broadcast alerts across all squad members.

---

## 4. Phase 6 Implementation & Validation Roadmap

1. **Phase 6 Implementation Plan Documentation:**
   - Commit and push `docs/PHASE_6_IMPLEMENTATION_PLAN.md`.
2. **Core Domain Implementation (`shared/commonMain/kotlin/com/bastionzero/...`):**
   - `optical/ZeroLightSpatialMapper.kt`
   - `optical/WaterTurbidityAnalyzer.kt`
   - `acoustic/AcousticTriangulationEngine.kt`
   - `nav/CelestialCompassEngine.kt`
   - `hal/SarGhostTransponder.kt`
   - `tactical/PerimeterDefenseCoordinator.kt`
3. **Comprehensive Unit Testing (`shared/commonTest/kotlin/com/bastionzero/...`):**
   - `optical/ZeroLightSpatialMapperTest.kt`
   - `optical/WaterTurbidityAnalyzerTest.kt`
   - `acoustic/AcousticTriangulationEngineTest.kt`
   - `nav/CelestialCompassEngineTest.kt`
   - `hal/SarGhostTransponderTest.kt`
   - `tactical/PerimeterDefenseCoordinatorTest.kt`
4. **End-to-End Simulation Test:**
   - `e2e/Phase6TacticalE2ETest.kt`
5. **Documentation & Architecture Synchronization:**
   - Update `README.md`, `docs/VISION.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/JOURNAL.md`, and `docs/index.md`.
6. **Pre-Commit & CI Validation:**
   - Run `./.githooks/pre-commit`, commit, push to `main`, and verify green CI on Android, iOS, and Docs.
