package com.coinbase.cache

import android.util.LruCache
import android.util.Pair
import okhttp3.*
import org.apache.commons.lang3.StringUtils
import java.util.*

@Suppress("unused")
/**
 * An in-memory cache of etag/body pairs.  In practice, this cache doesn't need to be larger than 250kb in size
 */
class OkHttpInMemoryLruCache(maxSize: Int) : LruCache<String, Pair<String, OkHttpInMemoryLruCache.CachedResponseBody>>(maxSize) {

    private var mEnabledForcedCachePathPrefixes: Array<String> = arrayOf()

    private val mForcedCacheUrls = HashMap<String, Long>()

    @Volatile
    private var mTimeoutInMillis: Long = 0

    @Volatile
    private var mForcedCacheEnabled = false

    companion object {

        private const val NOT_MODIFIED = 304
    }

    @Synchronized
    override fun sizeOf(key: String, value: Pair<String, CachedResponseBody>): Int {
        //Url length + etag length + body length
        return key.length + value.first.length + value.second.body().size
    }

    fun createInterceptor(): Interceptor {
        return Interceptor { chain ->
            var request = chain.request()

            //Only send ETag for get requests
            if (!request.method().equals("GET", ignoreCase = true)) {
                /**
                 * PUT/POST/DELETE can cause state update on the server
                 */
                synchronized(this@OkHttpInMemoryLruCache) {
                    mForcedCacheUrls.clear()
                }
                return@Interceptor chain.proceed(request)
            }


            val url = request.url().toString()
            var responseBodyPair: Pair<String, CachedResponseBody>? = null
            synchronized(this@OkHttpInMemoryLruCache) {
                responseBodyPair = get(url)
            }

            if (responseBodyPair != null) {
                synchronized(this@OkHttpInMemoryLruCache) {
                    request = request.newBuilder().header("If-None-Match", get(url).first).build()
                }
            }

            synchronized(this@OkHttpInMemoryLruCache) {
                val cachedResponse = handleForcedCacheResponseIfEnabled(request, responseBodyPair, chain)
                if (cachedResponse != null) {
                    return@Interceptor cachedResponse
                }
            }

            val response = chain.proceed(request)

            if (response.isSuccessful) {
                //Response is between a 200 and a 300, cache the response if it has an ETag header
                val etag = response.header("ETag")
                val contentType = response?.body()?.contentType()
                if (!etag.isNullOrEmpty() && contentType != null) {
                    responseBodyPair = Pair(etag!!, CachedResponseBody(contentType, response.body()!!.bytes(), response.code()))
                    synchronized(this@OkHttpInMemoryLruCache) {
                        put(url, responseBodyPair)
                    }
                    return@Interceptor response.newBuilder()
                            .body(ResponseBody.create(responseBodyPair!!.second.contentType(), responseBodyPair!!.second.body()))
                            .code(responseBodyPair!!.second.successCode())
                            .build()
                }
            } else if (response.code() == NOT_MODIFIED) {
                //Server sent us a 304, return the cached body if we have it (we should)
                if (response.body()!!.contentLength() > 0) {
                    android.util.Log.e("Coinbase", "Unexpected 304 with content length!")
                    return@Interceptor response
                }
                if (responseBodyPair == null) {
                    android.util.Log.e("Coinbase", "304 but no cached response")
                    return@Interceptor response
                }

                return@Interceptor response.newBuilder()
                        .body(ResponseBody.create(responseBodyPair!!.second.contentType(), responseBodyPair!!.second.body()))
                        .code(responseBodyPair!!.second.successCode())
                        .build()
            }

            response
        }
    }

    /**
     * Force cache response if timeout hasn't been hit and the url is one of the urls in the set.
     * @param paths the set of paths to force cache for
     * @param timeoutInMillis timeout in milliseconds, after the timeout is hit the response will not be from the local cache.
     */
    fun setForcedCache(paths: Set<String>, timeoutInMillis: Long) {
        synchronized(this@OkHttpInMemoryLruCache) {
            mForcedCacheUrls.clear()
            mEnabledForcedCachePathPrefixes = paths.toTypedArray()
            mTimeoutInMillis = timeoutInMillis
        }
    }

    /**
     * Disable forced cache response
     */
    fun clearForcedCache() {
        synchronized(this@OkHttpInMemoryLruCache) {
            mForcedCacheUrls.clear()
            mEnabledForcedCachePathPrefixes = arrayOf()
        }
    }

    /**
     * Enable/disable forced cache.
     */
    fun setForcedCacheEnabled(enabled: Boolean) {
        synchronized(this@OkHttpInMemoryLruCache) {
            mForcedCacheEnabled = enabled
        }
    }

    private fun handleForcedCacheResponseIfEnabled(request: Request,
                                                   responseBodyPair: Pair<String, CachedResponseBody>?,
                                                   chain: Interceptor.Chain): Response? {
        if (!mForcedCacheEnabled) {
            return null
        }
        /**
         * If we're forcing caching of this url path
         */
        val url = request.url().toString()
        val path = request.url().encodedPath()
        if (StringUtils.startsWithAny(path, *mEnabledForcedCachePathPrefixes)) {

            val currentTimeMillis = System.currentTimeMillis()
            /**
             * We have a response body, we've cached this path/query before and the response hasn't timed out,
             * then terminate the chain and return the cached response.
             */
            val cachedTimeZone = mForcedCacheUrls[url]
            if (responseBodyPair != null &&
                    cachedTimeZone != null &&
                    currentTimeMillis - cachedTimeZone < mTimeoutInMillis) {
                return Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .body(ResponseBody.create(responseBodyPair.second.contentType(), responseBodyPair.second.body()))
                        .code(responseBodyPair.second.successCode())
                        .build()
            } else {
                /**
                 * Skip force cache and remember the last time we served a real server response.
                 */
                mForcedCacheUrls[url] = currentTimeMillis
            }
        }
        return null
    }

    class CachedResponseBody(private val mContentType: MediaType, private val mBody: ByteArray, private val mSuccessCode: Int) {

        fun body(): ByteArray {
            return mBody
        }

        fun contentType(): MediaType {
            return mContentType
        }

        fun successCode(): Int {
            return mSuccessCode
        }
    }
}
