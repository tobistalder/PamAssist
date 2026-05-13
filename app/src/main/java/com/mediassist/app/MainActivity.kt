package com.mediassist.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.mediassist.app.domain.AlarmScheduler
import com.mediassist.app.domain.CactusManager
import com.mediassist.app.ui.screens.BasicProfileScreen
import com.mediassist.app.ui.screens.ChatScreen
import com.mediassist.app.ui.screens.MainMenuScreen
import com.mediassist.app.ui.screens.MedicalHistoryScreen
import com.mediassist.app.ui.screens.MedicationsScreen
import com.mediassist.app.ui.screens.ModelLoadScreen
import com.mediassist.app.ui.screens.ScanScreen
import com.mediassist.app.ui.screens.ViewProfileScreen
import com.mediassist.app.ui.screens.WelcomeScreen
import com.mediassist.app.ui.theme.PamAssistTheme
import com.mediassist.app.ui.viewmodel.ChatViewModel
import com.mediassist.app.ui.viewmodel.ChatViewModelFactory
import com.mediassist.app.ui.viewmodel.MedicationsViewModel
import com.mediassist.app.ui.viewmodel.MedicationsViewModelFactory
import com.mediassist.app.ui.viewmodel.ProfileViewModel
import com.mediassist.app.ui.viewmodel.ProfileViewModelFactory
import com.mediassist.app.ui.viewmodel.ScanViewModel
import com.mediassist.app.ui.viewmodel.ScanViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        val app = application as MediAssistApp

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }


        val sharedPrefs = getSharedPreferences("MediAssistPrefs", Context.MODE_PRIVATE)
        val isDemoMode = sharedPrefs.getBoolean("DEMO_MODE", false)
        var isModelReady = sharedPrefs.getBoolean("MODEL_READY", false)

        // Verify model file actually exists — clears stale flags from previous install methods
        if (isModelReady && !CactusManager.isModelDownloaded(this)) {
            isModelReady = false
            sharedPrefs.edit()
                .putBoolean("MODEL_READY", false)
                .remove("MODEL_SLUG")
                .apply()
        }

        val appModelReady = isModelReady || isDemoMode
        
        if (isDemoMode) {
            CactusManager.enableDemoMode()
        }

        // Schedule medication alarms on startup
        if (appModelReady) {
            lifecycleScope.launch {
                val meds = app.medicationRepository.allMedications
                    .firstOrNull() ?: emptyList()
                AlarmScheduler(applicationContext).scheduleAll(meds)
            }
        }

        setContent {
            PamAssistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MediAssistNavHost(app, appModelReady)
                    }
                }
            }
        }
    }
}

@Composable
fun MediAssistNavHost(app: MediAssistApp, isModelReady: Boolean) {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Shared ViewModel for profile creation flow (screens 1 & 2)
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(app.userProfileRepository)
    )

    // ViewModel for medications screen
    val medicationsViewModel: MedicationsViewModel = viewModel(
        factory = MedicationsViewModelFactory(app.medicationRepository, context.applicationContext)
    )

    // ViewModel for chat screen
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            app,
            app.userProfileRepository,
            app.medicationRepository
        )
    )

    // ViewModel for scan screen
    val scanViewModel: ScanViewModel = viewModel(
        factory = ScanViewModelFactory(
            app,
            app.userProfileRepository,
            app.medicationRepository
        )
    )

    val latestProfile by app.userProfileRepository.latestProfile.collectAsState(initial = null)
    val hasProfile = latestProfile != null

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                hasProfile = hasProfile,
                isModelReady = isModelReady,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }
        
        composable("main_menu") {
            MainMenuScreen(
                onChatClick = {
                    navController.navigate("chat") {
                        popUpTo("main_menu") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onScanClick = {
                    navController.navigate("scan") {
                        popUpTo("main_menu") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onMedicationsClick = {
                    navController.navigate("medications") {
                        popUpTo("main_menu") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onProfileClick = {
                    navController.navigate("view_profile") {
                        popUpTo("main_menu") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("scan") {
            ScanScreen(
                viewModel = scanViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("view_profile") {
            ViewProfileScreen(
                viewModel = profileViewModel,
                onNavigateToEdit = { navController.navigate("basic_profile") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("model_load") {
            ModelLoadScreen(
                onSuccess = {
                    navController.navigate("main_menu") {
                        popUpTo("model_load") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("basic_profile") {
            BasicProfileScreen(
                viewModel = profileViewModel,
                onContinue = {
                    navController.navigate("medical_history")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("medical_history") {
            MedicalHistoryScreen(
                viewModel = profileViewModel,
                onProfileSaved = {
                    val prefs = context.getSharedPreferences("MediAssistPrefs", Context.MODE_PRIVATE)
                    val modelReady = prefs.getBoolean("MODEL_READY", false)
                    val demoMode = prefs.getBoolean("DEMO_MODE", false)

                    if (!modelReady && !demoMode) {
                        navController.navigate("model_load") {
                            popUpTo("basic_profile") { inclusive = true }
                        }
                    } else {
                        navController.navigate("main_menu") {
                            popUpTo("basic_profile") { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("medications") {
            MedicationsScreen(
                viewModel = medicationsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
