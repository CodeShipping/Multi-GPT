package com.matrix.multigpt.util

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.matrix.multigpt.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager for AdMob initialization and ad operations
 */
object AdMobManager {
    private val isInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    /**
     * Initialize AdMob SDK
     * Should be called in Application.onCreate()
     */
    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            CoroutineScope(Dispatchers.Main).launch {
                MobileAds.initialize(context)
            }
        }
    }

    /**
     * Load an interstitial ad
     */
    fun loadInterstitialAd(context: Context, adUnitIdRes: Int, onAdLoaded: () -> Unit = {}, onAdFailed: (String) -> Unit = {}) {
        if (isLoadingInterstitial) return
        
        isLoadingInterstitial = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(adUnitIdRes)

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    onAdFailed(adError.message)
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    onAdLoaded()
                }
            }
        )
    }

    /**
     * Show interstitial ad if loaded, load if not available
     */
    fun showInterstitialAd(activity: Activity, adUnitIdRes: Int, onAdDismissed: () -> Unit = {}) {
        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onAdDismissed()
                    // Preload next ad
                    loadInterstitialAd(activity, adUnitIdRes)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                }

                override fun onAdShowedFullScreenContent() {
                    // Ad showed successfully
                }
            }
            ad.show(activity)
        } ?: run {
            // Ad not loaded, reload for next time
            loadInterstitialAd(activity, adUnitIdRes)
            onAdDismissed()
        }
    }

    /**
     * Check if interstitial ad is ready
     */
    fun isInterstitialAdReady(): Boolean = interstitialAd != null
}

/**
 * Frequency limiter for ads
 */
class AdFrequencyLimiter {
    private var lastInterstitialTime: Long = 0
    private val minInterstitialInterval = 10 * 60 * 1000L // 10 minutes

    fun canShowInterstitial(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastInterstitialTime) >= minInterstitialInterval
    }

    fun markInterstitialShown() {
        lastInterstitialTime = System.currentTimeMillis()
    }
}

/**
 * Composable to preload interstitial ad
 */
@Composable
fun PreloadInterstitialAd() {
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        if (!AdMobManager.isInterstitialAdReady()) {
            AdMobManager.loadInterstitialAd(context, R.string.setup_complete_interstitial)
        }
        onDispose { }
    }
}
