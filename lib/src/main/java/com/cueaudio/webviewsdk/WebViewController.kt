package com.cueaudio.webviewsdk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.URLUtil
import androidx.browser.customtabs.CustomTabsIntent

class InvalidUrlError(message: String): Exception(message)

class WebViewController(private val mContext: Context) {
    private val chromeAppID = "com.android.chrome"

    ///Checks validity of passed URL, starts new activity, navigates to the url in embedded WebView-object
    @Throws(InvalidUrlError::class)
    fun navigateTo(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val intent = Intent(mContext, WebViewActivity::class.java)
            intent.putExtra("url", url)
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
        var installed = false
        installed = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        return installed
    }
}
