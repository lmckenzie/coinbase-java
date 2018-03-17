package com.coinbase

import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit

interface CallbackWithRetrofit<T> {
    fun onResponse(call: Call<T>, response: Response<T>?, retrofit: Retrofit)

    fun onFailure(call: Call<T>, t: Throwable)
}
