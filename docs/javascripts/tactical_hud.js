/* ==========================================================================
   BASTION ZERO // INTERACTIVE TACTICAL HUD SIMULATOR
   Simulates MeshLink BLE flood routing, Lamport clocks & CRDT pin syncing
   ========================================================================== */

document.addEventListener("DOMContentLoaded", function () {
  const terminal = document.getElementById("tactical-terminal-output");
  if (!terminal) return;

  let lamportClock = 142;
  let peerCount = 4;
  let verifiedPins = 3;

  function appendLog(html) {
    const p = document.createElement("div");
    p.innerHTML = html;
    p.style.marginBottom = "4px";
    terminal.appendChild(p);
    terminal.scrollTop = terminal.scrollHeight;
  }

  function formatTime() {
    const d = new Date();
    return d.toTimeString().split(" ")[0];
  }

  window.simBroadcastSos = function (type) {
    lamportClock += 1;
    const isMedical = type === "MEDICAL";
    const sig = "ed25519:7f8a9b2c..." + Math.random().toString(16).substring(2, 6);
    appendLog(
      `<span style="color:#666;">[${formatTime()}]</span> <span class="log-sos">🚨 LOCAL BROADCAST // SOS_${type}</span> ` +
      `| L:${lamportClock} | Lat: 52.2297N Lon: 21.0122E | Sig: <span style="color:#aaa;">${sig}</span> | TTL: 5`
    );

    setTimeout(() => {
      appendLog(
        `<span style="color:#666;">[${formatTime()}]</span> <span class="log-hop">⇄ RELAY ACK // Node_RidgePatrol</span> ` +
        `verified Ed25519 signature in 1.4ms. Decrementing TTL to 4. Flooding to sector B...`
      );
    }, 450);

    setTimeout(() => {
      appendLog(
        `<span style="color:#666;">[${formatTime()}]</span> <span class="log-hop">⇄ RELAY ACK // Node_ForestPost_Alpha</span> ` +
        `forwarded to Ranger Camp. <span class="log-sos">Haptic Medical SOS pulse triggered on 3 peer receivers.</span>`
      );
    }, 900);
  };

  window.simDropPin = function (kind, label) {
    lamportClock += 1;
    const pinId = "pin_" + lamportClock + "_" + Math.floor(Math.random() * 900 + 100);
    appendLog(
      `<span style="color:#666;">[${formatTime()}]</span> <span class="log-pin">📍 CRDT AUTHOR // ${kind}</span>: ` +
      `"${label}" | ID: <code>${pinId}</code> | L:${lamportClock} | LWW-Element-Set updated locally.`
    );

    setTimeout(() => {
      appendLog(
        `<span style="color:#666;">[${formatTime()}]</span> <span class="log-hop">⇄ MESH SYNC // GATT Peripheral</span> ` +
        `pushed pin payload (48 bytes) to 2 connected peers. Deduplication window committed.`
      );
    }, 500);
  };

  window.simRemoteSync = function () {
    lamportClock += 3;
    peerCount += 1;
    const peerCounterElem = document.getElementById("hud-peer-count");
    if (peerCounterElem) peerCounterElem.innerText = peerCount;

    appendLog(
      `<span style="color:#666;">[${formatTime()}]</span> <span style="color:#39ff14;">⚡ NEW PEER DISCOVERED // Node_WildernessEcho (RSSI -68dBm)</span>`
    );
    appendLog(
      `<span style="color:#666;">[${formatTime()}]</span> <span class="log-hop">⇄ CRDT MERGE // Received 1 new hazard pin</span>: ` +
      `"Bridge Out - River Flood" | LWW-Join completed deterministically. Clock advanced to ${lamportClock}.`
    );
  };

  window.simClearLog = function () {
    terminal.innerHTML = "";
    appendLog(
      `<span style="color:#8ce980;">[SYSTEM] Terminal reset. Node ready. PowerOS Governor active (60 Hz OLED, 50% BLE Duty).</span>`
    );
  };
});
