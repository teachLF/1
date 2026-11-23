package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Bookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val domain: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val domain: String = "",
    @ServerTimestamp val visitedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val defaultEngine: String = IndependentEngine.VORTEX_NATIVE.name,
    val adBlockEnabled: Boolean = true,
    val aiSearchEnabled: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val desktopModeDefault: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun engineEnum(): IndependentEngine = IndependentEngine.fromRaw(defaultEngine)
}
