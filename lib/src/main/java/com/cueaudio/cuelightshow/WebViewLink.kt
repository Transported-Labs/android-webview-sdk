package com.cueaudio.cuelightshow

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

enum class ContentLoadType {
    NONE,
    PREFETCH,
    NAVIGATE
}

typealias LogHandler = (String) -> Unit

class WebViewLink (private val context: Context, private val webView: WebView) {
    private lateinit var mainOrigin: String
    private var cachePattern = ".com/files/"
    private var contentLoadType = ContentLoadType.NONE
    private var logHandler: LogHandler? = null

    init {
        attachEventHandlers(webView)
    }

    fun navigateTo(url: String, logHandler: LogHandler? = null) {
        contentLoadType = ContentLoadType.NAVIGATE
        adjustOriginParams(url)
        this.logHandler = logHandler
        addToLog("*** Started new NAVIGATE process ***")
        webView.loadUrl(url)
    }

    fun prefetch(url: String, logHandler: LogHandler? = null) {
        contentLoadType = ContentLoadType.PREFETCH
        adjustOriginParams(url)
        this.logHandler = logHandler
        addToLog("*** Started new PREFETCH process ***")
        webView.loadUrl(url)
    }

    private fun adjustOriginParams(url: String) {
        val urlObj = URL(url)
        mainOrigin = "${urlObj.protocol}://${urlObj.host}"
        cachePattern = ".${urlObj.host.substringAfterLast(".")}/files/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attachEventHandlers(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse != null) {
                    println("Received Http Error: ${errorResponse.reasonPhrase}")
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val urlString = request.url.toString()
                if (urlString.contains(cachePattern)) {
                    when (contentLoadType) {
                        ContentLoadType.NONE ->  {}
                        ContentLoadType.PREFETCH ->  {
                            saveToCache(urlString)
                        }
                        ContentLoadType.NAVIGATE ->  {
                            val webResourceResponse = loadFromCache(urlString)
                            if (webResourceResponse != null) {
                                return webResourceResponse
                            } else {
                                val logMessage = "Loaded NOT from cache, from url: $urlString"
                                addToLog(logMessage)
                            }
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun toastMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun loadFromCache(url: String): WebResourceResponse? {
        val mimeType = IoUtils.getMimeTypeFromUrl(url)
        val (inputStream, logMessage) = IoUtils.loadMediaFromFileUrl(context, url)
        addToLog(logMessage)
        if (inputStream != null) {
            val headers: MutableMap<String, String> = HashMap()
            headers["Access-Control-Allow-Origin"] = mainOrigin
            return WebResourceResponse(mimeType, "UTF-8", 200,
                "OK", headers, inputStream)
        }
        return null
    }

    private fun saveToCache(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val logMessage = IoUtils.downloadToFile(context, url)
            addToLog(logMessage)
        }
    }

    private fun addToLog(logMessage: String) {
        logHandler?.let { it(logMessage) }
        println("Files log: $logMessage")
    }
}