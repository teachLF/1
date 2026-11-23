package com.example.model

import android.net.Uri
import java.util.Locale

enum class IndependentEngine {
    VORTEX_NATIVE,
    BRAVE,
    MOJEEK,
    WIKIPEDIA;

    companion object {
        fun fromRaw(raw: String?): IndependentEngine {
            return entries.firstOrNull { it.name == raw } ?: VORTEX_NATIVE
        }
    }
}

object VortexUrls {
    const val HOME = "vortex://home"
    const val SEARCH_PREFIX = "vortex://search?q="

    fun buildNativeSearchUrl(query: String): String {
        return "$SEARCH_PREFIX${Uri.encode(query.trim())}"
    }

    fun isNativeSearch(url: String): Boolean = url.startsWith(SEARCH_PREFIX)

    fun extractNativeSearchQuery(url: String): String {
        if (!isNativeSearch(url)) return ""
        return Uri.decode(url.removePrefix(SEARCH_PREFIX))
    }

    fun buildExternalEngineSearchUrl(query: String, engine: IndependentEngine): String {
        val encoded = Uri.encode(query.trim())
        val wikiLang = if (containsArabic(query) || Locale.getDefault().language == "ar") "ar" else "en"
        return when (engine) {
            IndependentEngine.VORTEX_NATIVE -> buildNativeSearchUrl(query)
            IndependentEngine.BRAVE -> "https://search.brave.com/search?q=$encoded"
            IndependentEngine.MOJEEK -> "https://www.mojeek.com/search?q=$encoded"
            IndependentEngine.WIKIPEDIA -> "https://$wikiLang.wikipedia.org/wiki/Special:Search?search=$encoded"
        }
    }

    fun resolveOmniboxInput(rawInput: String, engine: IndependentEngine): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return HOME
        if (trimmed.startsWith("vortex://")) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val looksLikeDomain = !trimmed.contains(" ") &&
            trimmed.contains(".") &&
            !trimmed.startsWith(".") &&
            !trimmed.endsWith(".") &&
            trimmed.substringAfterLast(".").length in 2..12

        return if (looksLikeDomain || trimmed.startsWith("localhost")) {
            "https://$trimmed"
        } else {
            buildExternalEngineSearchUrl(trimmed, engine)
        }
    }

    fun extractDomain(url: String): String {
        return when {
            url == HOME -> "vortex.home"
            isNativeSearch(url) -> "vortex.search"
            else -> try {
                Uri.parse(url).host?.removePrefix("www.").orEmpty().ifBlank { url }
            } catch (_: Exception) {
                url
            }
        }
    }

    fun containsArabic(text: String): Boolean {
        return text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }
    }
}
