package com.naatdelay.dd7.licensing

import com.naatdelay.dd7.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ActivationApiResult {
    data class Activated(val signedToken: String, val planTier: String) : ActivationApiResult
    data class FriendlyError(val errorCode: String, val message: String, val showTransferSupport: Boolean = false) : ActivationApiResult
}

@Singleton
class LicenseApiClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun activateOnline(licenseKey: String, identity: DeviceIdentity): ActivationApiResult =
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.LICENSE_API_BASE_URL.trimEnd('/')
            if (baseUrl.isEmpty() || baseUrl.contains("YOUR_PROJECT_REF")) {
                return@withContext ActivationApiResult.FriendlyError(
                    errorCode = "OFFLINE_ONLY_BUILD",
                    message = "Online activation server is not configured. Paste your offline signed token instead."
                )
            }

            val payload = JSONObject().apply {
                put("license_key", licenseKey)
                put("device_fingerprint", identity.shortId)
                put("device_fingerprint_hash", identity.sha256Hash)
                put("device_model", identity.model)
                put("android_version", identity.androidVersion)
                put("app_version", BuildConfig.VERSION_NAME)
            }

            val request = Request.Builder()
                .url("$baseUrl/activate-license")
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                    if (resp.isSuccessful && json != null) {
                        val token = json.optString("signed_token", "")
                        val tier = json.optString("plan_tier", "PRO_LIFETIME")
                        if (token.isNotEmpty()) {
                            return@withContext ActivationApiResult.Activated(token, tier)
                        }
                    }

                    val code = json?.optString("error_code") ?: "HTTP_${resp.code}"
                    when (resp.code) {
                        400, 404 -> ActivationApiResult.FriendlyError(
                            code,
                            "That activation key wasn't recognized. Please double-check the characters and try again."
                        )
                        409 -> ActivationApiResult.FriendlyError(
                            code,
                            "This key is already active on another device. If you changed your phone, tap 'Contact Support on WhatsApp' to request a free transfer.",
                            showTransferSupport = true
                        )
                        403 -> ActivationApiResult.FriendlyError(
                            code,
                            "This license key is no longer active. Please contact support if you believe this is a mistake.",
                            showTransferSupport = true
                        )
                        429 -> ActivationApiResult.FriendlyError(
                            code,
                            "Too many attempts. Please wait 60 seconds and try again."
                        )
                        else -> ActivationApiResult.FriendlyError(
                            code,
                            "Could not verify key right now. Please try again in a moment."
                        )
                    }
                }
            } catch (_: IOException) {
                ActivationApiResult.FriendlyError(
                    "NO_INTERNET",
                    "No internet connection detected. Please turn on mobile data or Wi-Fi for 5 seconds to activate your key."
                )
            }
        }
}
