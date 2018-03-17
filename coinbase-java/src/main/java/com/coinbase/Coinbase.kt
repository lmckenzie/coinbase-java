@file:Suppress("unused")

package com.coinbase

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.coinbase.auth.AccessToken
import com.coinbase.cache.OkHttpInMemoryLruCache
import com.coinbase.coinbase_java.BuildConfig
import com.coinbase.v1.entity.OAuthCodeRequest
import com.coinbase.v1.exception.CoinbaseException
import com.coinbase.v2.models.account.Account
import com.coinbase.v2.models.account.Accounts
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Buffer
import org.apache.commons.codec.binary.Hex
import org.joda.money.CurrencyUnit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.Observable.combineLatest
import rx.Scheduler
import rx.schedulers.Schedulers
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

class Coinbase {

    private var _baseOAuthUrl: URL? = null
    private var _baseV2ApiUrl: URL? = null
    private var _baseApiUrl: URL? = null
    private var _apiKey: String? = null
    private var _apiSecret: String? = null
    private var _accessToken: String? = null
    private var _sslContext: SSLContext? = null
    private var _socketFactory: SSLSocketFactory? = null
    private var _callbackVerifier: CallbackVerifier? = null
    private var _backgroundScheduler: Scheduler? = null
    private var _context: Context? = null
    private val _cache: OkHttpInMemoryLruCache


    private val mInitializedServices = HashMap<String, Pair<ApiInterface, Retrofit>>()
    private val mInitializedServicesRx = HashMap<String, Pair<ApiInterfaceRx, Retrofit>>()

    companion object {

        private const val DEFAULT_CACHE_SIZE = 1024 * 1024 / 2 //500kb
    }

    private val v2VersionHeaders: HashMap<String, String>
        get() {
            val headers = HashMap<String, String>()
            headers["CB-VERSION"] = com.coinbase.ApiConstants.VERSION
            headers["CB-CLIENT"] = packageVersionName
            return headers
        }

    private val packageVersionName: String
        get() {
            var packageName = ""
            var versionName = ""

            if (_context != null) {
                packageName = _context!!.packageName
            }

            try {
                versionName = "$versionName/$versionCode"
            } catch (ignored: Throwable) {

            }

            return "$packageName/$versionName"
        }

    private val versionName: String = BuildConfig.VERSION_NAME

    private val versionCode: String = BuildConfig.VERSION_CODE.toString()

    private fun getOAuthApiService(): Pair<ApiInterface, Retrofit> = getService(_baseOAuthUrl!!.toString())

    private fun getApiService(): Pair<ApiInterface, Retrofit> = getService(_baseV2ApiUrl!!.toString())

    private fun getOAuthApiServiceRx(): Pair<ApiInterfaceRx, Retrofit> = getServiceRx(_baseOAuthUrl!!.toString())

    private fun getApiServiceRx(): Pair<ApiInterfaceRx, Retrofit> = getServiceRx(_baseV2ApiUrl!!.toString())

    /**
     * Retrieve the current user and their settings.
     *
     * @return observable object that emits user/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-user)
     */
    fun getUserRx(): Observable<Pair<retrofit2.Response<User>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val userObservable = apiRetrofitPair.first.user
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(userObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }


    /**
     * Get a list of known currencies.
     *
     * @return observable object emitting supportedcurrencies/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.currencies)
     */
    fun getSupportedCurrenciesRx(): Observable<Pair<retrofit2.Response<SupportedCurrencies>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.supportedCurrencies

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    private fun getTransactionExpandOptions(): List<String> = Arrays.asList(com.coinbase.ApiConstants.FROM,
            com.coinbase.ApiConstants.TO,
            com.coinbase.ApiConstants.BUY,
            com.coinbase.ApiConstants.SELL)


    constructor() {
        try {
            _baseApiUrl = URL("https://api.coinbase.com/")
            _baseV2ApiUrl = URL(ApiConstants.BASE_URL_PRODUCTION + "/v2/")
            _baseOAuthUrl = URL("https://www.coinbase.com/oauth/")
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }

        _sslContext = CoinbaseSSL.getSSLContext()
        _socketFactory = _sslContext!!.socketFactory
        _callbackVerifier = CallbackVerifierImpl()
        _backgroundScheduler = Schedulers.io()
        _cache = OkHttpInMemoryLruCache(DEFAULT_CACHE_SIZE)
    }

    private fun generateClientBuilder(sslContext: SSLContext?): OkHttpClient.Builder {
        val clientBuilder = OkHttpClient.Builder()
        if (sslContext != null) {
            @Suppress("DEPRECATION")
            clientBuilder.sslSocketFactory(sslContext.socketFactory)
        }

        // Disable SPDY, causes issues on some Android versions
        clientBuilder.protocols(listOf(Protocol.HTTP_1_1))

        clientBuilder.readTimeout(30, TimeUnit.SECONDS)
        clientBuilder.connectTimeout(30, TimeUnit.SECONDS)

        return clientBuilder
    }

