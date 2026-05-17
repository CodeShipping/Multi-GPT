package com.matrix.multigpt.util

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manager for AdMob initialization and ad operations.
 * Supports multiple interstitial ad units keyed by their string resource id,
 * so different placements (setup-complete, new-chat, etc.) don't clobber each other.
 */
object AdMobManager {
    private val isInitialized = AtomicBoolean(false)

    // Per-ad-unit interstitial cache and loading flags
    private val interstitialAds = ConcurrentHashMap<Int, InterstitialAd>()
    private val loadingFlags = ConcurrentHashMap<Int, Boolean>()

    // Frequency counter for "show every Nth tap" use-cases (e.g. new-chat interstitial)
    private val newChatTapCount = AtomicInteger(0)

    /**
     * Initialize AdMob SDK.
     * Should be called in Application.onCreate().
     */
    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            CoroutineScope(Dispatchers.Main).launch {
                MobileAds.initialize(context)
            }
        }
    }

    /**
     * Load an interstitial ad for the given ad unit res id.
     * No-op if already loaded or currently loading.
     */
    fun loadInterstitialAd(
        context: Context,
        @StringRes adUnitIdRes: Int,
        onAdLoaded: () -> Unit = {},
        onAdFailed: (String) -> Unit = {}
    ) {
        if (loadingFlags[adUnitIdRes] == true) return
        if (interstitialAds[adUnitIdRes] != null) return

        loadingFlags[adUnitIdRes] = true
        val adRequest = AdRequest.Builder().build()
        val adUnitId = context.getString(adUnitIdRes)

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loadingFlags[adUnitIdRes] = false
                    interstitialAds.remove(adUnitIdRes)
                    onAdFailed(adError.message)
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingFlags[adUnitIdRes] = false
                    interstitialAds[adUnitIdRes] = ad
                    onAdLoaded()
                }
            }
        )
    }

    /**
     * Show interstitial ad if loaded; if not, kick off a load and call onAdDismissed
     * immediately so the calling flow doesn't block.
     */
    fun showInterstitialAd(
        activity: Activity,
        @StringRes adUnitIdRes: Int,
        onAdDismissed: () -> Unit = {}
    ) {
        val ad = interstitialAds[adUnitIdRes]
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAds.remove(adUnitIdRes)
                    onAdDismissed()
                    // Preload next ad of the same unit
                    loadInterstitialAd(activity, adUnitIdRes)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAds.remove(adUnitIdRes)
                    onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    // Ad showed successfully
                }
            }
            ad.show(activity)
        } else {
            // Ad not loaded, reload for next time and let the caller continue
            loadInterstitialAd(activity, adUnitIdRes)
            onAdDismissed()
        }
    }

    /**
     * Check if a specific interstitial ad unit is loaded and ready to show.
     */
    fun isInterstitialAdReady(@StringRes adUnitIdRes: Int): Boolean =
        interstitialAds[adUnitIdRes] != null

    /**
     * Increments the new-chat tap counter; returns true on every Nth tap (default every 4th).
     * Caller should preload the new-chat interstitial in advance and only show when this returns true.
     */
    fun shouldShowNewChatInterstitial(every: Int = 4): Boolean {
        val count = newChatTapCount.incrementAndGet()
        return count > 0 && count % every == 0
    }
}

/**
 * Frequency limiter for ads (time-based)
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
 * Composable to preload an interstitial ad. Defaults to the setup-complete interstitial
 * for backward compatibility; pass a different ad unit res id to preload other slots.
 */
@Composable
fun PreloadInterstitialAd(
    @StringRes adUnitIdRes: Int = R.string.setup_complete_interstitial
) {
    val context = LocalContext.current

    DisposableEffect(adUnitIdRes) {
        if (!AdMobManager.isInterstitialAdReady(adUnitIdRes)) {
            AdMobManager.loadInterstitialAd(context, adUnitIdRes)
        }
        onDispose { }
    }
}
