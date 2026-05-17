package com.matrix.multigpt.presentation

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.matrix.multigpt.BuildConfig
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

        // On debug builds, mark this device + emulator as a test device so AdMob
        // serves test ads against your real ad unit IDs. This prevents "ad failed
        // to load: 0" while new units are still warming up and avoids self-click
        // policy issues during development.
        //
        // After your first debug run, look in logcat for a line like:
        //   I/Ads: Use RequestConfiguration.Builder.setTestDeviceIds(Arrays.asList("ABCDEF...")) ...
        // Copy that hash into TEST_DEVICE_IDS below and rebuild — test ads will be guaranteed.
        if (BuildConfig.DEBUG) {
            val testDeviceIds = listOf(
                "EMULATOR",
                // TODO: paste your debug device's hashed ID from logcat here
            )
            MobileAds.setRequestConfiguration(
                MobileAds.getRequestConfiguration().toBuilder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()
            )
        }
    }
}
