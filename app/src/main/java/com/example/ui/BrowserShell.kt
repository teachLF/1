package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.signOutUser
import com.example.model.SuggestionSource
import com.example.model.UiState
import com.example.model.VortexUrls
import com.example.ui.components.VortexNativeSearchView
import com.example.ui.components.VortexStartPage
import com.example.ui.components.VortexWebViewport
import com.example.ui.components.WebViewPool
import com.example.ui.sheets.AiSummarySheet
import com.example.ui.sheets.DownloadsSheet
import com.example.ui.sheets.LibrarySheet
import com.example.ui.sheets.SettingsSheet
import com.example.ui.sheets.TabSwitcherSheet
import com.example.ui.theme.LocalVortexPalette
import kotlinx.coroutines.launch

private enum class ActiveSheet {
    NONE,
    TABS,
    LIBRARY,
    DOWNLOADS,
    AI_SUMMARY,
    SETTINGS
}

@Composable
fun BrowserShell(
    viewModel: BrowserViewModel,
    userEmail: String?
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val palette = LocalVortexPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webViewPool = remember { WebViewPool() }

    DisposableEffect(viewModel.currentUserId) {
        onDispose { webViewPool.disposeAll() }
    }

    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab = tabs.firstOrNull { it.tabId == activeTabId } ?: tabs.first()

    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val nativeSearchState by viewModel.nativeSearchState.collectAsStateWithLifecycle()
    val aiSearchState by viewModel.aiSearchState.collectAsStateWithLifecycle()
    val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val totalBlockedSession by viewModel.totalBlockedSession.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var menuExpanded by remember { mutableStateOf(false) }
    var omniboxFocused by remember { mutableStateOf(false) }
    var omniboxText by remember(activeTab.tabId, activeTab.url) {
        mutableStateOf(
            when {
                activeTab.url == VortexUrls.HOME -> ""
                VortexUrls.isNativeSearch(activeTab.url) ->
                    VortexUrls.extractNativeSearchQuery(activeTab.url)
                else -> activeTab.url
            }
        )
    }

    var findBarVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var lastExtractedPageText by remember { mutableStateOf("") }

    val savedBookmarkMsg = stringResource(R.string.bookmark_saved)
    val removedBookmarkMsg = stringResource(R.string.bookmark_removed)
    val historyClearedMsg = stringResource(R.string.history_cleared)
    val downloadStartedTpl = stringResource(R.string.downloads_started)

    val currentBookmarks = (bookmarksState as? UiState.Success)?.data.orEmpty()
    val isCurrentPageBookmarked = currentBookmarks.any { it.url == activeTab.url }
    val isWebPage = !activeTab.url.startsWith("vortex://")

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    BackHandler(
        enabled = findBarVisible ||
            activeSheet != ActiveSheet.NONE ||
            omniboxFocused ||
            isWebPage ||
            VortexUrls.isNativeSearch(activeTab.url)
    ) {
        when {
            findBarVisible -> {
                findBarVisible = false
                findQuery = ""
                webViewPool.clearMatches(activeTab.tabId)
            }
            activeSheet != ActiveSheet.NONE -> activeSheet = ActiveSheet.NONE
            omniboxFocused -> {
                omniboxFocused = false
                viewModel.clearSuggestions()
                focusManager.clearFocus()
            }
            isWebPage && webViewPool.goBack(activeTab.tabId) -> Unit
            isWebPage || VortexUrls.isNativeSearch(activeTab.url) -> {
                viewModel.goHomeInActiveTab()
            }
        }
    }

    val chromeBg = if (activeTab.incognitoMode) palette.incognitoSurface else palette.glassSurface
    val accent = if (activeTab.incognitoMode) palette.phantomMagenta else palette.cyan

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Top Vortex Omnibox Bar
            Surface(
                color = chromeBg,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (omniboxFocused) accent else palette.subtleOutline,
                                    shape = RoundedCornerShape(18.dp)
                                ),
                            shape = RoundedCornerShape(18.dp),
                            color = if (activeTab.incognitoMode) {
                                palette.incognitoCanvas
                            } else {
                                MaterialTheme.colorScheme.background
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        activeTab.incognitoMode -> Icons.Rounded.VisibilityOff
                                        activeTab.url.startsWith("https://") -> Icons.Rounded.Lock
                                        else -> Icons.Rounded.Search
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        activeTab.incognitoMode -> palette.phantomMagenta
                                        activeTab.url.startsWith("https://") -> palette.shieldGreen
                                        else -> accent
                                    },
                                    modifier = Modifier.size(18.dp)
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    if (omniboxText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_or_enter_url),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = omniboxText,
                                        onValueChange = { updated ->
                                            omniboxText = updated
                                            viewModel.onOmniboxQueryChanged(updated)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { state ->
                                                omniboxFocused = state.isFocused
                                                if (!state.isFocused) {
                                                    viewModel.clearSuggestions()
                                                }
                                            }
                                            .testTag("omnibox_input"),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                        keyboardActions = KeyboardActions(
                                            onGo = {
                                                focusManager.clearFocus()
                                                omniboxFocused = false
                                                viewModel.submitOmnibox(omniboxText)
                                            }
                                        )
                                    )
                                }

                                if (activeTab.blockedTrackersCount > 0 && isWebPage && !omniboxFocused) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                palette.shieldGreen.copy(alpha = 0.16f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Shield,
                                                contentDescription = stringResource(R.string.cd_shield_status),
                                                tint = palette.shieldGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = activeTab.blockedTrackersCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.shieldGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (omniboxFocused && omniboxText.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            omniboxText = ""
                                            viewModel.clearSuggestions()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("omnibox_clear_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_clear),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else if (isWebPage) {
                                    IconButton(
                                        onClick = {
                                            if (activeTab.loading) {
                                                webViewPool.stopLoading(activeTab.tabId)
                                            } else {
                                                webViewPool.reload(activeTab.tabId)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("reload_stop_button")
                                    ) {
                                        Icon(
                                            imageVector = if (activeTab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                            contentDescription = stringResource(
                                                if (activeTab.loading) R.string.cd_stop else R.string.cd_reload
                                            ),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Find in Page Bar
                    AnimatedVisibility(visible = findBarVisible && isWebPage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .border(1.dp, palette.subtleOutline, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (findQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.find_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BasicTextField(
                                        value = findQuery,
                                        onValueChange = { q ->
                                            findQuery = q
                                            webViewPool.findAll(activeTab.tabId, q)
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(accent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("find_in_page_input")
                                    )
                                }
                            }
                            IconButton(
                                onClick = { webViewPool.findNext(activeTab.tabId, false) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.find_prev))
                            }
                            IconButton(
                                onClick = { webViewPool.findNext(activeTab.tabId, true) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.find_next))
                            }
                            IconButton(
                                onClick = {
                                    findBarVisible = false
                                    findQuery = ""
                                    webViewPool.clearMatches(activeTab.tabId)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close))
                            }
                        }
                    }

                    if (activeTab.loading && isWebPage) {
                        LinearProgressIndicator(
                            progress = { (activeTab.progress.coerceIn(5, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = accent,
                            trackColor = palette.subtleOutline.copy(alpha = 0.3f)
                        )
                    } else {
                        HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                    }
                }
            }

            // Main Content Viewport + Autocomplete Overlay
            Box(modifier = Modifier.weight(1f)) {
                when {
                    activeTab.url == VortexUrls.HOME -> {
                        VortexStartPage(
                            incognito = activeTab.incognitoMode,
                            activeEngine = preferences.engineEnum(),
                            adBlockEnabled = preferences.adBlockEnabled,
                            totalBlockedSession = totalBlockedSession,
                            bookmarksState = bookmarksState,
                            onEngineSelected = { engine ->
                                viewModel.updatePreferences { it.copy(defaultEngine = engine.name) }
                            },
                            onSearchSubmitted = { query ->
                                focusManager.clearFocus()
                                viewModel.submitOmnibox(query)
                            },
                            onOpenUrl = { url ->
                                viewModel.navigateActiveTab(url)
                            }
                        )
                    }
                    VortexUrls.isNativeSearch(activeTab.url) -> {
                        val searchQuery = VortexUrls.extractNativeSearchQuery(activeTab.url)
                        VortexNativeSearchView(
                            query = searchQuery,
                            nativeSearchState = nativeSearchState,
                            aiSearchState = aiSearchState,
                            aiSearchEnabled = preferences.aiSearchEnabled,
                            incognito = activeTab.incognitoMode,
                            onRetrySearch = { q -> viewModel.triggerNativeSearch(q) },
                            onOpenResultUrl = { url -> viewModel.navigateActiveTab(url) }
                        )
                    }
                    else -> {
                        VortexWebViewport(
                            tab = activeTab,
                            preferences = preferences,
                            webViewPool = webViewPool,
                            onStateUpdate = viewModel::updateTabWebState,
                            onPageCommitted = viewModel::onPageCommitted,
                            onTrackerBlocked = viewModel::onTrackerBlocked,
                            onDownloadRequest = { url, ua, disp, mime ->
                                viewModel.startDownload(url, ua, disp, mime, downloadStartedTpl)
                            }
                        )
                    }
                }

                // Privacy-Safe Autocomplete Dropdown (Bookmarks, History, Wikimedia)
                if (omniboxFocused && suggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .border(1.dp, palette.subtleOutline, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = palette.glassSurface,
                        tonalElevation = 8.dp
                    ) {
                        LazyColumn {
                            items(suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            focusManager.clearFocus()
                                            omniboxFocused = false
                                            if (suggestion.source == SuggestionSource.WIKIPEDIA) {
                                                viewModel.submitOmnibox(suggestion.text)
                                            } else {
                                                viewModel.navigateActiveTab(
                                                    suggestion.url ?: VortexUrls.buildNativeSearchUrl(suggestion.text)
                                                )
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = when (suggestion.source) {
                                            SuggestionSource.BOOKMARK -> Icons.Rounded.Bookmark
                                            SuggestionSource.HISTORY -> Icons.Rounded.History
                                            SuggestionSource.WIKIPEDIA -> Icons.Rounded.Public
                                        },
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!suggestion.url.isNullOrBlank() && suggestion.source != SuggestionSource.WIKIPEDIA) {
                                            Text(
                                                text = suggestion.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Thumb-Friendly Bottom Browser Bar
            Surface(
                color = chromeBg,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isWebPage) {
                                    if (!webViewPool.goBack(activeTab.tabId)) {
                                        viewModel.goHomeInActiveTab()
                                    }
                                } else if (VortexUrls.isNativeSearch(activeTab.url)) {
                                    viewModel.goHomeInActiveTab()
                                }
                            },
                            enabled = isWebPage || VortexUrls.isNativeSearch(activeTab.url),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }

                        IconButton(
                            onClick = { webViewPool.goForward(activeTab.tabId) },
                            enabled = isWebPage && activeTab.canGoForward,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = stringResource(R.string.cd_forward)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.goHomeInActiveTab() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = stringResource(R.string.cd_home),
                                tint = if (activeTab.url == VortexUrls.HOME) accent else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isWebPage) {
                                    viewModel.resetAiSummary()
                                    activeSheet = ActiveSheet.AI_SUMMARY
                                    webViewPool.extractReadableText(activeTab.tabId) { text ->
                                        lastExtractedPageText = text
                                        viewModel.summarizePageContent(
                                            title = activeTab.title.ifBlank { activeTab.url },
                                            url = activeTab.url,
                                            extractedText = text
                                        )
                                    }
                                } else {
                                    activeSheet = ActiveSheet.AI_SUMMARY
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("nav_ai_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.cd_ai_summary),
                                tint = palette.brightViolet
                            )
                        }

                        // Tab Count Pill
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { activeSheet = ActiveSheet.TABS }
                                .testTag("nav_tabs_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .border(1.8.dp, accent, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabs.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }

                        // Overflow Menu
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("nav_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_menu)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = palette.elevatedSurface
                            ) {
                                if (isWebPage) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (isCurrentPageBookmarked) {
                                                        R.string.menu_bookmark_remove
                                                    } else {
                                                        R.string.menu_bookmark_add
                                                    }
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isCurrentPageBookmarked) {
                                                    Icons.Rounded.Bookmark
                                                } else {
                                                    Icons.Rounded.BookmarkBorder
                                                },
                                                contentDescription = null,
                                                tint = accent
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.toggleBookmark(
                                                title = activeTab.title,
                                                url = activeTab.url,
                                                onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                                savedMsg = savedBookmarkMsg,
                                                removedMsg = removedBookmarkMsg
                                            )
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_new_tab)) },
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.openUrlInNewTab(VortexUrls.HOME, incognito = false)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_new_incognito)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.VisibilityOff,
                                            contentDescription = null,
                                            tint = palette.phantomMagenta
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.openUrlInNewTab(VortexUrls.HOME, incognito = true)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_bookmarks_history)) },
                                    leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        activeSheet = ActiveSheet.LIBRARY
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_downloads)) },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        activeSheet = ActiveSheet.DOWNLOADS
                                    }
                                )

                                if (isWebPage) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_find_in_page)) },
                                        leadingIcon = { Icon(Icons.Rounded.FindInPage, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            findBarVisible = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            val suffix = if (activeTab.desktopMode) " ✓" else ""
                                            Text("${stringResource(R.string.menu_desktop_site)}$suffix")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.DesktopWindows,
                                                contentDescription = null,
                                                tint = if (activeTab.desktopMode) accent else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.toggleDesktopModeForActiveTab()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_share)) },
                                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, null))
                                        }
                                    )
                                }

                                HorizontalDivider(color = palette.subtleOutline.copy(alpha = 0.5f))

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_settings)) },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        activeSheet = ActiveSheet.SETTINGS
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sign_out)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Logout,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        webViewPool.disposeAll()
                                        signOutUser(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (activeSheet) {
        ActiveSheet.NONE -> Unit
        ActiveSheet.TABS -> {
            TabSwitcherSheet(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = viewModel::selectTab,
                onCloseTab = { id ->
                    viewModel.closeTab(id) { disposed -> webViewPool.disposeTab(disposed) }
                },
                onNewNormalTab = { viewModel.openUrlInNewTab(VortexUrls.HOME, incognito = false) },
                onNewIncognitoTab = { viewModel.openUrlInNewTab(VortexUrls.HOME, incognito = true) },
                onCloseAllIncognito = {
                    viewModel.closeAllIncognitoTabs { disposed -> webViewPool.disposeTab(disposed) }
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.LIBRARY -> {
            LibrarySheet(
                bookmarksState = bookmarksState,
                historyState = historyState,
                onOpenUrl = viewModel::navigateActiveTab,
                onDeleteBookmark = viewModel::deleteBookmarkById,
                onDeleteHistoryEntry = viewModel::deleteHistoryEntryById,
                onClearHistory = { viewModel.clearAllHistory(historyClearedMsg) },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.DOWNLOADS -> {
            DownloadsSheet(
                downloads = downloads,
                onRefresh = viewModel::refreshDownloads,
                onOpenItem = viewModel::openDownload,
                onOpenSystemDownloads = viewModel::openSystemDownloads,
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.AI_SUMMARY -> {
            AiSummarySheet(
                pageTitle = activeTab.title.ifBlank { activeTab.url },
                hasWebPage = isWebPage,
                aiSummaryState = aiSummaryState,
                onAskFollowup = { question ->
                    viewModel.askAboutPage(
                        title = activeTab.title.ifBlank { activeTab.url },
                        extractedText = lastExtractedPageText,
                        question = question
                    )
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.SETTINGS -> {
            SettingsSheet(
                preferences = preferences,
                userEmail = userEmail,
                onUpdatePreferences = viewModel::updatePreferences,
                onSignOut = {
                    webViewPool.disposeAll()
                    signOutUser(credentialManager, scope)
                },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
    }
}
