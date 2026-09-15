package com.box.android.data.local

import android.content.Context
import android.content.SharedPreferences

interface UserPreferencesRepository {
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
    fun isLanAccessEnabled(): Boolean
    fun setLanAccessEnabled(enabled: Boolean)
}

class UserPreferencesRepositoryImpl(context: Context? = null) : UserPreferencesRepository {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isCompletedInMemory: Boolean = false
    private var isLanEnabledInMemory: Boolean = true

    override fun isOnboardingCompleted(): Boolean {
        return prefs?.getBoolean(KEY_ONBOARDING_COMPLETED, false) ?: isCompletedInMemory
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        isCompletedInMemory = completed
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_COMPLETED, completed)?.commit()
    }

    override fun isLanAccessEnabled(): Boolean {
        return prefs?.getBoolean(KEY_LAN_ACCESS, true) ?: isLanEnabledInMemory
    }

    override fun setLanAccessEnabled(enabled: Boolean) {
        isLanEnabledInMemory = enabled
        prefs?.edit()?.putBoolean(KEY_LAN_ACCESS, enabled)?.commit()
    }

    companion object {
        private const val PREFS_NAME = "box_user_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LAN_ACCESS = "lan_access_enabled"
    }
}
