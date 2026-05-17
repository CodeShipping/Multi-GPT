package com.matrix.multigpt.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.matrix.multigpt.R
import com.matrix.multigpt.billing.BillingManager

/**
 * Banner ad composable. Renders nothing when the user has purchased ad-free.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner,
    adSize: AdSize = AdSize.BANNER
) {
    val context = LocalContext.current
    if (BillingManager.isAdFree(context)) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adSize)
                adUnitId = ctx.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/**
 * Large banner ad for screens with more vertical room.
 */
@Composable
fun LargeBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    val context = LocalContext.current
    if (BillingManager.isAdFree(context)) return

    BannerAd(
        modifier = modifier,
        adUnitIdRes = adUnitIdRes,
        adSize = AdSize.LARGE_BANNER
    )
}

/**
 * Smart banner that adapts to screen size.
 */
@Composable
fun SmartBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    val context = LocalContext.current
    if (BillingManager.isAdFree(context)) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.SMART_BANNER)
                adUnitId = ctx.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/**
 * Adaptive banner ad that adjusts to screen width. Recommended default.
 * Renders nothing when the user has purchased ad-free.
 */
@Composable
fun AdaptiveBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    val context = LocalContext.current
    if (BillingManager.isAdFree(context)) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val displayMetrics = ctx.resources.displayMetrics
            val adWidthPixels = displayMetrics.widthPixels
            val density = displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()

            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth))
                adUnitId = ctx.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
