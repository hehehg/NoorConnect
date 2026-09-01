package com.noorconnect.app

import android.app.Application
import com.noorconnect.core.tdlib.TdLibManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    // Hilt injects Application fields before onCreate() runs (see Hilt's generated base class).
    @Inject lateinit var tdLibManager: TdLibManager

    override fun onCreate() {
        super.onCreate()
        // Single call, single place. Nothing else in the app ever creates a Client.
        tdLibManager.start()
    }
}
