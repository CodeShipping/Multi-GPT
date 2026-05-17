package com.matrix.multigpt.billing

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Reusable billing manager for in-app purchases.
 * Handles connection, purchase flow, restore, and entitlement checks for the
 * one-time `ad_free` SKU.
 *
 * Usage:
 *   BillingManager.init(context)              // call from Application.onCreate()
 *   BillingManager.isAdFree(context)          // check entitlement
 *   BillingManager.purchase(activity, BillingManager.SKU_AD_FREE)
 *   BillingManager.restorePurchases(context) { found -> ... }
 *
 * Entitlement is cached in SharedPreferences so checks are synchronous and cheap.
 */
object BillingManager {

    private const val TAG = "BillingManager"

    /** Product ID for the one-time ad-removal purchase. Must match Play Console exactly. */
    const val SKU_AD_FREE = "ad_free"

    private const val PREFS_NAME = "billing"
    private const val PREF_AD_FREE = "billing_ad_free"

    private var billingClient: BillingClient? = null
    private val productDetailsCache = mutableMapOf<String, ProductDetails>()
    private var onPurchaseComplete: ((Boolean) -> Unit)? = null
    private var appContext: Context? = null
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        analytics = analytics ?: FirebaseAnalytics.getInstance(context.applicationContext)
        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases(context)
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU_AD_FREE)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.forEach { productDetailsCache[it.productId] = it }
            } else {
                Log.w(TAG, "queryProducts failed: ${result.debugMessage}")
            }
        }
    }

    private fun queryPurchases(context: Context) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            // Reset and re-derive from authoritative server state
            prefs.putBoolean(PREF_AD_FREE, false)
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase)
                    if (purchase.products.contains(SKU_AD_FREE)) {
                        prefs.putBoolean(PREF_AD_FREE, true)
                    }
                }
            }
            prefs.apply()
        }
    }

    fun purchase(activity: Activity, sku: String, callback: ((Boolean) -> Unit)? = null) {
        onPurchaseComplete = callback
        val details = productDetailsCache[sku]
        if (details == null) {
            // Products may not be loaded yet — retry once after a short delay
            Log.w(TAG, "Product $sku not found, retrying query...")
            if (billingClient?.isReady == true) {
                queryProducts()
                Handler(Looper.getMainLooper()).postDelayed({
                    val retryDetails = productDetailsCache[sku]
                    if (retryDetails != null) {
                        launchPurchaseFlow(activity, retryDetails)
                    } else {
                        Log.d(TAG, "Product $sku unavailable — create it in Play Console and install from a test track.")
                        callback?.invoke(false)
                    }
                }, 2_000)
            } else {
                Log.e(TAG, "Billing client not ready")
                callback?.invoke(false)
            }
            return
        }
        launchPurchaseFlow(activity, details)
    }

    private fun launchPurchaseFlow(activity: Activity, details: ProductDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient?.launchBillingFlow(activity, flowParams)
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                purchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                        savePurchase(purchase)
                        logAnalytics("purchase_success", purchase.products.joinToString(","))
                        onPurchaseComplete?.invoke(true)
                    }
                }
            }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                logAnalytics("purchase_cancelled", null)
                onPurchaseComplete?.invoke(false)
            }
            else -> {
                logAnalytics("purchase_failed", "code_${result.responseCode}")
                onPurchaseComplete?.invoke(false)
            }
        }
        onPurchaseComplete = null
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) { /* fire and forget */ }
    }

    private fun savePurchase(purchase: Purchase) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (purchase.products.contains(SKU_AD_FREE)) {
            prefs.putBoolean(PREF_AD_FREE, true)
        }
        prefs.apply()
    }

    fun restorePurchases(context: Context, callback: (Boolean) -> Unit) {
        if (billingClient?.isReady != true) {
            callback(false)
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                callback(false)
                return@queryPurchasesAsync
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            var found = false
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(SKU_AD_FREE)
                ) {
                    found = true
                    acknowledgePurchase(purchase)
                    prefs.putBoolean(PREF_AD_FREE, true)
                }
            }
            prefs.apply()
            callback(found)
        }
    }

    /** Synchronous, cached entitlement check — safe to call from any thread. */
    fun isAdFree(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AD_FREE, false)
    }

    /** Localized, currency-correct price string from Google Play (e.g. "$2.99"). Null if not yet loaded. */
    fun getPrice(sku: String): String? {
        return productDetailsCache[sku]?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    private fun logAnalytics(event: String, sku: String?) {
        val fa = analytics ?: return
        val bundle = Bundle().apply {
            putString("event_type", event)
            sku?.let { putString("product_id", it) }
        }
        fa.logEvent("billing_$event", bundle)
    }

    fun logUpgradeScreenOpened() {
        val fa = analytics ?: return
        val bundle = Bundle().apply { putString("source", "upgrade_screen") }
        fa.logEvent("upgrade_screen_opened", bundle)
    }
}
