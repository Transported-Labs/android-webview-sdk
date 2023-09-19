package com.cueaudio.webviewsdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil

class InvalidUrlError(message: String): Exception(message)

class WebViewController(private val mContext: Context) {

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

    ///Checks validity of passed URL, navigates to the url in default browser
    @Throws(InvalidUrlError::class)
    fun openInBrowser(url: String) {
        if (URLUtil.isValidUrl(url)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                mContext.startActivity(intent)
            }
        } else {
            throw InvalidUrlError("Invalid URL: '$url'")
        }
    }
}
