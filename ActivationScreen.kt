package com.naatdelay.dd7.ui.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.BuildConfig
import com.naatdelay.dd7.data.repository.LicenseRepository
import com.naatdelay.dd7.data.repository.LicenseUiStatus
import com.naatdelay.dd7.licensing.ActivationApiResult
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationSheet(
    licenseRepo: LicenseRepository,
    licenseStatus: LicenseUiStatus,
    onDismiss: () -> Unit
) {
    val colors = LocalPedalColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deviceId = licenseStatus.deviceIdentity?.shortId ?: "SHD-0000-0000"
    var keyInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTransferBtn by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (licenseStatus.isProActivated) "Pro License Active" else "Unlock DD-7X Full Version",
                style = PedalTypography.headingSheet,
                color = colors.textPrimary
            )

            // Device ID Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRecessed)
                    .padding(12.dp)
            ) {
                Text("YOUR DEVICE ID", style = PedalTypography.knobLegend, color = colors.textSecondary)
                Text(deviceId, style = PedalTypography.monoReadout, color = colors.textPrimary)
            }

            if (licenseStatus.isProActivated) {
                Text(
                    "Your phone is permanently unlocked for unlimited offline exports and custom presets.",
                    style = PedalTypography.bodyPrimary,
                    color = colors.ledTempoGreen
                )
            } else {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { raw ->
                        keyInput = if (raw.contains(".")) raw.trim() else formatLicenseKey(raw)
                        errorMessage = null
                    },
                    label = { Text("Activation Key (DD7X-XXXX-XXXX) or Offline Token") },
                    singleLine = true,
                    textStyle = PedalTypography.monoReadout,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Text(it, style = PedalTypography.captionMeta, color = colors.ledActiveRed)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            errorMessage = null
                            when (val res = licenseRepo.activate(keyInput)) {
                                is ActivationApiResult.Activated -> {
                                    Toast.makeText(context, "Pro Version Unlocked!", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                is ActivationApiResult.FriendlyError -> {
                                    errorMessage = res.message
                                    showTransferBtn = res.showTransferSupport
                                }
                            }
                            isSubmitting = false
                        }
                    },
                    enabled = !isSubmitting && keyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCobalt),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isSubmitting) "Verifying..." else "Activate Key")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                            Toast.makeText(context, "Device ID copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy ID")
                    }
                    OutlinedButton(
                        onClick = {
                            val msg = if (showTransferBtn) {
                                "Assalamu Alaikum! I changed my phone and need a license transfer. Key: $keyInput | New Device ID: $deviceId"
                            } else {
                                "Assalamu Alaikum! I want to buy the Full Activation Key for DD-7X Vocal Delay. My Device ID is: $deviceId"
                            }
                            val url = "https://wa.me/${BuildConfig.FOUNDER_WHATSAPP_NUMBER}?text=${Uri.encode(msg)}"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }.onFailure {
                                Toast.makeText(context, "Device ID: $deviceId", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (showTransferBtn) "Transfer on WhatsApp" else "Get Key on WhatsApp")
                    }
                }
            }
        }
    }
}

private fun formatLicenseKey(raw: String): String {
    val clean = raw.uppercase().filter { it.isLetterOrDigit() }.take(12)
    return clean.chunked(4).joinToString("-")
}
