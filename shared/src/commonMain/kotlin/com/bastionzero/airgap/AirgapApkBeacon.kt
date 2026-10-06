package com.bastionzero.airgap

data class ApkDistributionSession(
    val isHosting: Boolean,
    val ssid: String,
    val passkey: String,
    val captivePortalUrl: String,
    val apkSha256Checksum: String,
    val apkSizeBytes: Long,
    val appVersionName: String,
    val completedPeerDownloads: Int,
)

/**
 * Air-Gapped APK Distribution Beacon.
 * Manages zero-infrastructure peer-to-peer APK installation via Wi-Fi Direct / Local Hotspot
 * so stranded survivors can install Bastion Zero directly in dead zones without app stores.
 */
class AirgapApkBeacon(
    private val appVersion: String = "1.0.0-tactical",
    private val apkSize: Long = 18_450_000L,
    private val apkSha256: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
) {

    private var isHostingActive = false
    private var downloadCounter = 0

    fun startHostingBeacon(nodeIdSuffix: String): ApkDistributionSession {
        isHostingActive = true
        val ssid = "BASTION_BEAM_$nodeIdSuffix"
        val passkey = "SURVIVE_$nodeIdSuffix"
        val url = "http://192.168.49.1:8080/BastionZero.apk"

        return ApkDistributionSession(
            isHosting = true,
            ssid = ssid,
            passkey = passkey,
            captivePortalUrl = url,
            apkSha256Checksum = apkSha256,
            apkSizeBytes = apkSize,
            appVersionName = appVersion,
            completedPeerDownloads = downloadCounter
        )
    }

    fun recordDownloadCompleted() {
        if (isHostingActive) {
            downloadCounter++
        }
    }

    fun stopHosting() {
        isHostingActive = false
    }

    /**
     * Formats an ultra-dense QR pairing payload for camera scanning.
     */
    fun formatQrPairingPayload(session: ApkDistributionSession): String {
        return "BZAPK:${session.appVersionName}|${session.ssid}|${session.passkey}|${session.captivePortalUrl}|${session.apkSha256Checksum}"
    }

    fun parseQrPairingPayload(qrString: String): ApkDistributionSession? {
        if (!qrString.startsWith("BZAPK:")) return null
        val parts = qrString.removePrefix("BZAPK:").split("|")
        if (parts.size < 5) return null

        return ApkDistributionSession(
            isHosting = false,
            appVersionName = parts[0],
            ssid = parts[1],
            passkey = parts[2],
            captivePortalUrl = parts[3],
            apkSha256Checksum = parts[4],
            apkSizeBytes = apkSize,
            completedPeerDownloads = 0
        )
    }
}
