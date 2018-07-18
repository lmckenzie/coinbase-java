package com.coinbase.coinbasesample

import android.app.Activity
import com.coinbase.Coinbase
import com.coinbase.OAuth
import com.coinbase.v2.models.errors.Errors
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

fun Activity.oAuth(): OAuth {
    return (this.applicationContext as MainApplication).oAuth
}

fun Activity.coinbase(): Coinbase {
    return (this.applicationContext as MainApplication).client
}

fun Retrofit.errorMessage(response: retrofit2.Response<*>?): String {
    val converter: Converter<ResponseBody, Errors> = this.responseBodyConverter(Errors::class.java, arrayOf<Annotation>())
    return response?.errorBody()?.let { errorBody ->
        converter.convert(errorBody).errors?.firstOrNull()?.message
    } ?: ""
}
