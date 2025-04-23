package com.cueaudio.cuelightshow

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/// Features:
/// 1. Shows in console HttpError
/// 2. Intercepts WebResourceRequest with handler
open class CueWebViewClient() : WebViewClient() {
    var onInterceptUrlLoad: ((String) -> WebResourceResponse?)? = null

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (errorResponse != null) {
            println("Received Http Error: ${errorResponse.reasonPhrase}")
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val urlString = request.url.toString()
        // Invoke the handler and return its response if non-null
        return onInterceptUrlLoad?.invoke(urlString) ?: super.shouldInterceptRequest(view, request)
    }


}