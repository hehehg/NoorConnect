package com.noorconnect.data.di

import com.noorconnect.data.repository.AuthRepositoryImpl
import com.noorconnect.data.repository.ChatModerationRepositoryImpl
import com.noorconnect.data.repository.ChatRepositoryImpl
import com.noorconnect.data.repository.FolderRepositoryImpl
import com.noorconnect.data.repository.ModerationSettingsRepositoryImpl
import com.noorconnect.data.repository.UserPreferencesRepositoryImpl
import com.noorconnect.data.repository.UserRepositoryImpl
import com.noorconnect.domain.repository.AuthRepository
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.ChatRepository
import com.noorconnect.domain.repository.FolderRepository
import com.noorconnect.domain.repository.ModerationSettingsRepository
import com.noorconnect.domain.repository.UserPreferencesRepository
import com.noorconnect.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds interface -> implementation. Nothing outside :data ever instantiates these
 * classes directly, so a screen or use case can be unit-tested with a fake in seconds.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindModerationSettingsRepository(impl: ModerationSettingsRepositoryImpl): ModerationSettingsRepository
    @Binds abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
    @Binds abstract fun bindChatModerationRepository(impl: ChatModerationRepositoryImpl): ChatModerationRepository
    @Binds abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
    @Binds abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
