package com.nasahapps.iaphelper;

import android.support.annotation.NonNull;

import com.android.billingclient.api.SkuDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by hhasan on 8/22/17.
 */

class SkuItem implements Comparable<SkuItem> {

    SkuDetails skuDetails;

    public SkuItem(SkuDetails details) {
        this.skuDetails = details;
    }

    public static List<SkuItem> listFromSkuDetailsList(List<SkuDetails> detailsList) {
        List<SkuItem> list = new ArrayList<>();
        for (SkuDetails details : detailsList) {
            list.add(new SkuItem(details));
        }
        Collections.sort(list);
        return list;
    }

    public static String[] getArrayOfTitles(List<SkuItem> items) {
        String[] array = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            array[i] = items.get(i).getTitle();
        }
        return array;
    }

    public String getTitle() {
        if (skuDetails.getTitle().contains("$1 USD")) {
            return "$1 USD";
        } else if (skuDetails.getTitle().contains("$2 USD")) {
            return "$2 USD";
        } else if (skuDetails.getTitle().contains("$5 USD")) {
            return "$5 USD";
        } else if (skuDetails.getTitle().contains("$10 USD")) {
            return "$10 USD";
        } else if (skuDetails.getTitle().contains("$20 USD")) {
            return "$20 USD";
        } else if (skuDetails.getTitle().contains("$50 USD")) {
            return "$50 USD";
        } else if (skuDetails.getTitle().contains("$100 USD")) {
            return "$100 USD";
        } else {
            return skuDetails.getTitle();
        }
    }

    @Override
    public int compareTo(@NonNull SkuItem skuItem) {
        if (this.skuDetails.getPriceAmountMicros() < skuItem.skuDetails.getPriceAmountMicros()) {
            return -1;
        } else if (this.skuDetails.getPriceAmountMicros() > skuItem.skuDetails.getPriceAmountMicros()) {
            return 1;
        } else {
            return 0;
        }
    }
}