    fun setBaseUrl(url: String) {
        try {
            _baseApiUrl = URL("$url/")
            _baseV2ApiUrl = URL("$url/v2/")
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    /**
     * Set this before using any of the methods, otherwise it will have no effect.
     *
     * @param backgroundScheduler Scheduler
     */
    fun setBackgroundScheduler(backgroundScheduler: Scheduler) {
        _backgroundScheduler = backgroundScheduler
    }

    fun init(context: Context, apiKey: String, apiSecret: String) {
        _apiKey = apiKey
        _apiSecret = apiSecret
        _context = context
        _cache.evictAll()
        _cache.clearForcedCache()
    }


    fun init(context: Context, accessToken: String) {
        if (!TextUtils.equals(_accessToken, accessToken)) {
            mInitializedServices.clear()
            mInitializedServicesRx.clear()
            _cache.evictAll()
            _cache.clearForcedCache()
        }
        _accessToken = accessToken
        _context = context
    }

    internal constructor(builder: CoinbaseBuilder) {

        _baseOAuthUrl = builder.base_oauth_url
        _apiKey = builder.api_key
        _apiSecret = builder.api_secret
        _accessToken = builder.access_token
        _sslContext = builder.ssl_context
        _callbackVerifier = builder.callback_verifier
        _backgroundScheduler = builder.scheduler
        _cache = OkHttpInMemoryLruCache(if (builder.cacheSize > 0) builder.cacheSize else DEFAULT_CACHE_SIZE)

        try {
            if (_baseOAuthUrl == null) {
                _baseOAuthUrl = URL("https://www.coinbase.com/oauth/")
            }
            if (_baseV2ApiUrl == null) {
                _baseV2ApiUrl = URL("https://api.coinbase.com/v2/")
            }
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }

        // Register BTC as a currency since Android won't let joda read from classpath resources
        try {
            CurrencyUnit.registerCurrency("BTC", -1, 8, ArrayList())
        } catch (ignored: IllegalArgumentException) {
        }

        if (_sslContext != null) {
            _socketFactory = _sslContext!!.socketFactory
        } else {
            _sslContext = CoinbaseSSL.getSSLContext()
            _socketFactory = _sslContext!!.socketFactory
        }

        if (_callbackVerifier == null) {
            _callbackVerifier = CallbackVerifierImpl()
        }
    }


    private fun buildOAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val newRequest = chain.request().newBuilder().addHeader("Authorization", "Bearer " + _accessToken!!).build()
            chain.proceed(newRequest)
        }


    }

    private fun buildHmacAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()

            val timestamp = (System.currentTimeMillis() / 1000L).toString()
            val method = request.method().toUpperCase()
            val path = request.url().url().file
            var body = ""
            if (request.body() != null) {
                val requestCopy = request.newBuilder().build()
                val buffer = Buffer()
                requestCopy.body()!!.writeTo(buffer)
                body = buffer.readUtf8()
            }

