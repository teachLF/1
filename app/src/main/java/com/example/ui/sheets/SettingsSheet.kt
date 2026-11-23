package com.example.ui.sheets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.IndependentEngine
import com.example.model.UserPreferences
import com.example.ui.theme.LocalVortexPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    preferences: UserPreferences,
    userEmail: String?,
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVortexPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedEngine = preferences.engineEnum()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.glassSurface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.settings_search_section),
                style = MaterialTheme.typography.titleSmall,
                color = palette.cyan
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.subtleOutline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = palette.elevatedSurface
            ) {
                Column {
                    IndependentEngine.entries.forEachIndexed { index, engine ->
                        val labelRes = when (engine) {
                            IndependentEngine.VORTEX_NATIVE -> R.string.engine_vortex_native
                            IndependentEngine.BRAVE -> R.string.engine_brave
                            IndependentEngine.MOJEEK -> R.string.engine_mojeek
                            IndependentEngine.WIKIPEDIA -> R.string.engine_wikipedia
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdatePreferences { it.copy(defaultEngine = engine.name) }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEngine == engine,
                                onClick = {
                                    onUpdatePreferences { it.copy(defaultEngine = engine.name) }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = palette.cyan)
                            )
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (index < IndependentEngine.entries.lastIndex) {
                            HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_privacy_section),
                style = MaterialTheme.typography.titleSmall,
                color = palette.cyan
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.subtleOutline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = palette.elevatedSurface
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_adblock_title),
                        subtitle = stringResource(R.string.settings_adblock_desc),
                        checked = preferences.adBlockEnabled,
                        testTag = "toggle_adblock",
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(adBlockEnabled = enabled) }
                        }
                    )
                    HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_ai_title),
                        subtitle = stringResource(R.string.settings_ai_desc),
                        checked = preferences.aiSearchEnabled,
                        testTag = "toggle_ai_search",
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(aiSearchEnabled = enabled) }
                        }
                    )
                    HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_js_title),
                        subtitle = stringResource(R.string.settings_js_desc),
                        checked = preferences.javascriptEnabled,
                        testTag = "toggle_javascript",
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(javascriptEnabled = enabled) }
                        }
                    )
                    HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_desktop_title),
                        subtitle = stringResource(R.string.settings_desktop_desc),
                        checked = preferences.desktopModeDefault,
                        testTag = "toggle_desktop_default",
                        onCheckedChange = { enabled ->
                            onUpdatePreferences { it.copy(desktopModeDefault = enabled) }
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_account_section),
                style = MaterialTheme.typography.titleSmall,
                color = palette.cyan
            )

            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.settings_signed_in_as, userEmail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_sign_out),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalVortexPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = palette.cyan
            )
        )
    }
}
