package com.cueaudio.webviewsdk

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.webkit.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.parcelize.Parcelize
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebViewActivity : AppCompatActivity() {

    private val cueSDKName = "cueSDK"
    private lateinit var webView: WebView
    private val userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.lib_main)
        webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = userAgentString
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse != null) {
                    alertInternetError(errorResponse.reasonPhrase)
                }
            }
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (error != null) {
                    alertInternetError(error.description.toString())
                }
            }
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (description != null) {
                    alertInternetError(description.toString())
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        val cueSDK = CueSDK(this, webView)
        webView.addJavascriptInterface(cueSDK, cueSDKName)

        val url = intent.getStringExtra("url")
        if (url != null) {
            webView.loadUrl(url)
        }
    }

    private fun alertInternetError(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}