            val message = timestamp + method + path + body
            val mac: Mac
            try {
                mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(_apiSecret!!.toByteArray(), "HmacSHA256"))
            } catch (t: Throwable) {
                throw IOException(t)
            }

            val signature = String(Hex.encodeHex(mac.doFinal(message.toByteArray())))

            val newRequest = request.newBuilder()
                    .addHeader("CB-ACCESS-KEY", _apiKey!!)
                    .addHeader("CB-ACCESS_SIGN", signature)
                    .addHeader("CB-ACCESS-TIMESTAMP", timestamp)
                    .build()

            chain.proceed(newRequest)
        }
    }

    private fun buildVersionInterceptor(): Interceptor {

        return Interceptor { chain ->
            val newRequest = chain
                    .request()
                    .newBuilder()
                    .addHeader("CB-VERSION", ApiConstants.VERSION)
                    .addHeader("CB-CLIENT", packageVersionName)
                    .addHeader("X-App-Version", versionName)
                    .addHeader("X-App-Build-Number", versionCode)
                    .build()
            chain.proceed(newRequest)
        }
    }

    private fun languageInterceptor(): Interceptor {
        return Interceptor { chain ->
            val newRequest = chain
                    .request()
                    .newBuilder()
                    .addHeader("Accept-Language", Locale.getDefault().language)
                    .build()
            chain.proceed(newRequest)
        }
    }

    /**
     * Interceptor for device info, override this to add device info
     *
     * @return Interceptor
     */
    private fun deviceInfoInterceptor(): Interceptor {
        return Interceptor { chain ->
            val newRequest = chain
                    .request()
                    .newBuilder()
                    .build()

            chain.proceed(newRequest)
        }
    }

    /**
     * Interceptor for network sniffing, override this to add network sniffing
     *
     * @return Interceptor
     */
    private fun networkSniffingInterceptor(): Interceptor {
        return Interceptor { chain -> chain.proceed(chain.request()) }
    }

    /**
     * Interceptor for logging, override this to add logging
     *
     * @return Interceptor
     */
    private fun loggingInterceptor(): Interceptor {
        return Interceptor { chain -> chain.proceed(chain.request()) }
    }


    @Synchronized
    private fun getService(url: String): Pair<ApiInterface, Retrofit> {
        mInitializedServices[url]?.let { return it }

        val clientBuilder = generateClientBuilder(_sslContext)

        if (_accessToken != null) {
            clientBuilder.addInterceptor(buildOAuthInterceptor())
        }

        clientBuilder.addInterceptor(buildVersionInterceptor())
        clientBuilder.addInterceptor(languageInterceptor())
        clientBuilder.addInterceptor(deviceInfoInterceptor())
        clientBuilder.addInterceptor(_cache.createInterceptor())
        clientBuilder.addInterceptor(loggingInterceptor())
        clientBuilder.addNetworkInterceptor(networkSniffingInterceptor())

        val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val service = retrofit.create(com.coinbase.ApiInterface::class.java)

        val servicePair = Pair(service, retrofit)
        mInitializedServices[url] = servicePair

        return servicePair
    }


    @Synchronized
    private fun getServiceRx(url: String): Pair<ApiInterfaceRx, Retrofit> {
        mInitializedServicesRx[url]?.let { return it }

        val clientBuilder = generateClientBuilder(_sslContext)

        if (_accessToken != null) {
            clientBuilder.addInterceptor(buildOAuthInterceptor())
        }

        clientBuilder.addInterceptor(buildVersionInterceptor())
        clientBuilder.addInterceptor(languageInterceptor())
        clientBuilder.addInterceptor(deviceInfoInterceptor())
        clientBuilder.addInterceptor(_cache.createInterceptor())
        clientBuilder.addInterceptor(loggingInterceptor())
        clientBuilder.addNetworkInterceptor(networkSniffingInterceptor())

        val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(clientBuilder.build())
                .addCallAdapterFactory(if (_backgroundScheduler == null)
                    RxJavaCallAdapterFactory.create()
                else
                    RxJavaCallAdapterFactory.createWithScheduler(_backgroundScheduler!!))
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        val service = retrofit.create(com.coinbase.ApiInterfaceRx::class.java)

        val servicePair = Pair(service, retrofit)
        mInitializedServicesRx[url] = servicePair

        return servicePair
    }

    fun getAuthorizationUri(params: OAuthCodeRequest): Uri {
        val authorizeURL: URL
        val builtUri: Uri
        val uriBuilder: Uri.Builder
        try {
            authorizeURL = URL(_baseOAuthUrl, "authorize")
            builtUri = Uri.parse(authorizeURL.toURI().toString())
            uriBuilder = builtUri.buildUpon()
        } catch (ex: URISyntaxException) {
            throw AssertionError(ex)
        } catch (ex: MalformedURLException) {
            throw AssertionError(ex)
        }
        uriBuilder.appendQueryParameter("response_type", "code")
        if (params.clientId != null) {
            uriBuilder.appendQueryParameter("client_id", params.clientId)
        } else {
            throw CoinbaseException("client_id is required")
        }
        if (params.redirectUri != null) {
            uriBuilder.appendQueryParameter("redirect_uri", params.redirectUri)
        } else {
            throw CoinbaseException("redirect_uri is required")
        }
        if (params.scope != null) {
            uriBuilder.appendQueryParameter("scope", params.scope)
        } else {
            throw CoinbaseException("scope is required")
        }
        if (params.meta != null) {
            val meta = params.meta
            if (meta.name != null) {
                uriBuilder.appendQueryParameter("meta[name]", meta.name)
            }
            if (meta.sendLimitAmount != null) {
                val sendLimit = meta.sendLimitAmount
                uriBuilder.appendQueryParameter("meta[send_limit_amount]", sendLimit.amount.toPlainString())
                uriBuilder.appendQueryParameter("meta[send_limit_currency]", sendLimit.currencyUnit.currencyCode)
                if (meta.sendLimitPeriod != null) {
                    uriBuilder.appendQueryParameter("meta[send_limit_period]", meta.sendLimitPeriod.toString())
                }
            }
        }
        return uriBuilder.build()
    }

    @Throws(CoinbaseException::class, IOException::class)
    fun getTokens(clientId: String, clientSecret: String, authCode: String, redirectUri: String?): Response<AccessToken>? {
        val params = mapOf<String, Any>(
                Pair("client_id", clientId),
                Pair("client_secret", clientSecret),
                Pair("code", authCode),
                Pair("grant_type", "authorization_code"),
                Pair("redirect_uri", redirectUri ?: "2_legged")
        )

        val apiRetrofitPair = getOAuthApiService()
        val call = apiRetrofitPair.first.getTokens(params)
        return call.execute()
    }

    /**
     * Refresh OAuth token
     *
     * @param clientId     String
     * @param clientSecret String
     * @param refreshToken String
     * @param callback     CallbackWithRetrofit<AccessToken>
     * @return call object
     * @see [](https://developers.coinbase.com/docs/wallet/coinbase-connect/access-and-refresh-tokens</a>
    )</AccessToken> */
    fun refreshTokens(clientId: String,
                      clientSecret: String,
                      refreshToken: String,
                      callback: CallbackWithRetrofit<AccessToken>?): Call<*> {
        val params = HashMap<String, Any>()
        params[ApiConstants.CLIENT_ID] = clientId
        params[ApiConstants.CLIENT_SECRET] = clientSecret
        params[ApiConstants.REFRESH_TOKEN] = refreshToken
        params[ApiConstants.GRANT_TYPE] = ApiConstants.REFRESH_TOKEN

        val apiRetrofitPair = getOAuthApiService()
        val call = apiRetrofitPair.first.refreshTokens(params)
        call.enqueue(object : Callback<AccessToken> {
            override fun onResponse(call: Call<AccessToken>, response: retrofit2.Response<AccessToken>?) {
                callback?.onResponse(call, response, apiRetrofitPair.second)

                if (response?.body() != null) {
                    _accessToken = response.body()!!.accessToken
                }
            }

            override fun onFailure(call: Call<AccessToken>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Refresh OAuth token
     *
     * @param clientId     String
     * @param clientSecret String
     * @param refreshToken String
     * @return observable object emitting acesstoken/retrofit pair
     * @see [](https://developers.coinbase.com/docs/wallet/coinbase-connect/access-and-refresh-tokens</a>
    ) */
    fun refreshTokensRx(clientId: String,
                        clientSecret: String,
                        refreshToken: String): Observable<Pair<retrofit2.Response<AccessToken>, Retrofit>> {
        val params = getRefreshTokensParams(clientId, clientSecret, refreshToken)
        val apiRetrofitPair = getOAuthApiServiceRx()
        val userObservable = apiRetrofitPair.first.refreshTokens(params)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(userObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Revoke OAuth token
     *
     * @param callback CallbackWithRetrofit<Void>
     * @return call object
     * @see [](https://developers.coinbase.com/docs/wallet/coinbase-connect/access-and-refresh-tokens</a>
    )</Void> */
    fun revokeToken(callback: CallbackWithRetrofit<Void>?): Call<*>? {
        if (_accessToken == null) {
            Log.w("Coinbase Error", "This client must have been initialized with an access token in order to call revokeToken()")
            return null
        }

        val params = mutableMapOf<String, Any?>()
        params[ApiConstants.TOKEN] = _accessToken

        val apiRetrofitPair = getOAuthApiService()
        val call = apiRetrofitPair.first.revokeToken(params)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    _accessToken = null
                }

                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Revoke OAuth token
     *
     * @return observable object emitting void/retrofit pair
     * @see [](https://developers.coinbase.com/docs/wallet/coinbase-connect/access-and-refresh-tokens</a>
    ) */
    fun revokeTokenRx(): Observable<Pair<retrofit2.Response<Void>, Retrofit>>? {
        if (_accessToken == null) {
            Log.w("Coinbase Error", "This client must have been initialized with an access token in order to call revokeToken()")
            return null
        }

        val params = mutableMapOf<String, Any?>()
        params[ApiConstants.TOKEN] = _accessToken

        val apiRetrofitPair = getOAuthApiServiceRx()
        val revokeObservable = apiRetrofitPair.first.revokeToken(params)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(revokeObservable,
                retrofitObservable
        ) { a, b ->
            _accessToken = null
            Pair(a, b)
        }
    }


    /**
     * Retrieve the current user and their settings.
     *
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-user)
     */
    fun getUser(callback: CallbackWithRetrofit<User>?): Call<*> {
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.user
        call.enqueue(object : Callback<User> {

            override fun onResponse(call: Call<User>, response: retrofit2.Response<User>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<User>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Modify current user and their preferences
     *
     * @param name           User's public name
     * @param timeZone       Time zone
     * @param nativeCurrency Local currency used to display amounts converted from BTC
     * @param callback       callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.update-current-user)
     */
    fun updateUser(name: String, timeZone: String, nativeCurrency: String, callback: CallbackWithRetrofit<User>?): Call<*> {
        val params = getUpdateUserParams(name, timeZone, nativeCurrency)

        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.updateUser(params)
        call.enqueue(object : Callback<User> {

            override fun onResponse(call: Call<User>, response: retrofit2.Response<User>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<User>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Modify current user and their preferences
     *
     * @param name           User's public name
     * @param timeZone       Time zone
     * @param nativeCurrency Local currency used to display amounts converted from BTC
     * @return observable object with user/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.update-current-user)
     */
    fun updateUserRx(name: String, timeZone: String, nativeCurrency: String): Observable<Pair<retrofit2.Response<User>, Retrofit>> {
        val params = getUpdateUserParams(name, timeZone, nativeCurrency)

        val apiRetrofitPair = getApiServiceRx()
        val userObservable = apiRetrofitPair.first.updateUser(params)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(userObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Retrieve an account belonging to this user
     *
     * @param accountId account ID for the account to retrieve
     * @param callback  callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-an-account)
     */
    fun getAccount(accountId: String, callback: CallbackWithRetrofit<Account>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.getAccount(accountId)
        call.enqueue(object : Callback<Account> {

            override fun onResponse(call: Call<Account>, response: retrofit2.Response<Account>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Account>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Retrieve an account belonging to this user
     *
     * @param accountId account ID for the account to retrieve
     * @return observable object emitting account/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-an-account)
     */
    fun getAccountRx(accountId: String): Observable<Pair<retrofit2.Response<Account>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val accountObservable = apiRetrofitPair.first.getAccount(accountId)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(accountObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Retrieve a list of accounts belonging to this user
     *
     * @param inOptions  endpoint options
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-accounts)
     */
    fun getAccounts(inOptions: HashMap<String, Any>, callback: CallbackWithRetrofit<Accounts>?): Call<*> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.getAccounts(options)
        call.enqueue(object : Callback<Accounts> {

            override fun onResponse(call: Call<Accounts>, response: Response<Accounts>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Accounts>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Retrieve a list of accounts belonging to this user
     *
     * @param inOptions endpoint options
     * @return observable object emitting accounts/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-accounts)
     */
    fun getAccountsRx(inOptions: HashMap<String, Any>): Observable<Pair<Response<Accounts>, Retrofit>> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiServiceRx()

        val accountsObservable = apiRetrofitPair.first.getAccounts(options)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(accountsObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }


    /**
     * Create a new account for user
     *
     * @param options  endpoint options
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.create-account)
     */
    fun createAccount(options: HashMap<String, Any>, callback: CallbackWithRetrofit<Account>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.createAccount(options)
        call.enqueue(object : Callback<Account> {

            override fun onResponse(call: Call<Account>, response: retrofit2.Response<Account>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Account>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Create a new account for user
     *
     * @param options endpoint options
     * @return observable object emitting account/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.create-account)
     */
    fun createAccountRx(options: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Account>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val accountObservable = apiRetrofitPair.first.createAccount(options)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(accountObservable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Promote an account as primary account
     *
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.set-account-as-primary)
     */
    fun setAccountPrimary(accountId: String, callback: CallbackWithRetrofit<Void>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.setAccountPrimary(accountId)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Promote an account as primary account
     *
     * @return observable object emitting void/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.set-account-as-primary)
     */
    fun setAccountPrimaryRx(accountId: String): Observable<Pair<retrofit2.Response<Void>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.setAccountPrimary(accountId)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Modifies user's account
     *
     * @param options  endpoint options
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.update-account)
     */
    fun updateAccount(accountId: String, options: HashMap<String, Any>, callback: CallbackWithRetrofit<Account>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.updateAccount(accountId, options)
        call.enqueue(object : Callback<Account> {

            override fun onResponse(call: Call<Account>, response: retrofit2.Response<Account>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Account>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Modifies user's account
     *
     * @param options endpoint options
     * @return observable object account/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.update-account)
     */
    fun updateAccountRx(accountId: String,
                        options: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Account>, Retrofit>> {

        val apiRetrofitPair = getApiServiceRx()

        val accountObservable = apiRetrofitPair.first.updateAccount(accountId,
                options)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(accountObservable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Removes user's account. See documentation for restrictions
     *
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.delete-account)
     */
    fun deleteAccount(accountId: String, callback: CallbackWithRetrofit<Void>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.deleteAccount(accountId)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Removes user's account. See documentation for restrictions
     *
     * @return observable object void/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.delete-account)
     */
    fun deleteAccountRx(accountId: String): Observable<Pair<retrofit2.Response<Void>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.deleteAccount(accountId)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }


    /**
     * Retrieve a list of the user's recent transactions.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param inOptions       endpoint options
     * @param expandOptions expand options
     * @param callback      callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-transactions)
     */
    fun getTransactions(accountId: String,
                        inOptions: HashMap<String, Any>,
                        expandOptions: List<String>,
                        callback: CallbackWithRetrofit<Transactions>?): Call<*> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.getTransactions(accountId, expandOptions, options)
        call.enqueue(object : Callback<Transactions> {

            override fun onResponse(call: Call<Transactions>, response: retrofit2.Response<Transactions>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transactions>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Retrieve a list of the user's recent transactions.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param inOptions       endpoint options
     * @param expandOptions expand options
     * @return observable object emitting transactions/retrofit object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-transactions)
     */
    fun getTransactionsRx(accountId: String,
                          inOptions: HashMap<String, Any>,
                          expandOptions: List<String>): Observable<Pair<retrofit2.Response<Transactions>, Retrofit>> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiServiceRx()

        val transactionObservable = apiRetrofitPair.first.getTransactions(accountId, expandOptions, options)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(transactionObservable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Retrieve details of an individual transaction.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the transaction id or idem field value
     * @param callback      callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-transaction)
     */
    fun getTransaction(accountId: String, transactionId: String, callback: CallbackWithRetrofit<Transaction>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val expandOptions = getTransactionExpandOptions()
        val call = apiRetrofitPair.first.getTransaction(accountId, transactionId, expandOptions)
        call.enqueue(object : Callback<Transaction> {

            override fun onResponse(call: Call<Transaction>, response: retrofit2.Response<Transaction>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transaction>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Retrieve details of an individual transaction.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the transaction id or idem field value
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-transaction)
     */
    fun getTransactionRx(accountId: String, transactionId: String): Observable<Pair<retrofit2.Response<Transaction>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val expandOptions = getTransactionExpandOptions()

        val transactionObservable = apiRetrofitPair.first.getTransaction(accountId, transactionId, expandOptions)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(transactionObservable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Complete a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be completed
     * @param callback      callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.complete-request-money)
     */
    fun completeRequest(accountId: String, transactionId: String, callback: CallbackWithRetrofit<Void>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.completeRequest(accountId, transactionId)
        call.enqueue(object : Callback<Void> {

            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Complete a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be completed
     * @return observable object emitting void/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.complete-request-money)
     */
    fun completeRequestRx(accountId: String, transactionId: String): Observable<Pair<retrofit2.Response<Void>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.completeRequest(accountId, transactionId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }


    /**
     * Resend emails for a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be resent
     * @param callback      callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.re-send-request-money)
     */
    fun resendRequest(accountId: String, transactionId: String, callback: CallbackWithRetrofit<Void>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.resendRequest(accountId, transactionId)
        call.enqueue(object : Callback<Void> {

            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Resend emails for a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be resent
     * @return observable object emitting void/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.re-send-request-money)
     */
    fun resendRequestRx(accountId: String, transactionId: String): Observable<Pair<retrofit2.Response<Void>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.resendRequest(accountId, transactionId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Cancel a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be cancelled
     * @param callback      callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.cancel-request-money)
     */
    fun cancelRequest(accountId: String, transactionId: String, callback: CallbackWithRetrofit<Void>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.cancelTransaction(accountId, transactionId)
        call.enqueue(object : Callback<Void> {

            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Cancel a money request.
     *
     * @param accountId     account ID that the transaction belongs to
     * @param transactionId the id of the request money transaction to be cancelled
     * @return observable object emitting void/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.cancel-request-money)
     */
    fun cancelRequestRx(accountId: String, transactionId: String): Observable<Pair<retrofit2.Response<Void>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.cancelTransaction(accountId, transactionId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Send money to an email address or bitcoin address
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.send-money)
     */
    fun sendMoney(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transaction>?): Call<*> {
        params[com.coinbase.ApiConstants.TYPE] = com.coinbase.ApiConstants.SEND
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.sendMoney(accountId, params)
        call.enqueue(object : Callback<Transaction> {

            override fun onResponse(call: Call<Transaction>, response: retrofit2.Response<Transaction>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transaction>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Send money to an email address or bitcoin address
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return observable object emitting transaction/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.send-money)
     */
    fun sendMoneyRx(accountId: String,
                    params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transaction>, Retrofit>> {
        params[com.coinbase.ApiConstants.TYPE] = com.coinbase.ApiConstants.SEND

        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.sendMoney(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Request money from an email address or bitcoin address
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.request-money)
     */
    fun requestMoney(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transaction>?): Call<*> {
        params[com.coinbase.ApiConstants.TYPE] = com.coinbase.ApiConstants.REQUEST
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.requestMoney(accountId, params)
        call.enqueue(object : Callback<Transaction> {

            override fun onResponse(call: Call<Transaction>, response: retrofit2.Response<Transaction>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transaction>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Request money from an email address or bitcoin address
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return observable object emitting transaction/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.request-money)
     */
    fun requestMoneyRx(accountId: String,
                       params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transaction>, Retrofit>> {
        params[com.coinbase.ApiConstants.TYPE] = com.coinbase.ApiConstants.REQUEST

        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.requestMoney(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Transfer bitcoin between two of a user’s accounts
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.transfer-money-between-accounts)
     */
    fun transferMoney(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transaction>?): Call<*> {
        params[com.coinbase.ApiConstants.TYPE] = ApiConstants.TRANSFER
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.transferMoney(accountId, params)
        call.enqueue(object : Callback<Transaction> {

            override fun onResponse(call: Call<Transaction>, response: retrofit2.Response<Transaction>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transaction>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Transfer bitcoin between two of a user’s accounts
     *
     * @param accountId account ID that the transaction belongs to
     * @param params    endpoint parameters
     * @return observable object emitting transaction/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.transfer-money-between-accounts)
     */
    fun transferMoneyRx(accountId: String, params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transaction>, Retrofit>> {
        params[com.coinbase.ApiConstants.TYPE] = ApiConstants.TRANSFER

        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.transferMoney(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Buys user-defined amount of bitcoin.
     *
     * @param accountId account ID that the buy belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.buy-bitcoin)
     */
    fun buyBitcoin(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.buyBitcoin(accountId, params)
        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Buys user-defined amount of bitcoin.
     *
     * @param accountId account ID that the buy belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.buy-bitcoin)
     */
    fun buyBitcoinRx(accountId: String, params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.buyBitcoin(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest<Response<Transfer>, Retrofit, Pair<Response<Transfer>, Retrofit>>(observable, retrofitObservable) { a, b -> Pair(a, b) }
    }

    /**
     * Commits a buy that is created in commit: false state.
     *
     * @param accountId account ID that the buy belongs to
     * @param buyId     buy ID that the buy belongs to
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-buy)
     */

    fun commitBuyBitcoin(accountId: String, buyId: String, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.commitBuyBitcoin(accountId, buyId)

        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Commits a buy that is created in commit: false state.
     *
     * @param accountId account ID that the buy belongs to
     * @param buyId     buy ID that the buy belongs to
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-buy)
     */

    fun commitBuyBitcoinRx(accountId: String, buyId: String): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.commitBuyBitcoin(accountId,
                buyId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest<Response<Transfer>, Retrofit, Pair<Response<Transfer>, Retrofit>>(observable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Sells user-defined amount of bitcoin.
     *
     * @param accountId account ID that the sell belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.sell-bitcoin)
     */
    fun sellBitcoin(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.sellBitcoin(accountId, params)
        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Sells user-defined amount of bitcoin.
     *
     * @param accountId account ID that the sell belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.sell-bitcoin)
     */
    fun sellBitcoinRx(accountId: String,
                      params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.sellBitcoin(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest<Response<Transfer>, Retrofit, Pair<Response<Transfer>, Retrofit>>(observable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Commits a sell that is created in commit: false state.
     *
     * @param accountId account ID that the sell belongs to
     * @param sellId    sell ID that the sell belongs to
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-sell)
     */

    fun commitSellBitcoin(accountId: String, sellId: String, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.commitSellBitcoin(accountId, sellId)

        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Commits a sell that is created in commit: false state.
     *
     * @param accountId account ID that the sell belongs to
     * @param sellId    sell ID that the sell belongs to
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-sell)
     */

    fun commitSellBitcoinRx(accountId: String, sellId: String): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.commitSellBitcoin(accountId,
                sellId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest<Response<Transfer>, Retrofit, Pair<Response<Transfer>, Retrofit>>(observable,
                retrofitObservable
        ) { a, b -> Pair(a, b) }
    }

    /**
     * Retrieve the current sell price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSellPrice(baseCurrency: String, fiatCurrency: String,
                     inParams: HashMap<String, Any>, callback: CallbackWithRetrofit<Price>?): Call<*> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.getSellPrice(baseCurrency, fiatCurrency, params)
        call.enqueue(object : Callback<Price> {

            override fun onResponse(call: Call<Price>, response: retrofit2.Response<Price>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Price>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Retrieve the current sell price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return observable object emitting price/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSellPriceRx(baseCurrency: String, fiatCurrency: String, inParams: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Price>, Retrofit>> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getSellPrice(baseCurrency, fiatCurrency, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Retrieve the current buy price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       optional HashMap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getBuyPrice(baseCurrency: String, fiatCurrency: String,
                    inParams: HashMap<String, Any>, callback: CallbackWithRetrofit<Price>?): Call<*> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiService()


        val call = apiRetrofitPair.first.getBuyPrice(baseCurrency, fiatCurrency, params)
        call.enqueue(object : Callback<Price> {

            override fun onResponse(call: Call<Price>, response: retrofit2.Response<Price>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Price>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Retrieve the current buy price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       optional HashMap of params as indicated in api docs
     * @return observable object emitting price/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getBuyPriceRx(baseCurrency: String, fiatCurrency: String, inParams: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Price>, Retrofit>> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getBuyPrice(baseCurrency, fiatCurrency, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Retrieve the current spot price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSpotPrice(baseCurrency: String, fiatCurrency: String,
                     inParams: HashMap<String, Any>, callback: CallbackWithRetrofit<Price>?): Call<*> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.getSpotPrice(baseCurrency, fiatCurrency, params)
        call.enqueue(object : Callback<Price> {

            override fun onResponse(call: Call<Price>, response: retrofit2.Response<Price>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Price>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Retrieve the current spot price of 1 BTC
     *
     * @param baseCurrency the digital currency in which to retrieve the price against
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return observable object emitting price/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSpotPriceRx(baseCurrency: String, fiatCurrency: String, inParams: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Price>, Retrofit>> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getSpotPrice(baseCurrency, fiatCurrency, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Retrieve the spot prices for all supported currencies for the given fiatCurrency
     *
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return observable object emitting price/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSpotPrices(fiatCurrency: String,
                      inParams: HashMap<String, Any>, callback: CallbackWithRetrofit<Prices>?): Call<*> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiService()


        val call = apiRetrofitPair.first.getSpotPrices(fiatCurrency, params)
        call.enqueue(object : Callback<Prices> {

            override fun onResponse(call: Call<Prices>, response: retrofit2.Response<Prices>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Prices>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Retrieve the spot prices for all supported currencies for the given fiatCurrency
     *
     * @param fiatCurrency the currency in which to retrieve the price
     * @param inParams       HashMap of params as indicated in api docs
     * @return observable object emitting price/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-spot-price)
     */
    fun getSpotPricesRx(fiatCurrency: String, inParams: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Prices>, Retrofit>> {
        val params = cleanQueryMap(inParams)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getSpotPrices(fiatCurrency, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Generate new address for an account
     *
     * @param accountId the accountId of the account
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.create-address)
     */

    fun generateAddress(accountId: String, callback: CallbackWithRetrofit<com.coinbase.v2.models.address.Address>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.generateAddress(accountId)
        call.enqueue(object : Callback<com.coinbase.v2.models.address.Address> {

            override fun onResponse(call: Call<com.coinbase.v2.models.address.Address>, response: retrofit2.Response<com.coinbase.v2.models.address.Address>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<com.coinbase.v2.models.address.Address>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Generate new address for an account
     *
     * @param accountId the accountId of the account
     * @return observable object emitting address/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.create-address)
     */

    fun generateAddressRx(accountId: String): Observable<Pair<retrofit2.Response<com.coinbase.v2.models.address.Address>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.generateAddress(accountId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Deposits user-defined amount of funds to a fiat account.
     *
     * @param accountId account ID that the deposit belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.deposit-funds)
     */
    fun depositFunds(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.depositFunds(accountId, params)
        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Deposits user-defined amount of funds to a fiat account.
     *
     * @param accountId account ID that the deposit belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.deposit-funds)
     */
    fun depositFundsRx(accountId: String,
                       params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.depositFunds(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Commits a deposit that is created in commit: false state.
     *
     * @param accountId account ID that the deposit belongs to
     * @param depositId deposit ID that the deposit belongs to
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-deposit)
     */

    fun commitDeposit(accountId: String, depositId: String, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.commitDeposit(accountId, depositId)

        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Commits a deposit that is created in commit: false state.
     *
     * @param accountId account ID that the deposit belongs to
     * @param depositId deposit ID that the deposit belongs to
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-deposit)
     */

    fun commitDepositRx(accountId: String, depositId: String): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()
        val observable = apiRetrofitPair.first.commitDeposit(accountId, depositId)
        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Withdraws user-defined amount of funds from a fiat account.
     *
     * @param accountId account ID that the withdrawal belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.withdraw-funds)
     */
    fun withdrawFunds(accountId: String, params: HashMap<String, Any>, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.withdrawFunds(accountId, params)
        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }


    /**
     * Withdraws user-defined amount of funds from a fiat account.
     *
     * @param accountId account ID that the withdrawal belongs to
     * @param params    hashmap of params as indicated in api docs
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.withdraw-funds)
     */
    fun withdrawFundsRx(accountId: String,
                        params: HashMap<String, Any>): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.withdrawFunds(accountId, params)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Commits a withdrawal that is created in commit: false state.
     *
     * @param accountId  account ID that the withdrawal belongs to
     * @param withdrawId deposit ID that the withdrawal belongs to
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-deposit)
     */

    fun commitWithdraw(accountId: String, withdrawId: String, callback: CallbackWithRetrofit<Transfer>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.commitWithdraw(accountId, withdrawId)

        call.enqueue(object : Callback<Transfer> {

            override fun onResponse(call: Call<Transfer>, response: retrofit2.Response<Transfer>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<Transfer>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Commits a withdrawal that is created in commit: false state.
     *
     * @param accountId  account ID that the withdrawal belongs to
     * @param withdrawId deposit ID that the withdrawal belongs to
     * @return observable object emitting transfer/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.commit-a-deposit)
     */

    fun commitWithdrawRx(accountId: String, withdrawId: String): Observable<Pair<retrofit2.Response<Transfer>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.commitWithdraw(accountId,
                withdrawId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Show current user’s payment method.
     *
     * @param paymentMethodId paymentMethod ID for the account to retrieve
     * @param callback        callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-payment-method)
     */
    fun getPaymentMethod(paymentMethodId: String, callback: CallbackWithRetrofit<PaymentMethod>?): Call<*> {
        val apiRetrofitPair = getApiService()
        val call = apiRetrofitPair.first.getPaymentMethod(paymentMethodId)
        call.enqueue(object : Callback<PaymentMethod> {

            override fun onResponse(call: Call<PaymentMethod>, response: retrofit2.Response<PaymentMethod>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<PaymentMethod>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Show current user’s payment method.
     *
     * @param paymentMethodId paymentMethod ID for the account to retrieve
     * @return observable object paymentmethod/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.show-a-payment-method)
     */
    fun getPaymentMethodRx(paymentMethodId: String): Observable<Pair<retrofit2.Response<PaymentMethod>, Retrofit>> {
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getPaymentMethod(paymentMethodId)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Lists current user’s payment methods.
     *
     * @param inOptions  endpoint options
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-payment-methods)
     */
    fun getPaymentMethods(inOptions: HashMap<String, Any>, callback: CallbackWithRetrofit<PaymentMethods>?): Call<*> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.getPaymentMethods(options)
        call.enqueue(object : Callback<PaymentMethods> {

            override fun onResponse(call: Call<PaymentMethods>, response: retrofit2.Response<PaymentMethods>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }


            override fun onFailure(call: Call<PaymentMethods>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Lists current user’s payment methods.
     *
     * @param inOptions endpoint options
     * @return observable object emitting paymentmethods/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.list-payment-methods)
     */
    fun getPaymentMethodsRx(inOptions: HashMap<String, Any>): Observable<Pair<retrofit2.Response<PaymentMethods>, Retrofit>> {
        val options = cleanQueryMap(inOptions)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getPaymentMethods(options)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Get current exchange rates.
     *
     * @param inCurrency base currency (Default: USD)
     * @param callback callback interface
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-exchange-rates)
     */
    fun getExchangeRates(inCurrency: HashMap<String, Any>, callback: CallbackWithRetrofit<ExchangeRates>?): Call<*> {
        val currency = cleanQueryMap(inCurrency)
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.getExchangeRates(currency)
        call.enqueue(object : Callback<ExchangeRates> {

            override fun onResponse(call: Call<ExchangeRates>, response: retrofit2.Response<ExchangeRates>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<ExchangeRates>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Get current exchange rates.
     *
     * @param inCurrency base currency (Default: USD)
     * @return observable object emitting exchangerates/retrofit pair
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.get-exchange-rates)
     */
    fun getExchangeRatesRx(inCurrency: HashMap<String, Any>): Observable<Pair<retrofit2.Response<ExchangeRates>, Retrofit>> {
        val currency = cleanQueryMap(inCurrency)
        val apiRetrofitPair = getApiServiceRx()

        val observable = apiRetrofitPair.first.getExchangeRates(currency)

        val retrofitObservable = Observable.just(apiRetrofitPair.second)
        return combineLatest(observable, retrofitObservable, { first, second -> Pair(first, second) })
    }

    /**
     * Get a list of known currencies.
     *
     * @return call object
     * @see [Online Documentation](https://developers.coinbase.com/api/v2.currencies)
     */
    fun getSupportedCurrencies(callback: CallbackWithRetrofit<SupportedCurrencies>?): Call<*> {
        val apiRetrofitPair = getApiService()

        val call = apiRetrofitPair.first.supportedCurrencies
        call.enqueue(object : Callback<SupportedCurrencies> {

            override fun onResponse(call: Call<SupportedCurrencies>, response: retrofit2.Response<SupportedCurrencies>) {
                callback?.onResponse(call, response, apiRetrofitPair.second)
            }

            override fun onFailure(call: Call<SupportedCurrencies>, t: Throwable) {
                callback?.onFailure(call, t)
            }
        })

        return call
    }

    /**
     * Remove any null values from the HashMap. If the HashMap is itself null, return an empty HashMap.
     * This is due to a difference between retrofit 1 and retrofit 2.  Retrofit 1 would quietly remove any
     * null query params and handle the query map itself being null. Retrofit2 throws an exception and fails
     * the request.
     *
     * @param options HashMap
     */
    private fun cleanQueryMap(options: HashMap<String, Any>?): HashMap<String, Any> {
        return if (options == null) {
            HashMap()
        } else {
            val optionsCopy = HashMap(options)
            for (key in options.keys) {
                if (optionsCopy[key] == null) {
                    optionsCopy.remove(key)
                }
            }
            optionsCopy
        }
    }

    private fun getRefreshTokensParams(clientId: String, clientSecret: String, refreshToken: String): HashMap<String, Any> {
        val params = HashMap<String, Any>()
        params[ApiConstants.CLIENT_ID] = clientId
        params[ApiConstants.CLIENT_SECRET] = clientSecret
        params[ApiConstants.REFRESH_TOKEN] = refreshToken
        params[ApiConstants.GRANT_TYPE] = ApiConstants.REFRESH_TOKEN
        return params
    }

    private fun getUpdateUserParams(name: String?, timeZone: String?, nativeCurrency: String?): HashMap<String, Any> {
        val params = HashMap<String, Any>()

        if (name != null) {
            params[ApiConstants.NAME] = name
        }

        if (timeZone != null) {
            params[ApiConstants.TIME_ZONE] = timeZone
        }

        if (nativeCurrency != null) {
            params[ApiConstants.NATIVE_CURRENCY] = nativeCurrency
        }

        return params
    }
}
