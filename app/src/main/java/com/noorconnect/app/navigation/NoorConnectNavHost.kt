package com.noorconnect.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noorconnect.feature.auth.AuthRoute
import com.noorconnect.feature.chat.ChatRoute
import com.noorconnect.feature.chats.ChatsRoute
import com.noorconnect.feature.onboarding.OnboardingRoute
import com.noorconnect.feature.settings.SettingsRoute

/**
 * :app owns the nav graph and wires feature Route composables together — feature modules
 * never reference each other or navigation-compose directly (see feature build.gradle files).
 * Adding a new screen later means: add a route here, call the feature's own Route composable.
 * No existing route changes.
 */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val CHATS = "chats"
    const val CHAT = "chat/{chatId}"
    const val SETTINGS = "settings"
    fun chat(chatId: Long) = "chat/$chatId"
}

@Composable
fun NoorConnectNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingRoute(onFinished = {
                navController.navigate(Routes.AUTH) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.AUTH) {
            AuthRoute(onAuthenticated = {
                navController.navigate(Routes.CHATS) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.CHATS) {
            ChatsRoute(
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) {
            // ChatViewModel reads "chatId" straight out of SavedStateHandle — no manual passing here.
            ChatRoute()
        }
        composable(Routes.SETTINGS) {
            SettingsRoute()
        }
    }
}
