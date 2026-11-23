package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.util.Locale

class VortexAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun streamSmartSearchBrief(query: String): Flow<String> = flow {
        val isArabic = Locale.getDefault().language == "ar" ||
            query.any { it in '\u0600'..'\u06FF' }
        val prompt = if (isArabic) {
            """
            أنت محرك الذكاء المعرفي لمتصفح Vortex المستقل.
            أجب عن استعلام البحث التالي بدقة واختصار ووضوح باللغة العربية:
            "$query"
            قدّم:
            1) خلاصة مباشرة في سطرين أو ثلاثة.
            2) 3 نقاط معرفية سريعة ومفيدة.
            لا تذكر أنك نموذج ذكاء اصطناعي.
            """.trimIndent()
        } else {
            """
            You are Vortex Search's independent synthesis engine.
            Answer the following search query concisely and clearly:
            "$query"
            Provide:
            1) A direct 2-3 sentence overview.
            2) 3 crisp key facts as bullet points.
            """.trimIndent()
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model returned an empty response.")
        }
    }

    fun streamPageSummary(title: String, url: String, pageText: String): Flow<String> = flow {
        val trimmedText = pageText.trim().take(12_000)
        val isArabic = Locale.getDefault().language == "ar" ||
            trimmedText.any { it in '\u0600'..'\u06FF' }
        val prompt = if (isArabic) {
            """
            لخّص محتوى صفحة الويب التالية بوضوح واحترافية باللغة العربية:
            العنوان: $title
            الرابط: $url
            المحتوى:
            $trimmedText

            قدّم:
            - الفكرة الرئيسية في فقرة قصيرة.
            - أهم 4 نقاط مستخلصة من الصفحة.
            """.trimIndent()
        } else {
            """
            Summarize the following webpage clearly and concisely:
            Title: $title
            URL: $url
            Content:
            $trimmedText

            Provide:
            - A short core takeaway paragraph.
            - Top 4 key bullet points from the page.
            """.trimIndent()
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model returned an empty summary.")
        }
    }

    fun streamPageQuestion(title: String, pageText: String, question: String): Flow<String> = flow {
        val trimmedText = pageText.trim().take(12_000)
        val isArabic = Locale.getDefault().language == "ar" ||
            question.any { it in '\u0600'..'\u06FF' }
        val prompt = if (isArabic) {
            """
            بناءً على محتوى الصفحة التالية ("$title"):
            $trimmedText

            أجب بوضوح واختصار عن سؤال المستخدم باللغة العربية:
            $question
            """.trimIndent()
        } else {
            """
            Based on the following webpage ("$title"):
            $trimmedText

            Answer the user's question clearly and concisely:
            $question
            """.trimIndent()
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model returned an empty answer.")
        }
    }
}
