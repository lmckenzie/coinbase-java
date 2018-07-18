package com.coinbase

import android.util.Base64
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

class CallbackVerifierImpl : com.coinbase.CallbackVerifier {

    override fun verifyCallback(body: String, signature: String): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(body.toByteArray())
            sig.verify(Base64.decode(signature, Base64.DEFAULT))
        } catch (ex: NoSuchAlgorithmException) {
            throw RuntimeException(ex)
        } catch (ex: InvalidKeyException) {
            throw RuntimeException(ex)
        } catch (e: SignatureException) {
            false
        }

    }

    companion object {
        private val publicKey: PublicKey by lazy {
            try {
                val keyStream = CallbackVerifierImpl::class.java.getResourceAsStream("/com/coinbase/api/coinbase-callback.pub.der")
                val keyBytes = IOUtils.toByteArray(keyStream)
                val keySpec = X509EncodedKeySpec(keyBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                keyFactory.generatePublic(keySpec)
            } catch (ex: NoSuchAlgorithmException) {
                throw RuntimeException(ex)
            } catch (ex: IOException) {
                throw RuntimeException(ex)
            } catch (ex: InvalidKeySpecException) {
                throw RuntimeException(ex)
            }
        }
    }
}
