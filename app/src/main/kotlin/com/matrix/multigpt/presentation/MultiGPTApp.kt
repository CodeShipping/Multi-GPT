package com.matrix.multigpt.presentation

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.matrix.multigpt.util.AdMobManager
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltAndroidApp
class MultiGPTApp : Application() {
    @Inject
    @ApplicationContext
    lateinit var context: Context

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize AdMob
        AdMobManager.initialize(this)
    }
}
