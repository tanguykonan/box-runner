package com.box.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class AuthUser(
    val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String? = null,
    val role: String = "ADMIN",
    val tier: String = "PRO",
    val token: String = ""
)

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>
    fun isLoggedIn(): Boolean
    suspend fun refreshProfile(): Result<AuthUser>
    fun saveDirectSession(user: AuthUser)
    fun logout()
}

class AuthRepositoryImpl(context: Context? = null) : AuthRepository {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_AUTH_NAME, Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<AuthUser?>(loadPersistedSession() ?: DEFAULT_LOCAL_USER)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override fun isLoggedIn(): Boolean = true

    override suspend fun refreshProfile(): Result<AuthUser> {
        val user = _currentUser.value ?: DEFAULT_LOCAL_USER
        return Result.success(user)
    }

    override fun saveDirectSession(user: AuthUser) {
        saveSession(user)
        _currentUser.value = user
    }

    override fun logout() {
        prefs?.edit()?.clear()?.commit()
        _currentUser.value = DEFAULT_LOCAL_USER
    }

    private fun loadPersistedSession(): AuthUser? {
        val p = prefs ?: return null
        val id = p.getString(KEY_USER_ID, null) ?: return null
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val username = p.getString(KEY_USERNAME, "User") ?: "User"
        val email = p.getString(KEY_EMAIL, "") ?: ""
        val avatarUrl = p.getString(KEY_AVATAR_URL, null)
        val role = p.getString(KEY_ROLE, "USER") ?: "USER"
        val tier = p.getString(KEY_TIER, "FREE") ?: "FREE"

        return AuthUser(
            id = id,
            username = username,
            email = email,
            avatarUrl = avatarUrl,
            role = role,
            tier = tier,
            token = token
        )
    }

    private fun saveSession(user: AuthUser) {
        prefs?.edit()
            ?.putString(KEY_USER_ID, user.id)
            ?.putString(KEY_USERNAME, user.username)
            ?.putString(KEY_EMAIL, user.email)
            ?.putString(KEY_AVATAR_URL, user.avatarUrl)
            ?.putString(KEY_ROLE, user.role)
            ?.putString(KEY_TIER, user.tier)
            ?.putString(KEY_TOKEN, user.token)
            ?.commit()
    }

    companion object {
        val DEFAULT_LOCAL_USER = AuthUser(
            id = "local_dev",
            username = "Developer",
            email = "developer@box.local",
            avatarUrl = null,
            role = "ADMIN",
            tier = "PRO",
            token = ""
        )

        private const val PREFS_AUTH_NAME = "box_auth_prefs"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_USERNAME = "auth_username"
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_AVATAR_URL = "auth_avatar_url"
        private const val KEY_ROLE = "auth_role"
        private const val KEY_TIER = "auth_tier"
        private const val KEY_TOKEN = "auth_token"
    }
}
