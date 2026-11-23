package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import com.example.model.DownloadItem
import com.example.model.DownloadStatus

class DownloadBridge(private val context: Context) {

    private val downloadManager: DownloadManager?
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    fun enqueueDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ): Result<DownloadItem> {
        return try {
            val uri = Uri.parse(url)
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .ifBlank { "vortex_download" }

            val request = DownloadManager.Request(uri).apply {
                setMimeType(mimeType.ifBlank { "*/*" })
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription("Vortex Browser Download")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = downloadManager
                ?: return Result.failure(IllegalStateException("DownloadManager unavailable"))
            val id = dm.enqueue(request)
            Result.success(
                DownloadItem(
                    downloadId = id,
                    fileName = fileName,
                    url = url,
                    mimeType = mimeType,
                    status = DownloadStatus.PENDING,
                    progressPercent = 0
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun refreshStatuses(existing: List<DownloadItem>): List<DownloadItem> {
        val dm = downloadManager ?: return existing
        if (existing.isEmpty()) return existing
        val ids = existing.map { it.downloadId }.toLongArray()
        val query = DownloadManager.Query().setFilterById(*ids)
        val updatedMap = mutableMapOf<Long, DownloadItem>()

        dm.query(query)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

            while (cursor.moveToNext()) {
                val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                val base = existing.firstOrNull { it.downloadId == id } ?: continue
                val dmStatus = if (statusCol >= 0) cursor.getInt(statusCol) else DownloadManager.STATUS_PENDING
                val downloaded = if (downloadedCol >= 0) cursor.getLong(downloadedCol) else 0L
                val total = if (totalCol >= 0) cursor.getLong(totalCol) else -1L
                val localUri = if (uriCol >= 0) cursor.getString(uriCol) else null
                val title = if (titleCol >= 0) cursor.getString(titleCol) else base.fileName

                val mappedStatus = when (dmStatus) {
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> DownloadStatus.PENDING
                }
                val pct = when {
                    mappedStatus == DownloadStatus.SUCCESSFUL -> 100
                    total > 0L -> ((downloaded * 100) / total).toInt().coerceIn(0, 99)
                    else -> 0
                }
                updatedMap[id] = base.copy(
                    fileName = title ?: base.fileName,
                    status = mappedStatus,
                    progressPercent = pct,
                    localUri = localUri
                )
            }
        }

        return existing.map { updatedMap[it.downloadId] ?: it }
    }

    fun openDownloadedItem(item: DownloadItem): Boolean {
        val dm = downloadManager ?: return false
        return try {
            val uri = dm.getUriForDownloadedFile(item.downloadId)
                ?: item.localUri?.let { Uri.parse(it) }
                ?: return false
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openSystemDownloads()
        }
    }

    fun openSystemDownloads(): Boolean {
        return try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
