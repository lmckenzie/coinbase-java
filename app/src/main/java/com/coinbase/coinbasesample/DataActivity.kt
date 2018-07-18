package com.coinbase.coinbasesample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.coinbase.CallbackWithRetrofit
import com.coinbase.v2.models.price.Price
import kotlinx.android.synthetic.main.activity_data.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import java.util.*

class DataActivity : AppCompatActivity() {

    companion object {

        private const val BTC = "BTC"
        private const val USD = "USD"
        private const val ERROR_MESSAGE = "Error occurred, please try again"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        val coinbase = (applicationContext as MainApplication).client

        sellPriceBtn.setOnClickListener {
            coinbase.getSellPrice(BTC, USD, HashMap(), object : CallbackWithRetrofit<Price> {
                override fun onResponse(call: Call<Price>, response: Response<Price>?, retrofit: Retrofit) {
                    handleResponse(response)
                }

                override fun onFailure(call: Call<Price>, t: Throwable) {
                    showError()
                }
            })
        }

        buyPriceBtn.setOnClickListener {
            coinbase.getBuyPrice(BTC, USD, HashMap(), object : CallbackWithRetrofit<Price> {
                override fun onResponse(call: Call<Price>, response: Response<Price>?, retrofit: Retrofit) {
                    handleResponse(response)
                }

                override fun onFailure(call: Call<Price>, t: Throwable) {
                    showError()
                }
            })
        }

        spotPriceBtn.setOnClickListener {
            coinbase.getSpotPrice(BTC, USD, HashMap(), object : CallbackWithRetrofit<Price> {
                override fun onResponse(call: Call<Price>, response: Response<Price>?, retrofit: Retrofit) {
                    handleResponse(response)
                }

                override fun onFailure(call: Call<Price>, t: Throwable) {
                    showError()
                }
            })
        }
    }

    private fun handleResponse(response: Response<Price>?) {
        if (!response!!.isSuccessful) {
            showError()
        } else {
            priceText.text = response.body()?.data?.amount
        }
    }

    private fun showError() {
        priceText.text = ERROR_MESSAGE
    }
}
