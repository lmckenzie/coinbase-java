package com.coinbase

import java.io.IOException
import java.io.InputStream
import java.security.KeyStore

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object CoinbaseSSL {

    private var sslContext: SSLContext? = null

    @Synchronized
    fun getSSLContext(): SSLContext {

        if (sslContext != null) {
            return sslContext as SSLContext
        }

        val trustStore: KeyStore?
        var trustStoreInputStream: InputStream? = null

        try {
            if (System.getProperty("java.vm.name").equals("Dalvik", ignoreCase = true)) {
                trustStoreInputStream = CoinbaseSSL::class.java.getResourceAsStream("/com/coinbase/api/ca-coinbase.bks")
                trustStore = KeyStore.getInstance("BKS")
            } else {
                trustStoreInputStream = CoinbaseSSL::class.java.getResourceAsStream("/com/coinbase/api/ca-coinbase.jks")
                trustStore = KeyStore.getInstance("JKS")
            }

            trustStore!!.load(trustStoreInputStream, "changeit".toCharArray())

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustStore)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, null)
            sslContext = ctx
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        } finally {
            if (trustStoreInputStream != null) {
                try {
                    trustStoreInputStream.close()
                } catch (ex: IOException) {
                    throw RuntimeException(ex)
                }

            }
        }

        return sslContext as SSLContext
    }
}
