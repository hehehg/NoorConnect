package com.noorconnect.feature.moderation

import com.noorconnect.domain.moderation.ContentFilter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ModerationModule {
    // Swap this one binding to change app-wide filtering behavior — e.g. a stricter
    // build flavor could bind a different ContentFilter here with zero other changes.
    @Binds
    abstract fun bindContentFilter(impl: IslamicContentFilter): ContentFilter
}
