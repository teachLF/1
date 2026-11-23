package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.Bookmark
import com.example.model.IndependentEngine
import com.example.model.UiState
import com.example.ui.theme.LocalVortexPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VortexStartPage(
    incognito: Boolean,
    activeEngine: IndependentEngine,
    adBlockEnabled: Boolean,
    totalBlockedSession: Int,
    bookmarksState: UiState<List<Bookmark>>,
    onEngineSelected: (IndependentEngine) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val palette = LocalVortexPalette.current
    var heroQuery by remember { mutableStateOf("") }
    val bgColor = if (incognito) palette.incognitoCanvas else MaterialTheme.colorScheme.background
    val cardColor = if (incognito) palette.incognitoSurface else palette.glassSurface
    val accent = if (incognito) palette.phantomMagenta else palette.cyan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.16f),
                            palette.violet.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.18f),
                        radius = size.maxDimension * 0.52f
                    )
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (incognito) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.phantomMagenta.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(
                            1.dp,
                            palette.phantomMagenta.copy(alpha = 0.45f),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = stringResource(R.string.cd_incognito_badge),
                            tint = palette.phantomMagenta
                        )
                        Text(
                            text = stringResource(R.string.phantom_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            VortexLogo(size = 96.dp, incognito = incognito)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (incognito) {
                    stringResource(R.string.phantom_tab_title).uppercase()
                } else {
                    stringResource(R.string.app_name).uppercase()
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.vortex_start_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Hero Search Bar
            OutlinedTextField(
                value = heroQuery,
                onValueChange = { heroQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_search_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_or_enter_url),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = accent
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (heroQuery.isNotBlank()) {
                            onSearchSubmitted(heroQuery)
                            heroQuery = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = palette.subtleOutline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Independent Search Engine Switcher (Zero Google / Zero Bing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IndependentEngine.entries.forEach { engine ->
                    val selected = engine == activeEngine
                    val labelRes = when (engine) {
                        IndependentEngine.VORTEX_NATIVE -> R.string.engine_vortex_native
                        IndependentEngine.BRAVE -> R.string.engine_brave
                        IndependentEngine.MOJEEK -> R.string.engine_mojeek
                        IndependentEngine.WIKIPEDIA -> R.string.engine_wikipedia
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onEngineSelected(engine) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.testTag("engine_chip_${engine.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent,
                            containerColor = cardColor,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = accent,
                            borderColor = palette.subtleOutline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // VortexShield Telemetry Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.subtleOutline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = cardColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    (if (adBlockEnabled) palette.shieldGreen else palette.amberPulse)
                                        .copy(alpha = 0.16f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = stringResource(R.string.cd_shield_status),
                                tint = if (adBlockEnabled) palette.shieldGreen else palette.amberPulse
                            )
                        }
                        Column {
                            Text(
                                text = if (adBlockEnabled) {
                                    stringResource(R.string.shield_active)
                                } else {
                                    stringResource(R.string.shield_paused)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.shield_blocked_session),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = totalBlockedSession.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (adBlockEnabled) palette.shieldGreen else palette.amberPulse
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Speed Dial Bookmarks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.speed_dial_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val bookmarks = (bookmarksState as? UiState.Success)?.data.orEmpty()
            if (bookmarks.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.subtleOutline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.speed_dial_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 4
                ) {
                    bookmarks.take(8).forEach { bookmark ->
                        SpeedDialTile(
                            title = bookmark.title,
                            domain = bookmark.domain,
                            accent = accent,
                            surfaceColor = cardColor,
                            outlineColor = palette.subtleOutline,
                            onClick = { onOpenUrl(bookmark.url) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Independent Web Portals (Brave, Mojeek, Wikipedia)
            Text(
                text = stringResource(R.string.discover_independent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IndependentPortalTile(
                    title = "Brave Search",
                    subtitle = "Independent",
                    color = cardColor,
                    accent = palette.cyan,
                    outline = palette.subtleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenUrl("https://search.brave.com") }
                )
                IndependentPortalTile(
                    title = "Mojeek",
                    subtitle = "Own Crawler",
                    color = cardColor,
                    accent = palette.brightViolet,
                    outline = palette.subtleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenUrl("https://www.mojeek.com") }
                )
                IndependentPortalTile(
                    title = "Wikipedia",
                    subtitle = "Knowledge",
                    color = cardColor,
                    accent = palette.shieldGreen,
                    outline = palette.subtleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenUrl("https://www.wikipedia.org") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SpeedDialTile(
    title: String,
    domain: String,
    accent: Color,
    surfaceColor: Color,
    outlineColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(surfaceColor, RoundedCornerShape(16.dp))
                .border(1.dp, outlineColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title.ifBlank { domain },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IndependentPortalTile(
    title: String,
    subtitle: String,
    color: Color,
    accent: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
