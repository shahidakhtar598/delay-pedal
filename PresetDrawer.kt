package com.naatdelay.dd7.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.data.local.entity.PresetEntity
import com.naatdelay.dd7.data.repository.SavePresetResult
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDrawer(
    presets: List<PresetEntity>,
    activePresetId: String?,
    onSelect: (PresetEntity) -> Unit,
    onDelete: (String) -> Unit,
    onSaveNew: suspend (String, Boolean) -> SavePresetResult,
    onDismiss: () -> Unit
) {
    val colors = LocalPedalColors.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }
    var pendingOverwrite by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Naat Vocal Presets", style = PedalTypography.headingSheet, color = colors.textPrimary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        if (it.length <= 28) {
                            newName = it
                            pendingOverwrite = false
                            feedbackMsg = null
                        }
                    },
                    placeholder = { Text("Save current knobs as...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        scope.launch {
                            when (val res = onSaveNew(newName, pendingOverwrite)) {
                                is SavePresetResult.Saved -> {
                                    newName = ""
                                    pendingOverwrite = false
                                    feedbackMsg = "Preset saved."
                                }
                                SavePresetResult.BlankNameError -> feedbackMsg = "Please enter a preset name."
                                SavePresetResult.FactoryNameConflict -> feedbackMsg = "Cannot overwrite a factory preset name."
                                is SavePresetResult.DuplicateCustomExists -> {
                                    pendingOverwrite = true
                                    feedbackMsg = "Preset exists — tap Save again to overwrite."
                                }
                                SavePresetResult.TrialLimitReached -> feedbackMsg = "Trial mode allows 1 custom preset. Activate Pro for unlimited presets."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCobalt),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(if (pendingOverwrite) "Overwrite" else "Save")
                }
            }

            feedbackMsg?.let {
                Text(it, style = PedalTypography.captionMeta, color = colors.statusWarning)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets, key = { it.id }) { p ->
                    val active = p.id == activePresetId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceCard)
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) colors.accentCobalt else colors.borderSubtle,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(p) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(p.name, style = PedalTypography.bodyPrimary, color = colors.textPrimary)
                                if (p.isFactory) {
                                    Text(
                                        text = "FACTORY",
                                        style = PedalTypography.knobLegend,
                                        color = colors.accentCobalt,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.accentCobalt.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val pp = if (p.isPingPong) " · PING-PONG" else ""
                            Text(
                                text = "${p.delayMode} · ${p.delayTimeMs.toInt()}ms · F.BACK ${(p.feedback * 100).toInt()}%$pp",
                                style = PedalTypography.monoReadout,
                                color = colors.textSecondary
                            )
                        }
                        if (!p.isFactory) {
                            IconButton(onClick = { onDelete(p.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.ledActiveRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
