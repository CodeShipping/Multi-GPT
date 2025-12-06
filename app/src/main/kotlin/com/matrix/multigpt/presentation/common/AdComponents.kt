package com.matrix.multigpt.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.matrix.multigpt.R

/**
 * Banner ad composable that can be placed at the bottom of screens
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner,
    adSize: AdSize = AdSize.BANNER
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = context.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/**
 * Large banner ad for bottom of screens with more space
 */
@Composable
fun LargeBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    BannerAd(
        modifier = modifier,
        adUnitIdRes = adUnitIdRes,
        adSize = AdSize.LARGE_BANNER
    )
}

/**
 * Smart banner that adapts to screen size
 */
@Composable
fun SmartBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.SMART_BANNER)
                adUnitId = context.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/**
 * Adaptive banner ad that adjusts to screen width
 */
@Composable
fun AdaptiveBannerAd(
    modifier: Modifier = Modifier,
    adUnitIdRes: Int = R.string.home_banner
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            val displayMetrics = context.resources.displayMetrics
            val adWidthPixels = displayMetrics.widthPixels
            val density = displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
                adUnitId = context.getString(adUnitIdRes)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
