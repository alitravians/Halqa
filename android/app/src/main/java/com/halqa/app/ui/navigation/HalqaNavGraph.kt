package com.halqa.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.halqa.app.ui.screens.arena.AvatarBattleScreen
import com.halqa.app.ui.screens.arena.PkArenaScreen
import com.halqa.app.ui.screens.auth.AuthScreen
import com.halqa.app.ui.screens.auth.PhoneAuthScreen
import com.halqa.app.ui.screens.live.LiveWatchScreen
import com.halqa.app.ui.screens.main.MainScaffold
import com.halqa.app.ui.screens.onboarding.OnboardingScreen
import com.halqa.app.ui.screens.splash.SplashScreen
import com.halqa.app.ui.screens.wallet.TopUpScreen
import com.halqa.app.ui.screens.wallet.WalletScreen
import com.halqa.app.ui.screens.legal.LegalScreen
import com.halqa.app.ui.screens.safety.AgeGateScreen
import com.halqa.app.ui.screens.safety.ReviewResultScreen
import com.halqa.app.ui.screens.safety.UnderReviewScreen

@Composable
fun HalqaNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        enterTransition = { slideInHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeOut(tween(200)) },
    ) {
        composable(Routes.Splash) { SplashScreen(navController) }
        composable(Routes.Onboarding) { OnboardingScreen(navController) }
        composable(Routes.Auth) { AuthScreen(navController) }
        composable(Routes.PhoneAuth) { PhoneAuthScreen(navController) }

        composable(Routes.Main) { MainScaffold(rootNavController = navController) }

        composable(
            Routes.LiveWatch,
            arguments = listOf(navArgument("streamId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("streamId") ?: ""
            LiveWatchScreen(streamId = id, navController = navController)
        }

        composable(Routes.Wallet) { WalletScreen(navController) }
        composable(Routes.TopUp) { TopUpScreen(navController) }

        composable(Routes.PkArena) { PkArenaScreen(navController) }
        composable(
            Routes.AvatarBattle,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("matchId") ?: ""
            AvatarBattleScreen(matchId = id, navController = navController)
        }

        composable(Routes.Terms) { LegalScreen(kind = "terms", navController = navController) }
        composable(Routes.Privacy) { LegalScreen(kind = "privacy", navController = navController) }
        composable(Routes.Community) { LegalScreen(kind = "community", navController = navController) }

        composable(Routes.AgeGate) { AgeGateScreen(navController) }
        composable(Routes.UnderReview) { UnderReviewScreen(navController) }
        composable(Routes.ReviewResult) { ReviewResultScreen(navController) }
    }
}
