package com.noorconnect.domain.moderation

/**
 * Substring match, case-insensitive, over a normalized copy of the text — no DI needed,
 * trivially unit-testable. Both [findFirstMatch] and [containsAny] normalize with the exact
 * same [normalize] function, so a banned word list authored in plain Arabic still matches text
 * that only differs by diacritics, tatweel, or letter-form variants; without this, appending a
 * harakah or an extra kashida to a banned word would silently defeat the filter.
 */
object BannedWordMatcher {

    fun findFirstMatch(text: String, bannedWords: List<String>): String? {
        val normalizedText = normalize(text)
        return bannedWords.firstOrNull { word ->
            word.isNotBlank() && normalizedText.contains(normalize(word))
        }
    }

    /** Used by the search-query gate: only whether ANY word matches, not which one — see
     *  [com.noorconnect.domain.model.SearchResult.QueryBlocked] for why the match itself is
     *  never surfaced back to the caller. */
    fun containsAny(text: String, bannedWords: List<String>): Boolean =
        findFirstMatch(text, bannedWords) != null

    private fun normalize(input: String): String {
        var result = input.lowercase()
        // Arabic diacritics (tashkeel/harakat) — U+064B..U+065F, plus the U+0670 superscript alef.
        result = result.replace(Regex("[\u064B-\u065F\u0670]"), "")
        // Tatweel/kashida — a stretch character with zero semantic meaning.
        result = result.replace("\u0640", "")
        // Common letter-form variants collapsed to one canonical form, so "أ/إ/آ" == "ا" etc.
        result = result
            .replace(Regex("[\u0622\u0623\u0625]"), "\u0627") // آ/أ/إ -> ا
            .replace('\u0629', '\u0647') // ة (taa marbuta) -> ه
            .replace('\u0649', '\u064A') // ى (alef maqsura) -> ي
        // Collapse repeated/extra whitespace so word-spacing tricks don't split a match.
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }
}
