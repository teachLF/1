package com.example.data

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.Bookmark
import com.example.model.HistoryEntry
import com.example.model.IndependentEngine
import com.example.model.UserPreferences
import com.example.model.VortexUrls
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    private fun requireUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUid) { "Signed-in user changed; refusing cross-user access." }
        return uid
    }

    fun observeBookmarks(userId: String): Flow<List<Bookmark>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/bookmarks"
        emitAll(
            db.collection("users").document(uid).collection("bookmarks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Bookmark::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeHistory(userId: String): Flow<List<HistoryEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/history"
        emitAll(
            db.collection("users").document(uid).collection("history")
                .orderBy("visitedAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        HistoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePreferences(userId: String): Flow<UserPreferences> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/settings/preferences"
        emitAll(
            db.collection("users").document(uid).collection("settings").document("preferences")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferences(userId = uid)
                    } else {
                        UserPreferences(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addBookmark(userId: String, title: String, url: String): Result<Unit> {
        var docPath = "users/$userId/bookmarks"
        return try {
            val uid = requireUserId(userId)
            val cleanUrl = url.trim().take(2048)
            require(cleanUrl.isNotEmpty()) { "Bookmark URL cannot be empty." }
            val cleanDomain = VortexUrls.extractDomain(cleanUrl).take(255).ifBlank { "web" }
            val cleanTitle = title.trim().ifBlank { cleanDomain }.take(200)

            val docRef = db.collection("users").document(uid).collection("bookmarks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "url" to cleanUrl,
                "domain" to cleanDomain,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(userId: String, bookmarkId: String): Result<Unit> {
        val docPath = "users/$userId/bookmarks/$bookmarkId"
        return try {
            val uid = requireUserId(userId)
            require(bookmarkId.isNotBlank()) { "Bookmark ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("bookmarks").document(bookmarkId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordHistoryVisit(userId: String, title: String, url: String): Result<Unit> {
        var docPath = "users/$userId/history"
        return try {
            val uid = requireUserId(userId)
            val cleanUrl = url.trim().take(2048)
            if (cleanUrl.isBlank() || cleanUrl.startsWith("vortex://")) {
                return Result.success(Unit)
            }
            val cleanDomain = VortexUrls.extractDomain(cleanUrl).take(255).ifBlank { "web" }
            val cleanTitle = title.trim().ifBlank { cleanDomain }.take(200)

            val docRef = db.collection("users").document(uid).collection("history").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "url" to cleanUrl,
                "domain" to cleanDomain,
                "visitedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteHistoryEntry(userId: String, entryId: String): Result<Unit> {
        val docPath = "users/$userId/history/$entryId"
        return try {
            val uid = requireUserId(userId)
            require(entryId.isNotBlank()) { "History entry ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("history").document(entryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearHistory(userId: String): Result<Unit> {
        val colPath = "users/$userId/history"
        return try {
            val uid = requireUserId(userId)
            val snapshot = db.collection("users").document(uid)
                .collection("history").get().await()
            val chunks = snapshot.documents.chunked(400)
            for (chunk in chunks) {
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }

    suspend fun savePreferences(userId: String, preferences: UserPreferences): Result<Unit> {
        val docPath = "users/$userId/settings/preferences"
        return try {
            val uid = requireUserId(userId)
            val validatedEngine = IndependentEngine.fromRaw(preferences.defaultEngine).name
            val docRef = db.collection("users").document(uid)
                .collection("settings").document("preferences")
            val payload = mapOf(
                "userId" to uid,
                "defaultEngine" to validatedEngine,
                "adBlockEnabled" to preferences.adBlockEnabled,
                "aiSearchEnabled" to preferences.aiSearchEnabled,
                "javascriptEnabled" to preferences.javascriptEnabled,
                "desktopModeDefault" to preferences.desktopModeDefault,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
