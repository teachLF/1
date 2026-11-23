package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.VortexShieldBlocker
import com.example.model.BrowserTab
import com.example.model.UserPreferences
import org.json.JSONTokener

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@Stable
class WebViewPool {
    private val instances = mutableMapOf<String, WebView>()
    private val lastLoadedUrls = mutableMapOf<String, String>()
    private val lastReloadNonces = mutableMapOf<String, Int>()
    private val lastDesktopModes = mutableMapOf<String, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(
        context: Context,
        tab: BrowserTab,
        preferences: UserPreferences,
        onStateUpdate: (
            tabId: String,
            url: String?,
            title: String?,
            loading: Boolean?,
            progress: Int?,
            canGoBack: Boolean?,
            canGoForward: Boolean?
        ) -> Unit,
        onPageCommitted: (tabId: String, title: String, url: String) -> Unit,
        onTrackerBlocked: (tabId: String) -> Unit,
        onDownloadRequest: (url: String, userAgent: String, disposition: String, mimeType: String) -> Unit
    ): WebView {
        val existing = instances[tab.tabId]
        if (existing != null) {
            applyDynamicSettings(existing, tab, preferences)
            return existing
        }

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#07070F"))
            applyDynamicSettings(this, tab, preferences)

            settings.apply {
                domStorageEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                databaseEnabled = !tab.incognitoMode
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            if (tab.incognitoMode) {
                CookieManager.getInstance().setAcceptCookie(true)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    if (request.isForMainFrame) return null
                    if (!preferences.adBlockEnabled) return null
                    val currentPageUrl = lastLoadedUrls[tab.tabId] ?: tab.url
                    if (VortexShieldBlocker.shouldBlock(reqUrl, currentPageUrl)) {
                        mainHandler.post { onTrackerBlocked(tab.tabId) }
                        return VortexShieldBlocker.emptyBlockedResponse()
                    }
                    return null
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val activeUrl = url ?: return
                    lastLoadedUrls[tab.tabId] = activeUrl
                    onStateUpdate(
                        tab.tabId,
                        activeUrl,
                        view?.title,
                        true,
                        15,
                        view?.canGoBack() ?: false,
                        view?.canGoForward() ?: false
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    val activeUrl = url ?: return
                    val pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: activeUrl
                    lastLoadedUrls[tab.tabId] = activeUrl
                    onStateUpdate(
                        tab.tabId,
                        activeUrl,
                        pageTitle,
                        false,
                        100,
                        view?.canGoBack() ?: false,
                        view?.canGoForward() ?: false
                    )
                    onPageCommitted(tab.tabId, pageTitle, activeUrl)
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    val activeUrl = url ?: return
                    lastLoadedUrls[tab.tabId] = activeUrl
                    onStateUpdate(
                        tab.tabId,
                        activeUrl,
                        view?.title,
                        null,
                        null,
                        view?.canGoBack() ?: false,
                        view?.canGoForward() ?: false
                    )
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onStateUpdate(
                        tab.tabId,
                        null,
                        null,
                        newProgress < 100,
                        newProgress,
                        view?.canGoBack() ?: false,
                        view?.canGoForward() ?: false
                    )
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        onStateUpdate(tab.tabId, null, title, null, null, null, null)
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (!url.isNullOrBlank()) {
                    onDownloadRequest(
                        url,
                        userAgent.orEmpty(),
                        contentDisposition.orEmpty(),
                        mimeType.orEmpty()
                    )
                }
            }
        }

        instances[tab.tabId] = webView
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyDynamicSettings(
        webView: WebView,
        tab: BrowserTab,
        preferences: UserPreferences
    ) {
        webView.settings.javaScriptEnabled = preferences.javascriptEnabled
        val prevDesktop = lastDesktopModes[tab.tabId]
        if (prevDesktop != tab.desktopMode) {
            lastDesktopModes[tab.tabId] = tab.desktopMode
            webView.settings.userAgentString = if (tab.desktopMode) DESKTOP_USER_AGENT else null
        }
    }

    fun syncNavigation(webView: WebView, tab: BrowserTab) {
        if (tab.url.startsWith("vortex://")) return
        val prevNonce = lastReloadNonces[tab.tabId] ?: -1
        val prevUrl = lastLoadedUrls[tab.tabId]
        if (prevNonce != tab.reloadNonce || prevUrl != tab.url) {
            lastReloadNonces[tab.tabId] = tab.reloadNonce
            lastLoadedUrls[tab.tabId] = tab.url
            webView.loadUrl(tab.url)
        }
    }

    fun goBack(tabId: String): Boolean {
        val wv = instances[tabId] ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            true
        } else false
    }

    fun goForward(tabId: String): Boolean {
        val wv = instances[tabId] ?: return false
        return if (wv.canGoForward()) {
            wv.goForward()
            true
        } else false
    }

    fun reload(tabId: String) {
        instances[tabId]?.reload()
    }

    fun stopLoading(tabId: String) {
        instances[tabId]?.stopLoading()
    }

    fun findAll(tabId: String, query: String) {
        instances[tabId]?.findAllAsync(query)
    }

    fun findNext(tabId: String, forward: Boolean) {
        instances[tabId]?.findNext(forward)
    }

    fun clearMatches(tabId: String) {
        instances[tabId]?.clearMatches()
    }

    fun extractReadableText(tabId: String, onExtracted: (String) -> Unit) {
        val wv = instances[tabId]
        if (wv == null) {
            onExtracted("")
            return
        }
        wv.evaluateJavascript(
            "(function(){ return document.body ? document.body.innerText : ''; })();"
        ) { rawJson ->
            val decoded = try {
                if (rawJson.isNullOrBlank() || rawJson == "null") ""
                else JSONTokener(rawJson).nextValue() as? String ?: ""
            } catch (_: Exception) {
                rawJson.orEmpty().trim('"')
            }
            onExtracted(decoded)
        }
    }

    fun disposeTab(tab: BrowserTab) {
        val wv = instances.remove(tab.tabId)
        lastLoadedUrls.remove(tab.tabId)
        lastReloadNonces.remove(tab.tabId)
        lastDesktopModes.remove(tab.tabId)
        if (wv != null) {
            if (tab.incognitoMode) {
                wv.clearHistory()
                wv.clearCache(true)
                wv.clearFormData()
                CookieManager.getInstance().removeSessionCookies(null)
            }
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }

    fun disposeAll() {
        val all = instances.values.toList()
        instances.clear()
        lastLoadedUrls.clear()
        lastReloadNonces.clear()
        lastDesktopModes.clear()
        all.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
    }
}

@Composable
fun VortexWebViewport(
    tab: BrowserTab,
    preferences: UserPreferences,
    webViewPool: WebViewPool,
    onStateUpdate: (
        tabId: String,
        url: String?,
        title: String?,
        loading: Boolean?,
        progress: Int?,
        canGoBack: Boolean?,
        canGoForward: Boolean?
    ) -> Unit,
    onPageCommitted: (tabId: String, title: String, url: String) -> Unit,
    onTrackerBlocked: (tabId: String) -> Unit,
    onDownloadRequest: (url: String, userAgent: String, disposition: String, mimeType: String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context)
        },
        update = { container ->
            val webView = webViewPool.getOrCreate(
                context = container.context,
                tab = tab,
                preferences = preferences,
                onStateUpdate = onStateUpdate,
                onPageCommitted = onPageCommitted,
                onTrackerBlocked = onTrackerBlocked,
                onDownloadRequest = onDownloadRequest
            )
            if (webView.parent !== container) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                container.addView(webView)
            }
            webViewPool.syncNavigation(webView, tab)
        }
    )
}
