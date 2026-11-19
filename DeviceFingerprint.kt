package com.naatdelay.dd7.licensing

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceIdentity(
    val shortId: String,  // e.g. SHD-8F4A-91C2
    val sha256Hash: String,
    val model: String,
    val androidVersion: String
)

@Singleton
class DeviceFingerprint @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("HardwareIds")
    fun resolve(): DeviceIdentity {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "fallback-id"
        val raw = "$androidId|${Build.BRAND}|${Build.BOARD}|${Build.HARDWARE}|dd7x-salt-v1"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        val upper = hex.uppercase()
        val shortId = "SHD-${upper.substring(0, 4)}-${upper.substring(4, 8)}"
        return DeviceIdentity(
            shortId = shortId,
            sha256Hash = hex,
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()
        )
    }
}
