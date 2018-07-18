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
import retrofit2.Response
import retrofit2.http.*
import rx.Observable

interface ApiInterfaceRx {

    @get:GET(ApiConstants.USER)
    val user: Observable<Response<User>>

    @get:GET(ApiConstants.CURRENCIES)
    val supportedCurrencies: Observable<Response<SupportedCurrencies>>

    @POST(ApiConstants.TOKEN)
    fun getTokens(@Body body: Map<String, String>): Observable<Response<AccessToken>>

    @POST(ApiConstants.TOKEN)
    fun refreshTokens(@Body body: Map<String, String>): Observable<Response<AccessToken>>

    @POST(ApiConstants.REVOKE)
    fun revokeToken(@Body body: Map<String, String>): Observable<Response<Void>>

    @PUT(ApiConstants.USER)
    fun updateUser(@Body body: Map<String, String>): Observable<Response<User>>

    @GET(ApiConstants.ACCOUNTS + "/{id}")
    fun getAccount(@Path("id") accountId: String): Observable<Response<Account>>

    @GET(com.coinbase.ApiConstants.ACCOUNTS)
    fun getAccounts(@QueryMap options: Map<String, String>): Observable<Response<Accounts>>

    @POST(ApiConstants.ACCOUNTS)
    fun createAccount(@Body body: Map<String, String>): Observable<Response<Account>>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.PRIMARY)
    fun setAccountPrimary(@Path("id") accountId: String): Observable<Response<Void>>

    @PUT(ApiConstants.ACCOUNTS + "/{id}")
    fun updateAccount(@Path("id") acountId: String, @Body body: Map<String, String>): Observable<Response<Account>>

    @DELETE(ApiConstants.ACCOUNTS + "/{id}")
    fun deleteAccount(@Path("id") accountId: String): Observable<Response<Void>>

    @GET(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun getTransactions(@Path("id") accountId: String,
                        @Query("expand[]") expandOptions: List<String>,
                        @QueryMap options: Map<String, String>): Observable<Response<Transactions>>

    @GET(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}")
    fun getTransaction(@Path("account_id") accountId: String,
                       @Path("transaction_id") transactionId: String,
                       @Query("expand[]") expandOptions: List<String>): Observable<Response<Transaction>>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}/" + com.coinbase.ApiConstants.COMPLETE)
    fun completeRequest(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Observable<Response<Void>>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}/" + com.coinbase.ApiConstants.RESEND)
    fun resendRequest(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Observable<Response<Void>>

    @DELETE(com.coinbase.ApiConstants.ACCOUNTS + "/{account_id}/" + com.coinbase.ApiConstants.TRANSACTIONS + "/{transaction_id}")
    fun cancelTransaction(@Path("account_id") accountId: String, @Path("transaction_id") transactionId: String): Observable<Response<Void>>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun sendMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transaction>>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun requestMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transaction>>

    @POST(com.coinbase.ApiConstants.ACCOUNTS + "/{id}/" + com.coinbase.ApiConstants.TRANSACTIONS)
    fun transferMoney(@Path("id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transaction>>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.SELL)
    fun getSellPrice(@Path("base_currency") baseCurrency: String,
                     @Path("fiat_currency") fiatCurrency: String,
                     @QueryMap body: Map<String, String>): Observable<Response<Price>>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.BUY)
    fun getBuyPrice(@Path("base_currency") baseCurrency: String,
                    @Path("fiat_currency") fiatCurrency: String,
                    @QueryMap body: Map<String, String>): Observable<Response<Price>>

    @GET(com.coinbase.ApiConstants.PRICES + "/{base_currency}-" + "{fiat_currency}/" + ApiConstants.SPOT)
    fun getSpotPrice(@Path("base_currency") baseCurrency: String,
                     @Path("fiat_currency") fiatCurrency: String,
                     @QueryMap body: Map<String, String>): Observable<Response<Price>>

    @GET(com.coinbase.ApiConstants.PRICES + "/{fiat_currency}/" + ApiConstants.SPOT)
    fun getSpotPrices(@Path("fiat_currency") fiatCurrency: String,
                      @QueryMap body: Map<String, String>): Observable<Response<Prices>>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.BUYS)
    fun buyBitcoin(@Path("id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.BUYS + "/{buy_id}/" + ApiConstants.COMMIT)
    fun commitBuyBitcoin(@Path("account_id") accountId: String, @Path("buy_id") buyId: String): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{id}/" + ApiConstants.SELLS)
    fun sellBitcoin(@Path("id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.SELLS + "/{sell_id}/" + ApiConstants.COMMIT)
    fun commitSellBitcoin(@Path("account_id") accountId: String, @Path("sell_id") sellId: String): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.ADDRESSES)
    fun generateAddress(@Path("account_id") accoundId: String): Observable<Response<Address>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.DEPOSITS)
    fun depositFunds(@Path("account_id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.DEPOSITS + "/{deposit_id}/" + ApiConstants.COMMIT)
    fun commitDeposit(@Path("account_id") accountId: String, @Path("deposit_id") depositId: String): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.WITHDRAWALS)
    fun withdrawFunds(@Path("account_id") accountId: String, @Body body: Map<String, String>): Observable<Response<Transfer>>

    @POST(ApiConstants.ACCOUNTS + "/{account_id}/" + ApiConstants.WITHDRAWALS + "/{withdrawal_id}/" + ApiConstants.COMMIT)
    fun commitWithdraw(@Path("account_id") accountId: String, @Path("withdrawal_id") depositId: String): Observable<Response<Transfer>>

    @GET(ApiConstants.PAYMENT_METHODS)
    fun getPaymentMethods(@QueryMap body: Map<String, String>): Observable<Response<PaymentMethods>>

    @GET(ApiConstants.PAYMENT_METHODS + "/{id}")
    fun getPaymentMethod(@Path("id") paymentMethodId: String): Observable<Response<PaymentMethod>>

    @GET(ApiConstants.EXCHANGE_RATES)
    fun getExchangeRates(@QueryMap body: Map<String, String>): Observable<Response<ExchangeRates>>
}
