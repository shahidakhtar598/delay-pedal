package com.naatdelay.dd7.data.repository

import com.naatdelay.dd7.data.local.dao.LicenseDao
import com.naatdelay.dd7.data.local.entity.DeviceLicenseEntity
import com.naatdelay.dd7.licensing.ActivationApiResult
import com.naatdelay.dd7.licensing.DeviceFingerprint
import com.naatdelay.dd7.licensing.DeviceIdentity
import com.naatdelay.dd7.licensing.Ed25519Verifier
import com.naatdelay.dd7.licensing.KeystoreHelper
import com.naatdelay.dd7.licensing.LicenseApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LicenseUiStatus(
    val isProActivated: Boolean = false,
    val deviceIdentity: DeviceIdentity? = null,
    val licenseKey: String? = null
)

@Singleton
class LicenseRepository @Inject constructor(
    private val licenseDao: LicenseDao,
    private val deviceFingerprint: DeviceFingerprint,
    private val verifier: Ed25519Verifier,
    private val keystoreHelper: KeystoreHelper,
    private val apiClient: LicenseApiClient
) {
    private val _status = MutableStateFlow(LicenseUiStatus())
    val status: StateFlow<LicenseUiStatus> = _status.asStateFlow()

    /**
     * Startup integrity check: re-verifies Ed25519 signature against real hardware Device ID.
     * Prevents rooted SQLite tampering from unlocking Pro mode.
     */
    suspend fun verifyOnStartup() {
        val identity = deviceFingerprint.resolve()
        val vaultToken = keystoreHelper.getSignedToken()
        val dbRow = licenseDao.getLicense()
        val candidateToken = vaultToken ?: dbRow?.signedToken

        val verified = candidateToken?.let { verifier.verifyToken(it, identity.shortId) }
        if (verified != null) {
            _status.value = LicenseUiStatus(
                isProActivated = true,
                deviceIdentity = identity,
                licenseKey = keystoreHelper.getLicenseKey() ?: dbRow?.licenseKey
            )
        } else {
            // Revert safely to Trial without touching user presets
            licenseDao.upsert(
                DeviceLicenseEntity(
                    singletonId = 1,
                    deviceFingerprint = identity.shortId,
                    status = "TRIAL"
                )
            )
            _status.value = LicenseUiStatus(
                isProActivated = false,
                deviceIdentity = identity,
                licenseKey = null
            )
        }
    }

    suspend fun activate(input: String): ActivationApiResult {
        val identity = deviceFingerprint.resolve()
        val cleaned = input.trim()

        // Support direct offline signed token paste (contains ".")
        if (cleaned.contains(".")) {
            val verified = verifier.verifyToken(cleaned, identity.shortId)
            return if (verified != null) {
                persistActivation("OFFLINE-TOKEN", cleaned, identity)
                ActivationApiResult.Activated(cleaned, verified.tier)
            } else {
                ActivationApiResult.FriendlyError(
                    "INVALID_OFFLINE_TOKEN",
                    "That activation token doesn't match this device. Please double-check and try again."
                )
            }
        }

        // Standard online key activation (DD7X-XXXX-XXXX)
        val normalizedKey = cleaned.uppercase().replace(" ", "")
        val apiResult = apiClient.activateOnline(normalizedKey, identity)
        if (apiResult is ActivationApiResult.Activated) {
            val verified = verifier.verifyToken(apiResult.signedToken, identity.shortId)
            if (verified != null) {
                persistActivation(normalizedKey, apiResult.signedToken, identity)
                return apiResult
            }
            return ActivationApiResult.FriendlyError(
                "SIGNATURE_MISMATCH",
                "Could not verify the server signature on this device. Please contact support."
            )
        }
        return apiResult
    }

    private suspend fun persistActivation(key: String, signedToken: String, identity: DeviceIdentity) {
        keystoreHelper.saveSignedToken(key, signedToken)
        licenseDao.upsert(
            DeviceLicenseEntity(
                singletonId = 1,
                deviceFingerprint = identity.shortId,
                licenseKey = key,
                signedToken = signedToken,
                status = "ACTIVATED",
                activatedAt = System.currentTimeMillis()
            )
        )
        _status.value = LicenseUiStatus(
            isProActivated = true,
            deviceIdentity = identity,
            licenseKey = key
        )
    }
}
