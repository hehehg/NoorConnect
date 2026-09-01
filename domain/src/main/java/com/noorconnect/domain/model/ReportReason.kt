package com.noorconnect.domain.model

/**
 * The fixed reason list shown in the "report this chat" sheet (feature:moderation). Plain
 * Kotlin + a couple of Arabic strings — no Android dependency, so it stays fine inside :domain.
 * Reporting never decides the outcome itself: it always lands in the same Firestore "pending"
 * queue as [com.noorconnect.domain.usecase.CheckChatAccessUseCase]'s auto-flag, and only the
 * admin panel moves a chat from there to whitelist/blacklist (see README "آلية الفلترة").
 */
enum class ReportReason(val label: String) {
    UN_ISLAMIC_CONTENT("محتوى غير إسلامي أو مخالف للشريعة"),
    NUDITY_OR_SEXUAL_CONTENT("محتوى جنسي أو غير لائق"),
    HATE_OR_VIOLENCE("خطاب كراهية أو تحريض على العنف"),
    SCAM_OR_FRAUD("احتيال أو نصب"),
    HARASSMENT("مضايقة أو تنمر"),
    OTHER("سبب آخر"),
}
