package com.coinbase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.coinbase.auth.AccessToken
import com.coinbase.v1.entity.OAuthCodeRequest
import com.coinbase.v1.exception.CoinbaseException
import com.coinbase.v1.exception.UnauthorizedException
import retrofit2.Response
import java.io.IOException
import java.util.*

class OAuth(private val coinbase: Coinbase) {

    companion object {

        private const val KEY_COINBASE_PREFERENCES = "com.coinbase.android.sdk"
        private const val KEY_LOGIN_CSRF_TOKEN = "com.coinbase.android.sdk.login_csrf_token"
    }

    @Throws(CoinbaseException::class)
    fun beginAuthorization(context: Context,
                           clientId: String,
                           scope: String,
                           redirectUri: String,
                           meta: OAuthCodeRequest.Meta?) {

        val request = OAuthCodeRequest()
        request.clientId = clientId
        request.scope = scope
        request.redirectUri = redirectUri
        request.meta = meta

        val authorizationUri = coinbase.getAuthorizationUri(request)

        val i = Intent(Intent.ACTION_VIEW)
        var androidUri = Uri.parse(authorizationUri.toString())
        androidUri = androidUri.buildUpon().appendQueryParameter("state", getLoginCSRFToken(context)).build()
        i.data = androidUri
        context.startActivity(i)
    }

    @Throws(UnauthorizedException::class, IOException::class)
    fun completeAuthorization(context: Context,
                              clientId: String,
                              clientSecret: String,
                              redirectUri: Uri): Response<AccessToken>? {

        val csrfToken = redirectUri.getQueryParameter("state")
        val authCode = redirectUri.getQueryParameter("code")

        if (csrfToken == null || csrfToken != getLoginCSRFToken(context)) {
            throw UnauthorizedException("CSRF Detected!")
        } else if (authCode == null) {
            val errorDescription = redirectUri.getQueryParameter("error_description")
            throw UnauthorizedException(errorDescription)
        }
        try {
            val redirectUriWithoutQuery = redirectUri.buildUpon().clearQuery().build()
            return coinbase.getTokens(clientId, clientSecret, authCode, redirectUriWithoutQuery.toString())
        } catch (ex: CoinbaseException) {
            throw UnauthorizedException(ex.message)
        }
    }

    private fun getLoginCSRFToken(context: Context): String {
        val prefs = context.getSharedPreferences(KEY_COINBASE_PREFERENCES, Context.MODE_PRIVATE)

        var result = prefs.getInt(KEY_LOGIN_CSRF_TOKEN, 0)
        if (result == 0) {
            result = Random().nextInt()
            val e = prefs.edit()
            e.putInt(KEY_LOGIN_CSRF_TOKEN, result)
            e.apply()
        }

        return Integer.toString(result)
    }
}
