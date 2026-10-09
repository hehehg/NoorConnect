package com.noorconnect.desktop

import java.util.prefs.Preferences

data class TelegramApiCredentials(
    val apiId: Int,
    val apiHash: String,
)

class TelegramApiCredentialsStore {
    private val preferences = Preferences.userNodeForPackage(TelegramApiCredentialsStore::class.java)

    fun load(): TelegramApiCredentials? {
        val apiId = preferences.getInt(API_ID_KEY, 0)
        val apiHash = preferences.get(API_HASH_KEY, "")
        if (apiId <= 0 || apiHash.isBlank()) return null
        return TelegramApiCredentials(apiId, apiHash)
    }

    fun save(credentials: TelegramApiCredentials) {
        preferences.putInt(API_ID_KEY, credentials.apiId)
        preferences.put(API_HASH_KEY, credentials.apiHash)
    }

    private companion object {
        const val API_ID_KEY = "telegram.api.id"
        const val API_HASH_KEY = "telegram.api.hash"
    }
}