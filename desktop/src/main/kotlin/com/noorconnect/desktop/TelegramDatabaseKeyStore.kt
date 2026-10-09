package com.noorconnect.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.security.SecureRandom
import java.util.prefs.Preferences

class TelegramDatabaseKeyStore {
    private val preferences = Preferences.userNodeForPackage(TelegramDatabaseKeyStore::class.java)

    @Synchronized
    fun loadOrCreate(): ByteArray {
        check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "TDLib account storage is supported only on Windows in this desktop build."
        }

        val protectedKey = preferences.getByteArray(KEY_NAME, null)
        if (protectedKey != null) {
            return Crypt32Util.cryptUnprotectData(protectedKey)
        }

        val key = ByteArray(KEY_LENGTH).also(SecureRandom()::nextBytes)
        preferences.putByteArray(KEY_NAME, Crypt32Util.cryptProtectData(key))
        preferences.flush()
        return key
    }

    private companion object {
        const val KEY_NAME = "telegram.database.key.dpapi"
        const val KEY_LENGTH = 32
    }
}