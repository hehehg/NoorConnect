package com.noorconnect.feature.auth

/**
 * Deliberately not the full ISO-3166 list (~195 countries) — this covers the common cases
 * (Arabic/Muslim-majority countries first, then the rest of the widely-used set) and is
 * trivially extensible: add another CountryCode(...) line, nothing else changes. The flag
 * is a plain Unicode emoji (regional indicator sequence) — no image asset needed.
 */
data class CountryCode(val nameAr: String, val iso: String, val dialCode: String, val flag: String)

val CountryCodes = listOf(
    CountryCode("مصر", "EG", "20", "🇪🇬"),
    CountryCode("السعودية", "SA", "966", "🇸🇦"),
    CountryCode("الإمارات", "AE", "971", "🇦🇪"),
    CountryCode("الكويت", "KW", "965", "🇰🇼"),
    CountryCode("قطر", "QA", "974", "🇶🇦"),
    CountryCode("البحرين", "BH", "973", "🇧🇭"),
    CountryCode("عُمان", "OM", "968", "🇴🇲"),
    CountryCode("الأردن", "JO", "962", "🇯🇴"),
    CountryCode("العراق", "IQ", "964", "🇮🇶"),
    CountryCode("سوريا", "SY", "963", "🇸🇾"),
    CountryCode("لبنان", "LB", "961", "🇱🇧"),
    CountryCode("فلسطين", "PS", "970", "🇵🇸"),
    CountryCode("اليمن", "YE", "967", "🇾🇪"),
    CountryCode("ليبيا", "LY", "218", "🇱🇾"),
    CountryCode("تونس", "TN", "216", "🇹🇳"),
    CountryCode("الجزائر", "DZ", "213", "🇩🇿"),
    CountryCode("المغرب", "MA", "212", "🇲🇦"),
    CountryCode("السودان", "SD", "249", "🇸🇩"),
    CountryCode("الصومال", "SO", "252", "🇸🇴"),
    CountryCode("موريتانيا", "MR", "222", "🇲🇷"),
    CountryCode("جيبوتي", "DJ", "253", "🇩🇯"),
    CountryCode("جزر القمر", "KM", "269", "🇰🇲"),
    CountryCode("تركيا", "TR", "90", "🇹🇷"),
    CountryCode("إيران", "IR", "98", "🇮🇷"),
    CountryCode("باكستان", "PK", "92", "🇵🇰"),
    CountryCode("أفغانستان", "AF", "93", "🇦🇫"),
    CountryCode("بنغلاديش", "BD", "880", "🇧🇩"),
    CountryCode("إندونيسيا", "ID", "62", "🇮🇩"),
    CountryCode("ماليزيا", "MY", "60", "🇲🇾"),
    CountryCode("نيجيريا", "NG", "234", "🇳🇬"),
    CountryCode("الولايات المتحدة", "US", "1", "🇺🇸"),
    CountryCode("المملكة المتحدة", "GB", "44", "🇬🇧"),
    CountryCode("ألمانيا", "DE", "49", "🇩🇪"),
    CountryCode("فرنسا", "FR", "33", "🇫🇷"),
    CountryCode("إيطاليا", "IT", "39", "🇮🇹"),
    CountryCode("إسبانيا", "ES", "34", "🇪🇸"),
    CountryCode("كندا", "CA", "1", "🇨🇦"),
    CountryCode("الهند", "IN", "91", "🇮🇳"),
    CountryCode("الصين", "CN", "86", "🇨🇳"),
    CountryCode("روسيا", "RU", "7", "🇷🇺"),
)

val DefaultCountryCode = CountryCodes.first { it.iso == "EG" }
