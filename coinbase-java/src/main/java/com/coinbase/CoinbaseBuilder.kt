package com.coinbase

import rx.Scheduler
import java.net.URL
import javax.net.ssl.SSLContext

@Suppress("unused")
class CoinbaseBuilder {

    internal var access_token: String? = null
    internal var api_key: String? = null
    internal var api_secret: String? = null
    internal var ssl_context: SSLContext? = null
    internal var base_oauth_url: URL? = null
    internal var callback_verifier: CallbackVerifier? = null
    internal var scheduler: Scheduler? = null
    internal var cacheSize: Int = 0

    /**
     * Build a new Coinbase client object with the specified options
     *
     * @return a new Coinbase client object
     */
    fun build(): Coinbase {
        return Coinbase(this)
    }

    /**
     * Specify an access token to be used for authenticated requests
     *
     *
     * Coinbase client objects built using an access token are thread-safe
     *
     * @param access_token the OAuth access token
     * @return this CoinbaseBuilder object
     */
    fun withAccessToken(access_token: String): CoinbaseBuilder {
        this.access_token = access_token
        return this
    }

    /**
     * Specify the HMAC api key and secret to be used for authenticated requests
     *
     *
     * Having more than one client with the same api/secret globally is unsupported
     * and will result in sporadic auth errors as the nonce is calculated from the system time.
     *
     * @param api_key    the HMAC API Key
     * @param api_secret the HMAC API Secret
     * @return this CoinbaseBuilder object
     */
    fun withApiKey(api_key: String, api_secret: String): CoinbaseBuilder {
        this.api_key = api_key
        this.api_secret = api_secret
        return this
    }

    /**
     * Specify the verifier used to verify merchant callbacks
     *
     * @param callback_verifier CallbackVerifier
     * @return this CoinbaseBuilder object
     */
    fun withCallbackVerifier(callback_verifier: CallbackVerifier): CoinbaseBuilder {
        this.callback_verifier = callback_verifier
        return this
    }

    /**
     * Specify the ssl context to be used when creating SSL sockets
     *
     * @param ssl_context the SSLContext to be used
     * @return this CoinbaseBuilder object
     */
    fun withSSLContext(ssl_context: SSLContext): CoinbaseBuilder {
        this.ssl_context = ssl_context
        return this
    }

    /**
     * Specify the base URL to be used for API requests
     *
     *
     * By default, this is 'https://coinbase.com/api/v1/'
     *
     * @param base_api_url the base URL to use for API requests. Must return an instance of javax.net.ssl.HttpsURLConnection on openConnection.
     * @return this CoinbaseBuilder object
     */
    fun withBaseApiURL(base_api_url: URL): CoinbaseBuilder {
        return this
    }

    /**
     * Specify the base URL to be used for OAuth requests
     *
     *
     * By default, this is 'https://coinbase.com/oauth/'
     *
     * @param base_oauth_url the base URL to use for OAuth requests. Must return an instance of javax.net.ssl.HttpsURLConnection on openConnection.
     * @return this CoinbaseBuilder object
     */
    fun withBaseOAuthURL(base_oauth_url: URL): CoinbaseBuilder {
        this.base_oauth_url = base_oauth_url
        return this
    }

    /**
     * Optional - specify the rx scheduler to run the subscriber (i.e. network io) requests on.
     * If you don't specify one, it's up to you to call subscribeOn with the scheduler you'd like to use.
     *
     *
     * By default, this is 'https://coinbase.com/oauth/'
     *
     * @param scheduler the rx.Scheduler to run the background requests on.
     * @return this CoinbaseBuilder object
     */
    fun withScheduler(scheduler: Scheduler): CoinbaseBuilder {
        this.scheduler = scheduler
        return this
    }

    /**
     * Optional - specify the cache size to use for the lru in-memory cache
     * If you don't specify one, the default cache size of 500kb will be used. In practice you shouldn't need more than 250kb.
     * @param cacheSize an int representing the size in BYTES
     *
     * @return this CoinbaseBuilder object
     */
    fun cacheSize(cacheSize: Int): CoinbaseBuilder {
        this.cacheSize = cacheSize
        return this
    }
}
