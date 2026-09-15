package com.box.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import com.box.android.data.auth.AuthUser
import com.box.android.data.auth.AuthRepositoryImpl
import com.box.android.data.box.BoxRepositoryImpl
import com.box.android.data.local.UserPreferencesRepositoryImpl
import com.box.android.navigation.AppNavigation
import com.box.android.ui.theme.BoxAndroidAppTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepositoryImpl

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredStartupPermissions()

        val preferencesRepository = UserPreferencesRepositoryImpl(applicationContext)
        authRepository = AuthRepositoryImpl(applicationContext)
        val boxRepository = BoxRepositoryImpl(applicationContext)

        handleAuthIntent(intent)

        setContent {
            BoxAndroidAppTheme {
                AppNavigation(
                    preferencesRepository = preferencesRepository,
                    authRepository = authRepository,
                    boxRepository = boxRepository
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "box" && (data.host == "auth" || data.host == "login" || data.host == "callback")) {
            val token = data.getQueryParameter("token") ?: data.getQueryParameter("verifiedToken")
            val id = data.getQueryParameter("id") ?: data.getQueryParameter("userId") ?: UUID.randomUUID().toString()
            val username = data.getQueryParameter("username") ?: "Developer"
            val email = data.getQueryParameter("email") ?: ""
            val avatarUrl = data.getQueryParameter("avatarUrl") ?: data.getQueryParameter("image")
            val role = data.getQueryParameter("role") ?: "USER"
            val tier = data.getQueryParameter("tier") ?: "FREE"

            if (!token.isNullOrBlank()) {
                val user = AuthUser(
                    id = id,
                    username = username,
                    email = email,
                    avatarUrl = avatarUrl,
                    role = role,
                    tier = tier,
                    token = token
                )
                authRepository.saveDirectSession(user)
            }
        }
    }

    private fun requestRequiredStartupPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}