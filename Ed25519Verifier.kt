package com.naatdelay.dd7.licensing

import android.util.Base64
import com.naatdelay.dd7.BuildConfig
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class VerifiedLicensePayload(
    val deviceId: String,
    val tier: String
)

@Singleton
class Ed25519Verifier @Inject constructor() {

    /**
     * Verifies an offline signed token of format: `<base64url_json_payload>.<base64_ed25519_sig>`
     * against the app's compiled-in Ed25519 Public Key and current device fingerprint.
     */
    fun verifyToken(signedToken: String, expectedDeviceId: String): VerifiedLicensePayload? {
        return try {
            val pubKeyB64 = BuildConfig.LICENSE_PUBLIC_KEY_ED25519.trim()
            if (pubKeyB64.isEmpty() || pubKeyB64.startsWith("REPLACE_")) return null

            val parts = signedToken.trim().split(".")
            if (parts.size != 2) return null

            val payloadB64 = parts[0]
            val sigBytes = Base64.decode(parts[1], Base64.DEFAULT)
            val pubKeyBytes = Base64.decode(pubKeyB64, Base64.DEFAULT)
            if (pubKeyBytes.size != 32 || sigBytes.size != 64) return null

            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(pubKeyBytes, 0))
            val payloadBytes = payloadB64.toByteArray(Charsets.UTF_8)
            signer.update(payloadBytes, 0, payloadBytes.size)
            if (!signer.verifySignature(sigBytes)) return null

            val decodedJson = String(Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            val obj = JSONObject(decodedJson)
            val tokenDeviceId = obj.optString("device_id", "")
            val tier = obj.optString("tier", "PRO_LIFETIME")

            if (!tokenDeviceId.equals(expectedDeviceId, ignoreCase = true)) {
                return null
            }
            VerifiedLicensePayload(deviceId = tokenDeviceId, tier = tier)
        } catch (_: Throwable) {
            null
        }
    }
}
