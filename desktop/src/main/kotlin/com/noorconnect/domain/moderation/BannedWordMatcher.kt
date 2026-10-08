package com.noorconnect.domain.moderation

object BannedWordMatcher {
    fun findFirstMatch(text: String, bannedWords: List<String>): String? {
        val normalizedText = normalize(text)
        return bannedWords.firstOrNull { word ->
            word.isNotBlank() && normalizedText.contains(normalize(word))
        }
    }

    fun containsAny(text: String, bannedWords: List<String>): Boolean =
        findFirstMatch(text, bannedWords) != null

    private fun normalize(input: String): String {
        var result = input.lowercase()
        result = result.replace(Regex("[\u064B-\u065F\u0670]"), "")
        result = result.replace("\u0640", "")
        result = result
            .replace(Regex("[\u0622\u0623\u0625]"), "\u0627")
            .replace('\u0629', '\u0647')
            .replace('\u0649', '\u064A')
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }
}
