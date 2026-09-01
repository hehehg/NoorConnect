package com.noorconnect.core.tdlib

/**
 * Fill these from https://my.telegram.org — required by Telegram's ToS for any third-party client.
 * Never hardcode real values in source control; read them from local.properties / BuildConfig instead.
 */
data class TdLibConfig(
    val apiId: Int,
    val apiHash: String,
    val appVersion: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val useTestDc: Boolean = false,
)
