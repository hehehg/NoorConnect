package com.noorconnect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.noorconnect.app.navigation.NoorConnectNavHost
import com.noorconnect.core.designsystem.NoorConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() — see androidx.core:core-splashscreen docs.
        // The activity's manifest theme (Theme.NoorConnect.Starting) shows first; this library
        // then switches to postSplashScreenTheme (Theme.NoorConnect) automatically once content
        // is ready to draw, no manual theme swap needed here.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            NoorConnectTheme {
                Surface { NoorConnectNavHost() }
            }
        }
    }
}
