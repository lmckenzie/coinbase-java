package com.coinbase


interface CallbackVerifier {
    /**
     * Verify authenticity of merchant callback from Coinbase
     */
    fun verifyCallback(body: String, signature: String): Boolean
}
