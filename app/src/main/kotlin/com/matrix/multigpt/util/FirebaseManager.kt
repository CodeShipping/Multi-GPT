package com.matrix.multigpt.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Firebase Analytics and Crashlytics operations
 * Provides centralized Firebase functionality for the app
 */
@Singleton
class FirebaseManager @Inject constructor() {
    
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    
    /**
     * Initialize Firebase services
     */
    fun initialize(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
        crashlytics = FirebaseCrashlytics.getInstance()
    }
    
    /**
     * Log custom events to Firebase Analytics
     */
    fun logEvent(eventName: String, parameters: Bundle? = null) {
        analytics?.logEvent(eventName, parameters)
    }
    
    /**
     * Log screen view events
     */
    fun logScreenView(screenName: String, screenClass: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }
    
    /**
     * Log user actions
     */
    fun logUserAction(action: String, item: String? = null) {
        val bundle = Bundle().apply {
            putString("action", action)
            item?.let { putString("item", it) }
        }
        logEvent("user_action", bundle)
    }
    
    /**
     * Log API usage events
     */
    fun logApiUsage(provider: String, model: String, messageCount: Int) {
        val bundle = Bundle().apply {
            putString("api_provider", provider)
            putString("model", model)
            putInt("message_count", messageCount)
        }
        logEvent("api_usage", bundle)
    }
    
    /**
     * Set user properties
     */
    fun setUserProperty(name: String, value: String) {
        analytics?.setUserProperty(name, value)
    }
    
    /**
     * Record non-fatal exceptions
     */
    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }
    
    /**
     * Log custom messages to Crashlytics
     */
    fun log(message: String) {
        crashlytics?.log(message)
    }
    
    /**
     * Set user identifier for crash reports
     */
    fun setUserId(userId: String) {
        crashlytics?.setUserId(userId)
    }
    
    /**
     * Set custom key-value pairs for crash reports
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }
    
    /**
     * Set custom key-value pairs for crash reports (boolean)
     */
    fun setCustomKey(key: String, value: Boolean) {
        crashlytics?.setCustomKey(key, value)
    }
    
    /**
     * Set custom key-value pairs for crash reports (int)
     */
    fun setCustomKey(key: String, value: Int) {
        crashlytics?.setCustomKey(key, value)
    }
}

/**
 * Firebase event constants for consistent logging
 */
object FirebaseEvents {
    // Screen events
    const val SCREEN_HOME = "home_screen"
    const val SCREEN_CHAT = "chat_screen"
    const val SCREEN_SETTINGS = "settings_screen"
    const val SCREEN_SETUP = "setup_screen"
    
    // User actions
    const val ACTION_NEW_CHAT = "new_chat"
    const val ACTION_SEND_MESSAGE = "send_message"
    const val ACTION_DELETE_CHAT = "delete_chat"
    const val ACTION_EXPORT_CHAT = "export_chat"
    const val ACTION_CHANGE_MODEL = "change_model"
    const val ACTION_CHANGE_SETTINGS = "change_settings"
    
    // Setup events
    const val SETUP_COMPLETE = "setup_complete"
    const val PLATFORM_SELECTED = "platform_selected"
    const val MODEL_SELECTED = "model_selected"
    
    // Error events
    const val API_ERROR = "api_error"
    const val MODEL_FETCH_ERROR = "model_fetch_error"
    const val NETWORK_ERROR = "network_error"
}
