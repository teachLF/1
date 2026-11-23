package com.example.data

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object VortexShieldBlocker {

    private val blockedDomains: HashSet<String> = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "fundingchoicesmessages.google.com",
        "bat.bing.com",
        "clarity.ms",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "admob.com",
        "amazon-adsystem.com",
        "appnexus.com",
        "appsflyer.com",
        "branch.io",
        "chartbeat.com",
        "comscore.com",
        "criteo.com",
        "criteo.net",
        "demdex.net",
        "exoclick.com",
        "facebook.net",
        "connect.facebook.net",
        "flurry.com",
        "hotjar.com",
        "indexww.com",
        "juicyads.com",
        "krxd.net",
        "liadm.com",
        "mathtag.com",
        "mediavine.com",
        "mgid.com",
        "mixpanel.com",
        "moatads.com",
        "newrelic.com",
        "omtrdc.net",
        "openx.net",
        "outbrain.com",
        "parsely.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "pubmatic.com",
        "quantserve.com",
        "revcontent.com",
        "rubiconproject.com",
        "scorecardresearch.com",
        "segment.io",
        "segment.com",
        "sentry.io",
        "serving-sys.com",
        "sharethrough.com",
        "smartadserver.com",
        "snapchat.com",
        "sc-static.net",
        "spotxchange.com",
        "taboola.com",
        "tapad.com",
        "teads.tv",
        "tiktokv.com",
        "analytics.tiktok.com",
        "trackjs.com",
        "trafficjunky.net",
        "turn.com",
        "twitter.com/i/jot",
        "analytics.twitter.com",
        "ads-twitter.com",
        "unityads.unity3d.com",
        "vungle.com",
        "yandex.ru/metrika",
        "mc.yandex.ru",
        "yieldmo.com",
        "zedo.com",
        "zemanta.com",
        "amplitude.com",
        "fullstory.com",
        "heapanalytics.com",
        "kissmetrics.com",
        "logrocket.com",
        "mouseflow.com",
        "optimizely.com",
        "bugsnag.com"
    )

    private val blockedSubdomainPrefixes = listOf(
        "ads.", "ad.", "adserver.", "adservice.", "analytics.",
        "tracker.", "tracking.", "telemetry.", "metrics.", "pixel.",
        "beacon.", "pagead.", "pop."
    )

    private val blockedPathPatterns = listOf(
        "/pagead/", "/ads/js", "/adserver/", "/pixel?", "/tr?",
        "/collect?", "/gtag/js", "/gtm.js", "prebid.js", "adsense"
    )

    fun shouldBlock(requestUrl: String, mainPageUrl: String?): Boolean {
        val uri = try {
            Uri.parse(requestUrl)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        val mainHost = mainPageUrl?.let {
            try {
                Uri.parse(it).host?.lowercase()?.removePrefix("www.")
            } catch (_: Exception) {
                null
            }
        }

        // Never block the primary first-party document host itself
        val normalizedHost = host.removePrefix("www.")
        if (mainHost != null && (normalizedHost == mainHost || normalizedHost.endsWith(".$mainHost"))) {
            return false
        }

        if (isHostBlocked(normalizedHost)) return true

        if (blockedSubdomainPrefixes.any { normalizedHost.startsWith(it) }) {
            return true
        }

        val lowerUrl = requestUrl.lowercase()
        return blockedPathPatterns.any { lowerUrl.contains(it) }
    }

    private fun isHostBlocked(host: String): Boolean {
        if (blockedDomains.contains(host)) return true
        var index = host.indexOf('.')
        while (index != -1) {
            val parent = host.substring(index + 1)
            if (blockedDomains.contains(parent)) return true
            index = host.indexOf('.', index + 1)
        }
        return false
    }

    fun emptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
