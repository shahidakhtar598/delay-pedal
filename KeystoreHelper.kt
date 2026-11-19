package com.naatdelay.dd7.licensing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dd7x_secure_license_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSignedToken(licenseKey: String, signedToken: String) {
        prefs.edit()
            .putString("license_key", licenseKey)
            .putString("signed_token", signedToken)
            .apply()
    }

    fun getSignedToken(): String? = prefs.getString("signed_token", null)
    fun getLicenseKey(): String? = prefs.getString("license_key", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
