# Vision

> Refined from the original ideation conversation ([`bastion_zero.md`](https://github.com/FranekJemiolo/bastion-zero) source, not vendored here).

## One-line

A smartphone becomes an **autonomous, zero-infrastructure survival multi-tool and edge-compute node** — no cell towers, no cloud, no grid.

## Why

Phones contain GNSS, barometer, magnetometer, accelerometer, gyroscope, multiple microphones, BLE, Wi-Fi and sometimes UWB/LiDAR. In a crisis, consumer software wastes all of it because it assumes servers and connectivity. Bastion Zero uses the hardware directly, locally.

## Principles

1. **Zero infrastructure.** No cloud APIs, no remote auth, no Firebase/Retrofit, no telemetry. The Android manifest requests no `INTERNET` permission.
2. **Battery is the scarcest resource.** Pure `#000000` background, `#FF3B30` red text (OLED pixels off, night vision preserved), throttled sensors, hardware interrupts over timers.
3. **Physics over polish.** Repurpose everyday sensors as instruments (tilt monitor, dead reckoning, acoustic detection, thermistor hypothermia alarm).
4. **Graceful degradation.** Shared logic runs everywhere; each platform exposes what its OS allows. iOS is a "lite" node by OS constraint, not by neglect.
5. **Trust nothing on the mesh.** Every packet is signed; ordering uses logical clocks, never wall-clock time.
6. **Expand outward.** Phone is the brain for optional peripherals (LoRa, thermal, SDR, Geiger, solar).

## Pillars

| Pillar | What it does |
| --- | --- |
| **PowerOS** | OLED-black/red theme, `PowerGovernor` throttling by battery level and motion, Doze workarounds (Android), graceful BG tasks (iOS) |
| **MeshLink** | Flood-routed BLE ad-hoc network carrying ≤200-byte signed protobuf packets: SOS, hazard/resource pins, pings |
| **Sensor HAL** | Uniform `SensorProvider` over internal sensors and (Android) USB-OTG peripherals; dead reckoning, structural tilt, acoustic edge-AI, Dead Man's Switch |
| **Offline Data** | Local vector maps (`.mbtiles`) and an FTS-searchable medical/survival wiki in SQLite |
| **Tactical Hub** | External hardware: LoRa mesh bridge, LWIR thermal with emissivity correction, SDR triangulation, dosimeter exclusion zones, solar AR |

## Platform reality

| Capability | Android | iOS |
| --- | --- | --- |
| Offline maps, wiki, dead reckoning | Full | Full |
| BLE mesh | Foreground + background (foreground service) | Foreground active, background passive |
| Radio / OS kill-switches | Limited to what APIs permit | Not possible |
| USB-C serial sensors | Full (OTG) | Needs MFi — out of scope |
| Haptic "chords" | `VibrationEffect.Waveform` | Core Haptics (later) |

## Feature catalogue (by environment)

- **Universal:** survival-mode UI, BLE/Wi-Fi mesh, offline GNSS maps, offline knowledge base.
- **Wilderness:** SOS strobe + siren, flora/fungi on-device vision, barometric storm warning, celestial/solar compass.
- **Urban disaster:** structural tilt monitor, mesh hazard/resource pins (CRDT-merged), acoustic drone/vehicle detection, FM radio where hardware allows.
- **Medical:** kinematic trauma log (G-force/fall), PPG vitals via camera, AR burn/wound sizing, stealth haptic alerts.
- **Rescue interfacing:** acoustic transponder, **Dead Man's Switch** final BLE payload, UWB micro-location.
- **Advanced/underused APIs:** UWB ranging, Wi-Fi Aware bulk sync, raw GNSS spoof detection, low-power acoustic gating.

## Future direction (same spirit)

Bluetooth 6 Channel Sounding for cheap-phone ranging; Android NTN satellite uplink gateway for mesh SOS; ultrasonic acoustic networking when RF is jammed; on-device LLM medical RAG; geospatial pipeline that strips a region to a tiny `.mbtiles`.

## Reality-check notes on the source brainstorm

Some ideas in the source conversation overstate what public APIs allow. They are kept as research items, not promises:

- **Cellular "ghost pinging"**, **forcing radio kill-switches**: not exposed to normal apps.
- **Flashing a TFLite model into the audio DSP / hardware dB interrupt**: not available to third-party apps; plan uses a low-rate software level gate plus a duty-cycled foreground service.
- **BLE advertising after the CPU "HALT"**: on stock Android/iOS an app cannot keep advertising after process death. The Dead Man's Switch design must be validated per device; fallbacks are a longer-lived advertising set at higher battery thresholds.
- **iOS background UWB via Live Activity / `setInstantCommunicationModeEnabled` semantics**: verify against current docs before building.
- **Burn TBSA / fluid dosing, medical numbers**: educational aid only; needs clinician review before shipping.
