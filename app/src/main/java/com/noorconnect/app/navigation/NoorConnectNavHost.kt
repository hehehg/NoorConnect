package com.noorconnect.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.navArgument
import com.noorconnect.feature.auth.AuthRoute
import com.noorconnect.feature.chat.ChatRoute
import com.noorconnect.feature.chat.ScheduledMessagesRoute
import com.noorconnect.feature.channelinfo.ChannelInfoRoute
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
    const val SCHEDULED_MESSAGES = "scheduled-messages"
    const val CHANNEL_INFO = "channel-info/{channelId}"
    const val CHANNEL_INFO_BY_USERNAME = "channel-info/username/{username}"
    const val CHANNEL_INFO_BY_POST = "channel-info/post/{username}/{messageId}"
    const val CHANNEL_INFO_BY_INVITE = "channel-info/invite/{inviteHash}"
    const val SETTINGS = "settings"
    fun chat(chatId: Long) = "chat/$chatId"
    fun channelInfo(chatId: Long) = "channel-info/$chatId"
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
                if (navController.previousBackStackEntry?.destination?.route?.startsWith("channel-info/") == true) {
                    navController.popBackStack()
                } else {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.CHATS) {
            ChatsRoute(
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenScheduledMessages = { navController.navigate(Routes.SCHEDULED_MESSAGES) },
            )
        }
        composable(Routes.SCHEDULED_MESSAGES) {
            ScheduledMessagesRoute(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
        ) {
            // ChatViewModel reads "chatId" straight out of SavedStateHandle — no manual passing here.
            ChatRoute(
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenChannelInfo = { chatId -> navController.navigate(Routes.channelInfo(chatId)) },
            )
        }
        composable(
            route = Routes.CHANNEL_INFO,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType }),
        ) {
            ChannelInfoRoute(
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Routes.AUTH) },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
            )
        }
        composable(
            route = Routes.CHANNEL_INFO_BY_USERNAME,
            arguments = listOf(navArgument("username") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://t.me/{username}" },
                navDeepLink { uriPattern = "https://telegram.me/{username}" },
                navDeepLink { uriPattern = "tg://resolve?domain={username}" },
            ),
        ) {
            ChannelInfoRoute(
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Routes.AUTH) },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
            )
        }
        composable(
            route = Routes.CHANNEL_INFO_BY_POST,
            arguments = listOf(
                navArgument("username") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.LongType },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://t.me/{username}/{messageId}" },
                navDeepLink { uriPattern = "https://telegram.me/{username}/{messageId}" },
                navDeepLink { uriPattern = "tg://resolve?domain={username}&post={messageId}" },
            ),
        ) {
            ChannelInfoRoute(
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Routes.AUTH) },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
            )
        }
        composable(
            route = Routes.CHANNEL_INFO_BY_INVITE,
            arguments = listOf(navArgument("inviteHash") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://t.me/+{inviteHash}" },
                navDeepLink { uriPattern = "https://t.me/joinchat/{inviteHash}" },
                navDeepLink { uriPattern = "https://telegram.me/+{inviteHash}" },
                navDeepLink { uriPattern = "https://telegram.me/joinchat/{inviteHash}" },
                navDeepLink { uriPattern = "tg://join?invite={inviteHash}" },
            ),
        ) {
            ChannelInfoRoute(
                onBack = { navController.popBackStack() },
                onSignIn = { navController.navigate(Routes.AUTH) },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute()
        }
    }
}
