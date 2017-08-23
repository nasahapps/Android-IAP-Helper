package com.nasahapps.iaphelper;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.android.billingclient.BuildConfig;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

/**
 * Created by hhasan on 8/22/17.
 */

public class IapHelper implements PurchasesUpdatedListener, BillingClientStateListener {

    private BillingClient mBillingClient;
    @Nullable
    private Callbacks mCallbacks;

    public IapHelper(Context context, @Nullable Callbacks callbacks) {
        mBillingClient = new BillingClient.Builder(context)
                .setListener(this)
                .build();
        mBillingClient.startConnection(this);
        mCallbacks = callbacks;
    }

    public void endConnection() {
        mBillingClient.endConnection();
    }

    public boolean isClientReady() {
        return mBillingClient.isReady();
    }

    public void getSkuDetails(Activity activity) {
        final WeakReference<Activity> weakRefContext = new WeakReference<>(activity);
        mBillingClient.querySkuDetailsAsync(BillingClient.SkuType.INAPP,
                Arrays.asList(activity.getResources().getStringArray(R.array.donate_product_ids)),
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(SkuDetails.SkuDetailsResult result) {
                        if (weakRefContext.get() != null && result.getSkuDetailsList() != null) {
                            Activity activity = weakRefContext.get();
                            if (result.getResponseCode() == BillingClient.BillingResponse.OK) {
                                final List<SkuItem> items = SkuItem.listFromSkuDetailsList(result.getSkuDetailsList());
                                new AlertDialog.Builder(activity)
                                        .setTitle(R.string.donate_choose_title)
                                        .setItems(SkuItem.getArrayOfTitles(items), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int position) {
                                                if (weakRefContext.get() != null) {
                                                    launchBillingFlow(weakRefContext.get(), items.get(position).skuDetails.getSku());
                                                }
                                            }
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();
                            } else {
                                new AlertDialog.Builder(activity)
                                        .setTitle(R.string.error_donate_setup_title)
                                        .setMessage(activity.getString(R.string.error_donate_setup_message,
                                                result.getResponseCode(), getResponseMessageForCode(result.getResponseCode())))
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            }
                        }
                    }
                });
    }

    private void launchBillingFlow(Activity activity, String sku) {
        BillingFlowParams params = new BillingFlowParams.Builder()
                .setSku(sku)
                .setType(BillingClient.SkuType.INAPP)
                .build();
        mBillingClient.launchBillingFlow(activity, params);
    }

    @Override
    public void onPurchasesUpdated(int responseCode, List<Purchase> purchases) {
        if (responseCode != BillingClient.BillingResponse.OK) {
            logE("Error making purchase: " + getResponseMessageForCode(responseCode));
        }
        logD("Purchases updated: " + responseCode + ", " + purchases);
        if (purchases != null) {
            for (Purchase purchase : purchases) {
                consumePurchase(purchase.getPurchaseToken());
            }
        }
    }

    private void consumePurchase(@Nullable String purchaseToken) {
        if (purchaseToken != null) {
            logD("Purchase: " + purchaseToken);
            logD("Consuming purchase...");
            mBillingClient.consumeAsync(purchaseToken, new ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(String purchaseToken, int resultCode) {
                    if (resultCode == BillingClient.BillingResponse.OK) {
                        logD("Purchase " + purchaseToken + " consumed");
                    } else {
                        logE("Error consuming purchase: " + getResponseMessageForCode(resultCode));
                    }
                }
            });
        }
    }

    @Override
    public void onBillingSetupFinished(int resultCode) {
        logD("Billing setup finished: " + resultCode);
        if (resultCode != BillingClient.BillingResponse.OK) {
            logE("Error setting up billing client, " + getResponseMessageForCode(resultCode));
        } else {
            // Consume any leftover purchases
            mBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, new PurchaseHistoryResponseListener() {
                @Override
                public void onPurchaseHistoryResponse(Purchase.PurchasesResult result) {
                    if (result.getResponseCode() == BillingClient.BillingResponse.OK
                            && result.getPurchasesList() != null) {
                        for (Purchase purchase : result.getPurchasesList()) {
                            consumePurchase(purchase.getPurchaseToken());
                        }
                    } else {
                        logE("Error getting purchase history: " + getResponseMessageForCode(result.getResponseCode()));
                    }
                }
            });
        }
        if (mCallbacks != null) {
            mCallbacks.onBillingSetupFinished(resultCode);
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        logD("Billing service disconnected");
        if (mCallbacks != null) {
            mCallbacks.onBillingServiceDisconnected();
        }
    }

    private void logD(String log) {
        if (BuildConfig.DEBUG) {
            Log.d(getClass().getSimpleName(), log);
        }
    }

    private void logE(String log) {
        if (BuildConfig.DEBUG) {
            Log.e(getClass().getSimpleName(), log);
        }
    }

    private String getResponseMessageForCode(int code) {
        String result;
        switch (code) {
            case BillingClient.BillingResponse.BILLING_UNAVAILABLE:
            case BillingClient.BillingResponse.SERVICE_UNAVAILABLE:
                result = "Billing service unavailable";
                break;
            case BillingClient.BillingResponse.DEVELOPER_ERROR:
                result = "Developer error";
                break;
            case BillingClient.BillingResponse.FEATURE_NOT_SUPPORTED:
                result = "IAP feature not supported";
                break;
            case BillingClient.BillingResponse.ITEM_ALREADY_OWNED:
                result = "IAP item already owned";
                break;
            case BillingClient.BillingResponse.ITEM_NOT_OWNED:
                result = "IAP item not owned";
                break;
            case BillingClient.BillingResponse.ITEM_UNAVAILABLE:
                result = "IAP item unavailable";
                break;
            case BillingClient.BillingResponse.SERVICE_DISCONNECTED:
                result = "Billing service disconnected";
                break;
            case BillingClient.BillingResponse.USER_CANCELED:
                result = "User cancelled IAP process";
                break;
            default:
                result = "Unknown error";
        }

        return result;
    }

    public interface Callbacks {
        void onBillingSetupFinished(int resultCode);

        void onBillingServiceDisconnected();
    }
}
