package com.example.model

import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

data class BrowserTab(
    val tabId: String = UUID.randomUUID().toString(),
    val url: String = VortexUrls.HOME,
    val title: String = "",
    val incognitoMode: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false,
    val blockedTrackersCount: Int = 0,
    val reloadNonce: Int = 0
)

enum class SuggestionSource {
    BOOKMARK,
    HISTORY,
    WIKIPEDIA
}

data class SearchSuggestion(
    val text: String,
    val url: String? = null,
    val source: SuggestionSource
)

data class WikiSearchResult(
    val pageId: Long,
    val title: String,
    val snippet: String,
    val url: String,
    val thumbnailUrl: String? = null
)

sealed interface NativeSearchState {
    data object Idle : NativeSearchState
    data object Loading : NativeSearchState
    data class Success(
        val query: String,
        val results: List<WikiSearchResult>
    ) : NativeSearchState
    data class Error(val query: String, val message: String) : NativeSearchState
}

enum class DownloadStatus {
    PENDING,
    RUNNING,
    SUCCESSFUL,
    FAILED
}

data class DownloadItem(
    val downloadId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val status: DownloadStatus,
    val progressPercent: Int,
    val localUri: String? = null
)
