package com.nasahapps.iaphelper

import android.app.Activity
import android.content.Context
import android.support.v7.app.AlertDialog
import android.util.Log
import com.android.billingclient.BuildConfig
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetailsParams
import java.lang.ref.WeakReference
import java.util.Arrays

/**
 * Created by hhasan on 8/22/17.
 */

class IapHelper(context: Context, private val callbacks: Callbacks? = null) : PurchasesUpdatedListener,
        BillingClientStateListener {

    private val billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .build()

    init {
        billingClient.startConnection(this)
    }

    fun isClientReady() = billingClient.isReady

    fun endConnection() {
        billingClient.endConnection()
    }

    fun getSkuDetails(activity: Activity) {
        val weakRefContext = WeakReference(activity)
        val params = SkuDetailsParams.newBuilder()
                .setSkusList(Arrays.asList(*activity.resources.getStringArray(R.array.donate_product_ids)))
                .setType(BillingClient.SkuType.INAPP)
        billingClient.querySkuDetailsAsync(params.build()) { responseCode, skuDetailsList ->
            weakRefContext.get()?.let { activity ->
                if (!activity.isFinishing) {
                    if (responseCode == BillingClient.BillingResponse.OK) {
                        skuDetailsList?.let { skuDetails ->
                            val items = SkuItem.listFromSkuDetailsList(skuDetails)
                            AlertDialog.Builder(activity)
                                    .setTitle(R.string.donate_choose_title)
                                    .setItems(SkuItem.getArrayOfTitles(items)) { dialogInterface, position ->
                                        weakRefContext.get()?.let {
                                            launchBillingFlow(it, items[position].skuDetails.sku)
                                        }
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                        }
                    } else {
                        AlertDialog.Builder(activity)
                                .setTitle(R.string.error_donate_setup_title)
                                .setMessage(activity.getString(R.string.error_donate_setup_message,
                                        responseCode, getResponseMessageForCode(responseCode)))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                    }
                }
            }
        }
    }

    private fun launchBillingFlow(activity: Activity, sku: String) {
        val params = BillingFlowParams.newBuilder()
                .setSku(sku)
                .setType(BillingClient.SkuType.INAPP)
                .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: List<Purchase>?) {
        if (responseCode != BillingClient.BillingResponse.OK) {
            logE("Error making purchase: " + getResponseMessageForCode(responseCode))
        }
        logD("Purchases updated: $responseCode, $purchases")
        if (purchases != null) {
            for (purchase in purchases) {
                consumePurchase(purchase.purchaseToken)
            }
        }
    }

    private fun consumePurchase(purchaseToken: String?) {
        if (purchaseToken != null) {
            logD("Purchase: " + purchaseToken)
            logD("Consuming purchase...")
            billingClient.consumeAsync(purchaseToken) { responseCode, purchaseToken ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    logD("Purchase $purchaseToken consumed")
                } else {
                    logE("Error consuming purchase: " + getResponseMessageForCode(responseCode))
                }
            }
        }
    }

    override fun onBillingSetupFinished(resultCode: Int) {
        logD("Billing setup finished: $resultCode")
        if (resultCode != BillingClient.BillingResponse.OK) {
            logE("Error setting up billing client, " + getResponseMessageForCode(resultCode))
        } else {
            // Consume any leftover purchases
            billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP) { responseCode, purchasesList ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    purchasesList?.forEach { consumePurchase(it.purchaseToken) }
                } else {
                    logE("Error getting purchase history: " + getResponseMessageForCode(responseCode))
                }
            }
        }
        callbacks?.onBillingSetupFinished(resultCode)
    }

    override fun onBillingServiceDisconnected() {
        logD("Billing service disconnected")
        callbacks?.onBillingServiceDisconnected()
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

    private fun getResponseMessageForCode(code: Int): String {
        val result: String
        when (code) {
            BillingClient.BillingResponse.BILLING_UNAVAILABLE -> {
                result = "Billing API version is not supported for the type requested"
            }
            BillingClient.BillingResponse.SERVICE_UNAVAILABLE -> result = "Network connection is down"
            BillingClient.BillingResponse.DEVELOPER_ERROR -> {
                result = "Invalid arguments provided to the API, or this app is not properly setup " +
                        "for IAP, or does not have the necessary permissions in the manifest"
            }
            BillingClient.BillingResponse.FEATURE_NOT_SUPPORTED -> {
                result = "IAP feature not supported on the current device"
            }
            BillingClient.BillingResponse.ITEM_ALREADY_OWNED -> result = "IAP item already owned"
            BillingClient.BillingResponse.ITEM_NOT_OWNED -> result = "IAP item not owned"
            BillingClient.BillingResponse.ITEM_UNAVAILABLE -> result = "IAP item unavailable for purchase"
            BillingClient.BillingResponse.SERVICE_DISCONNECTED -> result = "Billing service disconnected"
            BillingClient.BillingResponse.USER_CANCELED -> result = "User cancelled IAP process"
            BillingClient.BillingResponse.ERROR -> result = "Fatal error"
            else -> result = "Unknown error"
        }

        return result
    }

    interface Callbacks {
        fun onBillingSetupFinished(resultCode: Int)

        fun onBillingServiceDisconnected()
    }
}
