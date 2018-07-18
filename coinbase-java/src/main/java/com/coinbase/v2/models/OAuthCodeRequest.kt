package com.coinbase.v2.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

import org.joda.money.Money

import java.io.Serializable

@Suppress("unused")
class OAuthCodeRequest : Serializable {

    companion object {
        private const val serialVersionUID = 3716938132337502204L
    }

    var clientId: String? = null
    var clientSecret: String? = null
    var username: String? = null
    var password: String? = null
    var referrerId: String? = null
    var token: String? = null
    var scope: String? = null
    var redirectUri: String? = null

    var meta: Meta = Meta()

    class Meta : Serializable {

        companion object {
            private const val serialVersionUID = -5468361596726979847L
        }

        var name: String? = null
        var sendLimitAmount: Money? = null
        var sendLimitPeriod: Period? = null
        val sendLimitCurrency: String?
            get() = sendLimitAmount?.currencyUnit?.currencyCode

        enum class Period(private val value: String) {
            DAILY("daily"),
            WEEKLY("weekly"),
            MONTHLY("monthly");

            @JsonValue
            override fun toString(): String {
                return this.value
            }

            companion object {

                @JsonCreator
                fun create(value: String): Period? {
                    return Period.values().first { it.toString().equals(value, ignoreCase = true) }
                }
            }
        }
    }
}
