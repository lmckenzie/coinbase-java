package com.coinbase.coinbasesample

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.coinbase.CallbackWithRetrofit
import com.coinbase.v2.models.account.Accounts
import com.coinbase.v2.models.account.Data
import com.coinbase.v2.models.transactions.Transactions
import kotlinx.android.synthetic.main.activity_transactions.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import java.util.*

class TransactionsActivity : AppCompatActivity() {

    lateinit var account: Data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)


        @Suppress("DEPRECATION")
        val dialog = ProgressDialog.show(this, "Loading Accounts", null)

        coinbase().getAccounts(HashMap(), object : CallbackWithRetrofit<Accounts> {
            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call<Accounts>, response: Response<Accounts>?, retrofit: Retrofit) {
                if (response?.isSuccessful == true) {
                    response.body()?.data?.let { accounts ->
                        Log.d("getAccount", "Got " + accounts.size + " accounts")
                        account = accounts[0]
                        accountsText.text = "Loaded account: ${account.name}"
                        getTransactions()
                    }
                } else {
                    Log.w("getAccounts", retrofit.errorMessage(response))
                }
                dialog.dismiss()
            }

            override fun onFailure(call: Call<Accounts>, t: Throwable) {
                dialog.dismiss()
            }
        })
    }

    private fun getTransactions() {
        coinbase().getTransactions(account.id, HashMap(), ArrayList(), object : CallbackWithRetrofit<Transactions> {
            override fun onResponse(call: Call<Transactions>, response: Response<Transactions>?, retrofit: Retrofit) {
                if (response?.isSuccessful == true) {
                    response.body()?.data?.let { transactions ->
                        Log.d("getTransactions", "Got ${transactions.size} transactions")
                    }
                } else {
                    Log.w("getTransactions", retrofit.errorMessage(response))
                }
            }

            override fun onFailure(call: Call<Transactions>, t: Throwable) {

            }
        })
    }
}
