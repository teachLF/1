package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudSyncRepository
import com.example.data.DownloadBridge
import com.example.data.IndependentSearchRepository
import com.example.data.VortexAiRepository
import com.example.model.AiUiState
import com.example.model.Bookmark
import com.example.model.BrowserTab
import com.example.model.DownloadItem
import com.example.model.HistoryEntry
import com.example.model.IndependentEngine
import com.example.model.NativeSearchState
import com.example.model.SearchSuggestion
import com.example.model.SuggestionSource
import com.example.model.UiState
import com.example.model.UserPreferences
import com.example.model.VortexUrls
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val cloudRepo: CloudSyncRepository,
    private val searchRepo: IndependentSearchRepository,
    private val aiRepo: VortexAiRepository,
    private val downloadBridge: DownloadBridge,
    val currentUserId: String,
    initialExternalUrl: String? = null
) : ViewModel() {

    private val initialTab = BrowserTab(
        url = initialExternalUrl?.takeIf { it.startsWith("http") } ?: VortexUrls.HOME,
        title = ""
    )

    private val _tabs = MutableStateFlow(listOf(initialTab))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(initialTab.tabId)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _totalBlockedSession = MutableStateFlow(0)
    val totalBlockedSession: StateFlow<Int> = _totalBlockedSession.asStateFlow()

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

    private val _nativeSearchState = MutableStateFlow<NativeSearchState>(NativeSearchState.Idle)
    val nativeSearchState: StateFlow<NativeSearchState> = _nativeSearchState.asStateFlow()

    private val _aiSearchState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiSearchState: StateFlow<AiUiState> = _aiSearchState.asStateFlow()

    private val _aiSummaryState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiSummaryState: StateFlow<AiUiState> = _aiSummaryState.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var suggestionJob: Job? = null
    private var nativeSearchJob: Job? = null
    private var aiSearchJob: Job? = null
    private var aiSummaryJob: Job? = null
    private var lastRecordedHistoryUrl: String = ""

    val bookmarksState: StateFlow<UiState<List<Bookmark>>> = cloudRepo.observeBookmarks(currentUserId)
        .map<List<Bookmark>, UiState<List<Bookmark>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing bookmarks", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to sync bookmarks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val historyState: StateFlow<UiState<List<HistoryEntry>>> = cloudRepo.observeHistory(currentUserId)
        .map<List<HistoryEntry>, UiState<List<HistoryEntry>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing history", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to sync history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val preferencesState: StateFlow<UserPreferences> = cloudRepo.observePreferences(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing preferences", firestoreError ?: error)
            emit(UserPreferences(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserPreferences(userId = currentUserId)
        )

    fun activeTab(): BrowserTab {
        val current = _tabs.value
        return current.firstOrNull { it.tabId == _activeTabId.value } ?: current.first()
    }

    fun submitOmnibox(rawInput: String, engineOverride: IndependentEngine? = null) {
        val prefs = preferencesState.value
        val engine = engineOverride ?: prefs.engineEnum()
        val resolvedUrl = VortexUrls.resolveOmniboxInput(rawInput, engine)
        _suggestions.value = emptyList()
        navigateActiveTab(resolvedUrl)
    }

    fun navigateActiveTab(targetUrl: String) {
        val currentId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.tabId == currentId) {
                    tab.copy(
                        url = targetUrl,
                        loading = !targetUrl.startsWith("vortex://"),
                        progress = if (targetUrl.startsWith("vortex://")) 100 else 10,
                        reloadNonce = tab.reloadNonce + 1
                    )
                } else tab
            }
        }
        if (VortexUrls.isNativeSearch(targetUrl)) {
            val query = VortexUrls.extractNativeSearchQuery(targetUrl)
            triggerNativeSearch(query)
        }
    }

    fun goHomeInActiveTab() {
        val currentId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.tabId == currentId) {
                    tab.copy(
                        url = VortexUrls.HOME,
                        title = "",
                        loading = false,
                        progress = 0
                    )
                } else tab
            }
        }
    }

    fun openUrlInNewTab(url: String, incognito: Boolean = false) {
        val prefs = preferencesState.value
        val newTab = BrowserTab(
            url = url,
            incognitoMode = incognito,
            desktopMode = prefs.desktopModeDefault,
            loading = !url.startsWith("vortex://")
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.tabId
        if (VortexUrls.isNativeSearch(url)) {
            triggerNativeSearch(VortexUrls.extractNativeSearchQuery(url))
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
        val selected = _tabs.value.firstOrNull { it.tabId == tabId }
        if (selected != null && VortexUrls.isNativeSearch(selected.url)) {
            val q = VortexUrls.extractNativeSearchQuery(selected.url)
            val currentQuery = (_nativeSearchState.value as? NativeSearchState.Success)?.query
            if (currentQuery != q) {
                triggerNativeSearch(q)
            }
        }
    }

    fun closeTab(tabId: String, onTabDisposed: (BrowserTab) -> Unit = {}) {
        val currentList = _tabs.value
        val closing = currentList.firstOrNull { it.tabId == tabId } ?: return
        onTabDisposed(closing)
        val remaining = currentList.filterNot { it.tabId == tabId }
        if (remaining.isEmpty()) {
            val fresh = BrowserTab(desktopMode = preferencesState.value.desktopModeDefault)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.tabId
        } else {
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                _activeTabId.value = remaining.last().tabId
            }
        }
    }

    fun closeAllIncognitoTabs(onTabDisposed: (BrowserTab) -> Unit) {
        val incognitoTabs = _tabs.value.filter { it.incognitoMode }
        incognitoTabs.forEach(onTabDisposed)
        val remaining = _tabs.value.filterNot { it.incognitoMode }
        if (remaining.isEmpty()) {
            val fresh = BrowserTab(desktopMode = preferencesState.value.desktopModeDefault)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.tabId
        } else {
            _tabs.value = remaining
            if (remaining.none { it.tabId == _activeTabId.value }) {
                _activeTabId.value = remaining.last().tabId
            }
        }
    }

    fun updateTabWebState(
        tabId: String,
        url: String? = null,
        title: String? = null,
        loading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.tabId == tabId) {
                    tab.copy(
                        url = url ?: tab.url,
                        title = title ?: tab.title,
                        loading = loading ?: tab.loading,
                        progress = progress ?: tab.progress,
                        canGoBack = canGoBack ?: tab.canGoBack,
                        canGoForward = canGoForward ?: tab.canGoForward
                    )
                } else tab
            }
        }
    }

    fun onPageCommitted(tabId: String, title: String, url: String) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        if (tab.incognitoMode || url.startsWith("vortex://") || url == "about:blank") return
        if (url == lastRecordedHistoryUrl) return
        lastRecordedHistoryUrl = url
        viewModelScope.launch {
            cloudRepo.recordHistoryVisit(currentUserId, title, url)
        }
    }

    fun onTrackerBlocked(tabId: String) {
        _totalBlockedSession.update { it + 1 }
        _tabs.update { list ->
            list.map { tab ->
                if (tab.tabId == tabId) tab.copy(blockedTrackersCount = tab.blockedTrackersCount + 1)
                else tab
            }
        }
    }

    fun toggleDesktopModeForActiveTab() {
        val currentId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.tabId == currentId) {
                    tab.copy(
                        desktopMode = !tab.desktopMode,
                        reloadNonce = tab.reloadNonce + 1
                    )
                } else tab
            }
        }
    }

    fun onOmniboxQueryChanged(query: String) {
        suggestionJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("vortex://")) {
            _suggestions.value = emptyList()
            return
        }

        val localMatches = buildList {
            val bookmarks = (bookmarksState.value as? UiState.Success)?.data.orEmpty()
            bookmarks.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.url.contains(trimmed, ignoreCase = true)
            }.take(3).forEach { bm ->
                add(SearchSuggestion(bm.title, bm.url, SuggestionSource.BOOKMARK))
            }

            val history = (historyState.value as? UiState.Success)?.data.orEmpty()
            history.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.url.contains(trimmed, ignoreCase = true)
            }.distinctBy { it.url }.take(3).forEach { entry ->
                if (none { it.url == entry.url }) {
                    add(SearchSuggestion(entry.title, entry.url, SuggestionSource.HISTORY))
                }
            }
        }
        _suggestions.value = localMatches

        if (trimmed.length >= 2 && !trimmed.startsWith("http")) {
            suggestionJob = viewModelScope.launch {
                delay(220L)
                val wiki = searchRepo.fetchSuggestions(trimmed)
                val merged = (localMatches + wiki).distinctBy { it.text.lowercase() }.take(7)
                _suggestions.value = merged
            }
        }
    }

    fun clearSuggestions() {
        suggestionJob?.cancel()
        _suggestions.value = emptyList()
    }

    fun triggerNativeSearch(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return

        nativeSearchJob?.cancel()
        aiSearchJob?.cancel()

        nativeSearchJob = viewModelScope.launch {
            _nativeSearchState.value = NativeSearchState.Loading
            searchRepo.searchIndependentKnowledge(cleanQuery)
                .onSuccess { results ->
                    _nativeSearchState.value = NativeSearchState.Success(cleanQuery, results)
                }
                .onFailure { error ->
                    _nativeSearchState.value = NativeSearchState.Error(
                        cleanQuery,
                        error.localizedMessage ?: "Search request failed"
                    )
                }
        }

        if (preferencesState.value.aiSearchEnabled) {
            aiSearchJob = viewModelScope.launch {
                _aiSearchState.value = AiUiState.Loading
                try {
                    var latest = ""
                    aiRepo.streamSmartSearchBrief(cleanQuery).collect { text ->
                        latest = text
                        _aiSearchState.value = AiUiState.Streaming(text)
                    }
                    _aiSearchState.value = AiUiState.Success(latest)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "AI smart search timed out", e)
                    _aiSearchState.value = AiUiState.Error("AI search brief timed out after 60s.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "AI smart search failed", e)
                    _aiSearchState.value = AiUiState.Error(
                        e.localizedMessage ?: "AI smart brief failed."
                    )
                }
            }
        } else {
            _aiSearchState.value = AiUiState.Idle
        }
    }

    fun summarizePageContent(title: String, url: String, extractedText: String) {
        aiSummaryJob?.cancel()
        if (extractedText.isBlank()) {
            _aiSummaryState.value = AiUiState.Error("Could not extract readable text from this page.")
            return
        }
        aiSummaryJob = viewModelScope.launch {
            _aiSummaryState.value = AiUiState.Loading
            try {
                var latest = ""
                aiRepo.streamPageSummary(title, url, extractedText).collect { text ->
                    latest = text
                    _aiSummaryState.value = AiUiState.Streaming(text)
                }
                _aiSummaryState.value = AiUiState.Success(latest)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI page summary timed out", e)
                _aiSummaryState.value = AiUiState.Error("Page summary timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI page summary failed", e)
                _aiSummaryState.value = AiUiState.Error(
                    e.localizedMessage ?: "Failed to summarize page."
                )
            }
        }
    }

    fun askAboutPage(title: String, extractedText: String, question: String) {
        if (question.isBlank()) return
        aiSummaryJob?.cancel()
        aiSummaryJob = viewModelScope.launch {
            _aiSummaryState.value = AiUiState.Loading
            try {
                var latest = ""
                aiRepo.streamPageQuestion(title, extractedText, question).collect { text ->
                    latest = text
                    _aiSummaryState.value = AiUiState.Streaming(text)
                }
                _aiSummaryState.value = AiUiState.Success(latest)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI page Q&A timed out", e)
                _aiSummaryState.value = AiUiState.Error("Question timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI page Q&A failed", e)
                _aiSummaryState.value = AiUiState.Error(
                    e.localizedMessage ?: "Failed to answer question."
                )
            }
        }
    }

    fun resetAiSummary() {
        aiSummaryJob?.cancel()
        _aiSummaryState.value = AiUiState.Idle
    }

    fun toggleBookmark(title: String, url: String, onMessage: (String) -> Unit, savedMsg: String, removedMsg: String) {
        if (url.startsWith("vortex://") || url.isBlank()) return
        val currentBookmarks = (bookmarksState.value as? UiState.Success)?.data.orEmpty()
        val existing = currentBookmarks.firstOrNull { it.url == url }
        viewModelScope.launch {
            if (existing != null) {
                cloudRepo.deleteBookmark(currentUserId, existing.id)
                    .onSuccess { onMessage(removedMsg) }
                    .onFailure { _statusMessage.value = it.localizedMessage }
            } else {
                cloudRepo.addBookmark(currentUserId, title, url)
                    .onSuccess { onMessage(savedMsg) }
                    .onFailure { _statusMessage.value = it.localizedMessage }
            }
        }
    }

    fun deleteBookmarkById(bookmarkId: String) {
        viewModelScope.launch {
            cloudRepo.deleteBookmark(currentUserId, bookmarkId)
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun deleteHistoryEntryById(entryId: String) {
        viewModelScope.launch {
            cloudRepo.deleteHistoryEntry(currentUserId, entryId)
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun clearAllHistory(clearedMsg: String) {
        viewModelScope.launch {
            cloudRepo.clearHistory(currentUserId)
                .onSuccess { _statusMessage.value = clearedMsg }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        val updated = transform(preferencesState.value).copy(userId = currentUserId)
        viewModelScope.launch {
            cloudRepo.savePreferences(currentUserId, updated)
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        startedMsgTemplate: String
    ) {
        downloadBridge.enqueueDownload(url, userAgent, contentDisposition, mimeType)
            .onSuccess { item ->
                _downloads.update { listOf(item) + it }
                _statusMessage.value = String.format(startedMsgTemplate, item.fileName)
            }
            .onFailure { error ->
                _statusMessage.value = error.localizedMessage ?: "Download failed"
            }
    }

    fun refreshDownloads() {
        _downloads.update { current -> downloadBridge.refreshStatuses(current) }
    }

    fun openDownload(item: DownloadItem) {
        downloadBridge.openDownloadedItem(item)
    }

    fun openSystemDownloads() {
        downloadBridge.openSystemDownloads()
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    private companion object {
        const val TAG = "BrowserVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
