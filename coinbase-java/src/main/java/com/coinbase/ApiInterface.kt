package com.coinbase

import com.coinbase.auth.AccessToken
import com.coinbase.v2.models.account.Account
import com.coinbase.v2.models.account.Accounts
import com.coinbase.v2.models.address.Address
import com.coinbase.v2.models.exchangeRates.ExchangeRates
import com.coinbase.v2.models.paymentMethods.PaymentMethod
import com.coinbase.v2.models.paymentMethods.PaymentMethods
import com.coinbase.v2.models.price.Price
import com.coinbase.v2.models.price.Prices
import com.coinbase.v2.models.supportedCurrencies.SupportedCurrencies
import com.coinbase.v2.models.transactions.Transaction
import com.coinbase.v2.models.transactions.Transactions
import com.coinbase.v2.models.transfers.Transfer
import com.coinbase.v2.models.user.User
import retrofit2.Call
import retrofit2.http.*

interface ApiInterface {

    @get:GET(ApiConstants.USER)
    val user: Call<User>

    @get:GET(ApiConstants.CURRENCIES)
    val supportedCurrencies: Call<SupportedCurrencies>

    @POST(ApiConstants.TOKEN)
    fun getTokens(@Body body: Map<String, String>): Call<AccessToken>

    @POST(ApiConstants.TOKEN)
    fun refreshTokens(@Body body: Map<String, String>): Call<AccessToken>

    @POST(ApiConstants.REVOKE)
    fun revokeToken(@Body body: Map<String, String>): Call<Void>

    @PUT(ApiConstants.USER)
    fun updateUser(@Body body: Map<String, String>): Call<User>

    @GET(ApiConstants.ACCOUNTS + "/{id}")
    fun getAccount(@Path("id") accountId: String): Call<Account>

    @GET(com.coinbase.ApiConstants.ACCOUNTS)
    fun getAccounts(@QueryMap options: Map<String, String>): Call<Accounts>

    @POST(ApiConstants.ACCOUNTS)
    fun createAccount(@Body body: Map<String, String>): Call<Account>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.PRIMARY)
    fun setAccountPrimary(@Path("id") accountId: String): Call<Void>

    @PUT(ApiConstants.ACCOUNTS + "/{id}")
    fun updateAccount(@Path("id") acountId: String, @Body body: Map<String, String>): Call<Account>

    @DELETE(ApiConstants.ACCOUNTS + "/{id}")
    fun deleteAccount(@Path("id") accountId: String): Call<Void>

    @GET(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun getTransactions(@Path("id") accountId: String,
                        @Query("expand[]") expandOptions: List<String>,
                        @QueryMap options: Map<String, String>): Call<Transactions>

    @GET(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}")
    fun getTransaction(@Path("account_id") accountId: String,
                       @Path("transaction_id") transactionId: String,
                       @Query("expand[]") expandOptions: List<String>): Call<Transaction>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}/" + com.coinbase
            .ApiConstants.COMPLETE)
    fun completeRequest(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Call<Void>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}/" + com.coinbase
            .ApiConstants.RESEND)
    fun resendRequest(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Call<Void>

    @DELETE(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}")
    fun cancelTransaction(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Call<Void>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun sendMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Call<Transaction>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun requestMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Call<Transaction>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun transferMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Call<Transaction>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.SELL)
    fun getSellPrice(@Path("base_currency") baseCurrency: String,
                     @Path("fiat_currency") fiatCurrency: String,
                     @QueryMap body: Map<String, String>): Call<Price>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.BUY)
    fun getBuyPrice(@Path("base_currency") baseCurrency: String,
                    @Path("fiat_currency") fiatCurrency: String,
                    @QueryMap body: Map<String, String>): Call<Price>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.SPOT)
    fun getSpotPrice(@Path("base_currency") baseCurrency: String,
                     @Path("fiat_currency") fiatCurrency: String,
                     @QueryMap body: Map<String, String>): Call<Price>

    @GET(com.coinbase.ApiConstants.PRICES + "/{fiat_currency}/" + ApiConstants.SPOT)
    fun getSpotPrices(@Path("fiat_currency") fiatCurrency: String,
                      @QueryMap body: Map<String, String>): Call<Prices>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.BUYS)
    fun buyBitcoin(@Path("id") accountId: String, @Body body: Map<String, String>): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.BUYS + "/{buy_id}/" + ApiConstants.COMMIT)
    fun commitBuyBitcoin(@Path("account_id") accountId: String, @Path("buy_id") buyId: String): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.SELLS)
    fun sellBitcoin(@Path("id") accountId: String, @Body body: Map<String, String>): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.SELLS + "/{sell_id}/" + ApiConstants.COMMIT)
    fun commitSellBitcoin(@Path("account_id") accountId: String, @Path("sell_id") sellId: String): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.ADDRESSES)
    fun generateAddress(@Path("account_id") accoundId: String): Call<Address>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.DEPOSITS)
    fun depositFunds(@Path("account_id") accountId: String, @Body body: Map<String, String>): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.DEPOSITS + "/{deposit_id}/" + ApiConstants.COMMIT)
    fun commitDeposit(@Path("account_id") accountId: String, @Path("deposit_id") depositId: String): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.WITHDRAWALS)
    fun withdrawFunds(@Path("account_id") accountId: String, @Body body: Map<String, String>): Call<Transfer>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.WITHDRAWALS + "/{withdrawal_id}/" + ApiConstants.COMMIT)
    fun commitWithdraw(@Path("account_id") accountId: String, @Path("withdrawal_id") depositId: String): Call<Transfer>

    @GET(ApiConstants.PAYMENT_METHODS)
    fun getPaymentMethods(@QueryMap body: Map<String, String>): Call<PaymentMethods>

    @GET(ApiConstants.PAYMENT_METHODS + "/{id}")
    fun getPaymentMethod(@Path("id") paymentMethodId: String): Call<PaymentMethod>

    @GET(ApiConstants.EXCHANGE_RATES)
    fun getExchangeRates(@QueryMap body: Map<String, String>): Call<ExchangeRates>
}
