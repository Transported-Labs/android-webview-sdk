package com.cueaudio.cuelightshow

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import com.cueaudio.cuelightshow.WebViewActivity.Companion.CUE_SDK_NAME

class InvalidUrlError(message: String): Exception(message)

///Helper singleton object to pass the lambda handler to activity
object LogHandlerHolder {
    var logHandler: LogHandler? = null
}

class WebViewController(private val context: Context) {
    var isExitButtonHidden = false
    private val prefetchWebView: WebView = WebView(context)
    private val cueSDK: CueSDK = CueSDK(context, prefetchWebView)
    private val prefetchWebViewLink: WebViewLink = WebViewLink(context, prefetchWebView, cueSDK, "Prefetch WebView")

    init {
        prefetchWebView.addJavascriptInterface(cueSDK, CUE_SDK_NAME)
    }

    ///Checks validity of passed URL, starts new activity, navigates to the url in embedded WebView-object
    @Throws(InvalidUrlError::class)
    fun navigateTo(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra("url", url)
            intent.putExtra("isExitButtonHidden", isExitButtonHidden)
            context.startActivity(intent)
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }

    @Throws(InvalidUrlError::class)
    fun prefetch(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val urlPreload = "${url}&preload=true"
            prefetchWebViewLink.prefetch(urlPreload)
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }

    ///Checks validity of passed URL, navigates to the url in Chrome Custom Tabs
    @Throws(InvalidUrlError::class)
    fun openInChrome(url: String) {
        val chromeAppID = "com.android.chrome"
        if (URLUtil.isValidUrl(url)) {
            if (isAppInstalled(chromeAppID)) {
                    val intentCustomTabs = CustomTabsIntent.Builder()
                        .setUrlBarHidingEnabled(true)
                        .setShowTitle(false).build()
                    intentCustomTabs.intent.setPackage(chromeAppID);
                    intentCustomTabs.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intentCustomTabs.launchUrl(context, Uri.parse(url))
            } else {
                throw InvalidUrlError("Browser is not installed: '$chromeAppID'")
            }
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }
    @Suppress("DEPRECATION")
    private fun isAppInstalled(packageName: String): Boolean {
        val pm: PackageManager = context.packageManager
        val installed: Boolean = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        return installed
    }

    fun showCache(): String {
        return IoUtils.showCache(context)
    }

    fun clearCache(): String {
        return IoUtils.clearCache(context)
    }
}
