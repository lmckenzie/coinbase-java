package com.coinbase.coinbasesample

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.coinbase.CallbackWithRetrofit
import com.coinbase.auth.AccessToken
import com.coinbase.v2.models.user.User
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    companion object {
        // NOTE: Go to https://www.coinbase.com/oauth/applications/new
        // to create an application and generate your own keys
        private const val API_KEY = "f89d5f52d5bf6678b6449e0b6feb5100bf8e0ed3dc45f5f2be51fcea1232111c"
        private const val API_SECRET = "9fbb237f4bc9c977f5e88895882b5677c4de395fe0996a6eedf912d4fee2b415"

        class CompleteAuthorizationTask constructor(mainActivity: MainActivity, private val intent: Intent) : AsyncTask<Void, Void, Response<AccessToken>>() {

            private val contextWeakReference = WeakReference(mainActivity)

            public override fun doInBackground(vararg params: Void): Response<AccessToken>? {
                contextWeakReference.get()?.let { mainActivity ->
                    return mainActivity.oAuth().completeAuthorization(mainActivity, API_KEY, API_SECRET, intent.data!!)
                }
                return null
            }

            public override fun onPostExecute(tokens: Response<AccessToken>?) {
                contextWeakReference.get()?.let { mainActivity ->
                    if (tokens?.isSuccessful == true) {
                        mainActivity.coinbase().init(mainActivity, tokens.body()?.accessToken!!)
                        mainActivity.getUser()
                    } else {
                        mainActivity.handleLoginError("Authorization failed")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // In the Activity we set up to listen to our redirect URI
        val intent = intent
        if (intent != null && intent.action != null && intent.action == "android.intent.action.VIEW") {
            CompleteAuthorizationTask(this, intent).execute()
        }

        enableButtons(false)

        val oauth = (applicationContext as MainApplication).oAuth
        authBtn.setOnClickListener {
            oauth.beginAuthorization(this@MainActivity,
                    API_KEY,
                    "wallet:user:read,wallet:accounts:read",
                    "coinbase-sample-app://coinbase-oauth", null)

        }

        transactionsBtn.setOnClickListener { startActivity(Intent(this@MainActivity, TransactionsActivity::class.java)) }

        dataBtn.setOnClickListener { startActivity(Intent(this@MainActivity, DataActivity::class.java)) }
    }

    private fun getUser() {
        val coinbase = (applicationContext as MainApplication).client
        coinbase.getUser(object : CallbackWithRetrofit<User> {
            override fun onResponse(call: Call<User>, response: Response<User>?, retrofit: Retrofit) {
                if (response?.isSuccessful == true) {
                    userText.text = String.format("User: %s", response.body()?.data?.name ?: "unknown")
                    enableButtons(true)
                } else {
                    handleLoginError(retrofit.errorMessage(response))
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                handleLoginError(null)
            }
        })
    }

    private fun enableButtons(enabled: Boolean) {
        transactionsBtn.isEnabled = enabled
    }

    private fun handleLoginError(message: String?) {
        userText.text = message ?: "Login error occurred"
        enableButtons(false)
    }
}
