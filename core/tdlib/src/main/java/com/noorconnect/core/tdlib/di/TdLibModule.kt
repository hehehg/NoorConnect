package com.noorconnect.core.tdlib.di

import android.content.Context
import com.noorconnect.core.tdlib.TdLibConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TdLibModule {

    @Provides
    @Singleton
    fun provideTdLibConfig(@ApplicationContext context: Context): TdLibConfig = TdLibConfig(
        // Populated from local.properties by core/tdlib/build.gradle.kts — see local.properties.example.
        apiId = com.noorconnect.core.tdlib.BuildConfig.TELEGRAM_API_ID.toInt(),
        apiHash = com.noorconnect.core.tdlib.BuildConfig.TELEGRAM_API_HASH,
        appVersion = "0.1.0",
        databaseDirectory = context.filesDir.absolutePath + "/tdlib",
        filesDirectory = context.filesDir.absolutePath + "/tdlib-files",
    )
}
