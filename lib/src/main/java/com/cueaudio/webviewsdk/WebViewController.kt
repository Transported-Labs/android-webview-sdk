package com.cueaudio.webviewsdk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

class InvalidUrlError(message: String): Exception(message)

typealias ProgressHandler = (Int) -> Unit

class WebViewController(private val mContext: Context) {
    private val chromeAppID = "com.android.chrome"
    var isExitButtonHidden = false

    @Throws(InvalidUrlError::class)
    fun prefetch(url: String, progressHandler: ProgressHandler? = null) {
        if (URLUtil.isValidUrl(url)) {
            val webView = WebView(mContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (errorResponse != null) {
                        toastMessage(errorResponse.reasonPhrase)
                    }
                }
            }
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (progressHandler != null) {
                        progressHandler(newProgress)
                    }
                }
            }
            webView.loadUrl(url)
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }

    ///Checks validity of passed URL, starts new activity, navigates to the url in embedded WebView-object
    @Throws(InvalidUrlError::class)
    fun navigateTo(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val intent = Intent(mContext, WebViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("isExitButtonHidden", isExitButtonHidden)
            mContext.startActivity(intent)
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }

    ///Checks validity of passed URL, navigates to the url in Chrome Custom Tabs
    @Throws(InvalidUrlError::class)
    fun openInChrome(url: String) {
        if (URLUtil.isValidUrl(url)) {
            if (isAppInstalled(chromeAppID)) {
                    val intentCustomTabs = CustomTabsIntent.Builder()
                        .setUrlBarHidingEnabled(true)
                        .setShowTitle(false).build()
                    intentCustomTabs.intent.setPackage(chromeAppID);
                    intentCustomTabs.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intentCustomTabs.launchUrl(mContext, Uri.parse(url))
            } else {
                throw InvalidUrlError("Browser is not installed: '$chromeAppID'")
            }
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }
    @Suppress("DEPRECATION")
    private fun isAppInstalled(packageName: String): Boolean {
        val pm: PackageManager = mContext.packageManager
        var installed: Boolean = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        return installed
    }

    private fun toastMessage(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
    }
}
