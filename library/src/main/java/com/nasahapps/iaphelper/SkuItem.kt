package com.nasahapps.iaphelper

import com.android.billingclient.api.SkuDetails

/**
 * Created by hhasan on 8/22/17.
 */

internal data class SkuItem(var skuDetails: SkuDetails) : Comparable<SkuItem> {

    val title: String
        get() = when {
            skuDetails.title.contains("$1 USD") -> "$1 USD"
            skuDetails.title.contains("$2 USD") -> "$2 USD"
            skuDetails.title.contains("$5 USD") -> "$5 USD"
            skuDetails.title.contains("$10 USD") -> "$10 USD"
            skuDetails.title.contains("$20 USD") -> "$20 USD"
            skuDetails.title.contains("$50 USD") -> "$50 USD"
            skuDetails.title.contains("$100 USD") -> "$100 USD"
            else -> skuDetails.title
        }

    override fun compareTo(other: SkuItem): Int {
        return when {
            this.skuDetails.priceAmountMicros < other.skuDetails.priceAmountMicros -> -1
            this.skuDetails.priceAmountMicros > other.skuDetails.priceAmountMicros -> 1
            else -> 0
        }
    }

    companion object {

        fun listFromSkuDetailsList(detailsList: List<SkuDetails>): List<SkuItem> {
            return detailsList.map { SkuItem(it) }.sorted()
        }

        fun getArrayOfTitles(items: List<SkuItem>): Array<String> {
            return items.map { it.title }.toTypedArray()
        }

    }
}
