package com.example.metateste.host.session

import java.text.Normalizer

/**
 * Classifies a pt-BR spoken reply to a pending command confirmation. Contradictory or
 * unrecognized input is never treated as "sim" — only an unambiguous affirmative counts.
 */
object ConfirmationClassifier {

    enum class Confirmation { AFFIRMATIVE, NEGATIVE, UNCLEAR }

    private val AFFIRMATIVE_WORDS = setOf(
        "sim", "pode", "confirmo", "confirmado", "confirma", "manda", "claro", "afirmativo", "positivo",
    )
    private val NEGATIVE_WORDS = setOf(
        "nao", "cancela", "cancelar", "cancelado", "pare", "parar", "negativo", "negativa",
    )

    fun classify(text: String): Confirmation {
        val words = normalize(text).split(Regex("[^a-z0-9]+")).filterTo(HashSet()) { it.isNotBlank() }
        val hasAffirmative = words.any { it in AFFIRMATIVE_WORDS }
        val hasNegative = words.any { it in NEGATIVE_WORDS }
        return when {
            hasAffirmative && hasNegative -> Confirmation.UNCLEAR
            hasAffirmative -> Confirmation.AFFIRMATIVE
            hasNegative -> Confirmation.NEGATIVE
            else -> Confirmation.UNCLEAR
        }
    }

    /** Lowercases and strips diacritics, so "não"/"nao" and "É"/"e" match the same way. */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}"), "")
    }
}
