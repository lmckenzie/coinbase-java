package com.coinbase.v2.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

import javax.annotation.Generated

@Suppress("unused")
@Generated("org.jsonschema2pojo")
class Pagination @JvmOverloads constructor(
        @SerializedName("ending_before") @Expose var endingBefore: String? = null,
        @SerializedName("starting_after") @Expose var startingAfter: String? = null,
        @SerializedName("limit") @Expose var limit: Int? = null,
        @SerializedName("order") @Expose var order: String? = null,
        @SerializedName("previous_uri") @Expose var previousUri: String? = null,
        @SerializedName("next_uri") @Expose var nextUri: String? = null
)