package com.nasahapps.iaphelper

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.BuildConfig
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams

/**
 * Created by hhasan on 8/22/17.
 */

class IapHelper(context: Context,
                lifecycle: Lifecycle,
                private val statusCallback: (Boolean, Int) -> Unit) : PurchasesUpdatedListener,
        BillingClientStateListener, DefaultLifecycleObserver {

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
            .enablePendingPurchases()
            .setListener(this)
            .build()
    val isClientReady: Boolean
        get() = billingClient.isReady
    private var isLifecycleValid = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        isLifecycleValid = true
        logD("Starting billing client connection...")
        billingClient.startConnection(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        isLifecycleValid = false
        if (isClientReady) {
            logD("Ending billing client connection...")
            billingClient.endConnection()
        }
    }

    fun getSkuDetails(activity: Activity) {
        if (isLifecycleValid) {
            val params = SkuDetailsParams.newBuilder()
                    .setSkusList(listOf(*activity.resources.getStringArray(R.array.donate_product_ids)))
                    .setType(BillingClient.SkuType.INAPP)
            billingClient.querySkuDetailsAsync(params.build()) { result, skuDetailsList ->
                if (isLifecycleValid) {
                    if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
                        skuDetailsList?.let { skuDetails ->
                            val items = skuDetails.toSkuItemList()
                            AlertDialog.Builder(activity)
                                    .setTitle(R.string.donate_choose_title)
                                    .setItems(SkuItem.getArrayOfTitles(items)) { _, position ->
                                        launchBillingFlow(activity, items[position].skuDetails)
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                        }
                    } else {
                        AlertDialog.Builder(activity)
                                .setTitle(R.string.error_donate_setup_title)
                                .setMessage(activity.getString(R.string.error_donate_setup_message,
                                        result?.responseCode, getResponseMessageForBillingResult(result)))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                    }
                }
            }
        }
    }

    private fun launchBillingFlow(activity: Activity, skuDetails: SkuDetails) {
        if (isLifecycleValid) {
            val params = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails)
                    .build()
            billingClient.launchBillingFlow(activity, params)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult?,
                                    purchases: MutableList<Purchase>?) {
        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            logE("Error making purchase: " + getResponseMessageForBillingResult(billingResult))
        }
        logD("Purchases updated: ${billingResult?.responseCode}, $purchases")
        purchases?.forEach {
            val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(it.purchaseToken)
                    .build()
            consumePurchase(consumeParams)
        }
    }

    private fun consumePurchase(consumeParams: ConsumeParams?) {
        if (consumeParams != null) {
            logD("Consume params: $consumeParams")
            logD("Consuming purchase...")
            billingClient.consumeAsync(consumeParams) { result, token ->
                if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
                    logD("Purchase $token consumed")
                } else {
                    logE("Error consuming purchase: " + getResponseMessageForBillingResult(result))
                }
            }
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult?) {
        logD("Billing setup finished: ${billingResult?.responseCode}")
        if (billingResult?.responseCode != BillingClient.BillingResponseCode.OK) {
            logE("Error setting up billing client, " + getResponseMessageForBillingResult(billingResult))
        } else {
            // Consume any leftover purchases
            billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP) { responseCode, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchasesList?.forEach {
                        val consumeParams = ConsumeParams.newBuilder()
                                .setPurchaseToken(it.purchaseToken)
                                .build()
                        consumePurchase(consumeParams)
                    }
                } else {
                    logE("Error getting purchase history: " + getResponseMessageForBillingResult(responseCode))
                }
            }
        }

        statusCallback(billingResult?.responseCode == BillingClient.BillingResponseCode.OK,
                billingResult?.responseCode ?: BillingClient.BillingResponseCode.ERROR)
    }

    override fun onBillingServiceDisconnected() {
        logD("Billing service disconnected")
        statusCallback(false, BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
    }

    private fun logD(log: String) {
        if (BuildConfig.DEBUG) {
            Log.d(javaClass.simpleName, log)
        }
    }

    private fun logE(log: String) {
        if (BuildConfig.DEBUG) {
            Log.e(javaClass.simpleName, log)
        }
    }

    private fun getResponseMessageForBillingResult(result: BillingResult?): String {
        return when (result?.responseCode) {
            BillingClient.BillingResponseCode.OK -> "Transaction successful"
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                "Billing API version is not supported for the type requested"
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Network connection is down"
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                "Invalid arguments provided to the API, or this app is not properly setup " +
                        "for IAP, or does not have the necessary permissions in the manifest"
            }
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                "In-app purchases are not supported on this device"
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "IAP item already owned"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "IAP item not owned"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "IAP item unavailable for purchase"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "Billing service disconnected"
            BillingClient.BillingResponseCode.USER_CANCELED -> "User cancelled IAP process"
            BillingClient.BillingResponseCode.ERROR -> "Fatal error"
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT -> "Billing service timed out"
            else -> "Unknown error"
        }
    }
}
