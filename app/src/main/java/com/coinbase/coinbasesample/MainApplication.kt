package com.coinbase.coinbasesample

import android.app.Application
import com.coinbase.Coinbase
import com.coinbase.OAuth

class MainApplication : Application() {

    lateinit var client: Coinbase
        private set

    lateinit var oAuth: OAuth
        private set

    override fun onCreate() {
        super.onCreate()

        client = Coinbase()
        oAuth = OAuth(client)
    }
}
