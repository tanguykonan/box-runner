package com.box.android.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.box.android.data.auth.AuthRepository
import com.box.android.data.box.BoxRepository
import com.box.android.data.box.BoxRepositoryImpl
import com.box.android.data.local.UserPreferencesRepository
import com.box.android.feature.faq.FaqScreen
import com.box.android.feature.home.HomeScreen
import com.box.android.feature.home.HomeViewModel
import com.box.android.feature.onboarding.OnboardingScreen
import com.box.android.feature.onboarding.OnboardingViewModel
import com.box.android.feature.profile.ProfileScreen
import com.box.android.feature.service.NewAppScreen
import com.box.android.feature.service.NewAppViewModel
import com.box.android.feature.service.details.AppDetailsScreen
import com.box.android.feature.service.details.AppDetailsViewModel
import com.box.android.feature.volume.NewVolumeScreen
import com.box.android.feature.volume.NewVolumeViewModel
import com.box.android.feature.volume.list.VolumesScreen
import com.box.android.feature.volume.list.VolumesViewModel

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Profile : Screen("profile")
    data object NewApp : Screen("new_app?packageId={packageId}") {
        const val ROUTE = "new_app?packageId={packageId}"
        fun createRoute(packageId: String? = null): String {
            return if (!packageId.isNullOrBlank()) {
                val encoded = java.net.URLEncoder.encode(packageId, "UTF-8")
                "new_app?packageId=$encoded"
            } else {
                "new_app?packageId="
            }
        }
    }
    data object NewVolume : Screen("new_volume")
    data object Volumes : Screen("volumes")
    data object Faq : Screen("faq")
    data class AppDetails(val appId: String = "{appId}") : Screen("app_details/$appId") {
        companion object {
            const val ROUTE = "app_details/{appId}"
            fun createRoute(appId: String) = "app_details/$appId"
        }
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    preferencesRepository: UserPreferencesRepository,
    authRepository: AuthRepository,
    boxRepository: BoxRepository = remember { BoxRepositoryImpl() },
    navController: NavHostController = rememberNavController()
) {
    val startDestination = if (!preferencesRepository.isOnboardingCompleted()) {
        Screen.Onboarding.route
    } else {
        Screen.Home.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(280)) },
        exitTransition = { fadeOut(animationSpec = tween(280)) },
        popEnterTransition = { fadeIn(animationSpec = tween(280)) },
        popExitTransition = { fadeOut(animationSpec = tween(280)) }
    ) {
        composable(Screen.Onboarding.route) {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.provideFactory(preferencesRepository)
            )
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(boxRepository, authRepository, preferencesRepository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToAppDetails = { appId ->
                    navController.navigate(Screen.AppDetails.createRoute(appId))
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToLogin = {},
                onNavigateToNewApp = { packageId ->
                    navController.navigate(Screen.NewApp.createRoute(packageId))
                },
                onNavigateToNewVolume = {
                    navController.navigate(Screen.NewVolume.route)
                },
                onNavigateToVolumes = {
                    navController.navigate(Screen.Volumes.route)
                },
                onNavigateToFaq = {
                    navController.navigate(Screen.Faq.route)
                }
            )
        }

        composable(Screen.Volumes.route) {
            val volumesViewModel: VolumesViewModel = viewModel(
                factory = VolumesViewModel.provideFactory(boxRepository)
            )
            VolumesScreen(
                viewModel = volumesViewModel,
                onNavigateToNewVolume = {
                    navController.navigate(Screen.NewVolume.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AppDetails.ROUTE) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString("appId") ?: return@composable
            val appDetailsViewModel: AppDetailsViewModel = viewModel(
                factory = AppDetailsViewModel.provideFactory(appId, boxRepository)
            )
            AppDetailsScreen(
                viewModel = appDetailsViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Profile.route) {
            val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
            ProfileScreen(
                user = currentUser,
                onClose = {
                    navController.popBackStack()
                },
                onLogout = {
                    authRepository.logout()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.NewApp.ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("packageId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val packageIdArg = backStackEntry.arguments?.getString("packageId")?.takeIf { it.isNotBlank() }?.let {
                try {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } catch (e: Exception) {
                    it
                }
            }
            val newAppViewModel: NewAppViewModel = viewModel(
                factory = NewAppViewModel.provideFactory(boxRepository, packageIdArg)
            )
            NewAppScreen(
                viewModel = newAppViewModel,
                onClose = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.NewVolume.route) {
            val newVolumeViewModel: NewVolumeViewModel = viewModel(
                factory = NewVolumeViewModel.provideFactory(boxRepository)
            )
            NewVolumeScreen(
                viewModel = newVolumeViewModel,
                onClose = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Faq.route) {
            FaqScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
