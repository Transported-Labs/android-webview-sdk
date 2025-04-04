package com.cueaudio.cuelightshow

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
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
    private var ignorePattern = "https://services"
    private var indexFileName = "index.json"
    private var gameAssetsPath = "games/light-show"
    private var contentLoadType = ContentLoadType.NONE
    private var logHandler: LogHandler? = null

    init {
        attachEventHandlers(webView)
    }

    fun navigateTo(url: String, logHandler: LogHandler? = null) {
        contentLoadType = ContentLoadType.NAVIGATE
        adjustOriginParams(url)
        this.logHandler = logHandler
        val isOffline = !isOnline()
        val offlineParam = if (isOffline) { "&offline=true" } else { "" }
        val urlNavigate = "$url$offlineParam"
        addToLog("*** Started new NAVIGATE process, offline mode = $isOffline ***")
        webView.loadUrl(urlNavigate)
    }

    fun prefetch(url: String, logHandler: LogHandler? = null) {
        contentLoadType = ContentLoadType.PREFETCH
        adjustOriginParams(url)
        this.logHandler = logHandler
        addToLog("*** Started new PREFETCH process ***")
        val urlObj = URL(url)
        val platformIndexUrl = "${urlObj.protocol}://${urlObj.host}/$indexFileName"
        val gameIndexUrl = "${urlObj.protocol}://${urlObj.host}/$gameAssetsPath/$indexFileName"
        makeCacheForIndex(platformIndexUrl)
        makeCacheForIndex(gameIndexUrl)
        webView.loadUrl(url)
//        makeCacheByList()

    }

    fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    println("Internet ON, NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    println("Internet ON, NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    println("Internet ON, NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
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
                when (contentLoadType) {
                    ContentLoadType.NONE ->  {}
                    ContentLoadType.PREFETCH ->  {
                        saveToCacheFromWebView(urlString)
                    }
                    ContentLoadType.NAVIGATE ->  {
                        if (!urlString.contains(ignorePattern)) {
                            val webResourceResponse = loadFromCache(urlString)
                            if (webResourceResponse != null) {
                                return webResourceResponse
                            } else {
                                val logMessage = "Loaded NOT from cache, from url: $urlString"
                                addToLog(logMessage)
                                saveToCacheFromWebView(urlString)
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

    private fun saveToCacheFromWebView(url: String) {
        if (url.contains(cachePattern) && !url.contains(ignorePattern)) {
            saveToCache(url)
        }
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

    private fun makeCacheByList() {
        val jsonList = loadJsonDataFromAsset("local.json")
        if (jsonList != null) {
            val linkArray = convertToArray(jsonList)
            if (linkArray != null) {
                for (i in 0 until linkArray.length()) {
                    val url = linkArray[i] as String
                    saveToCache(url)
                }
            }
        }
    }
    private fun makeCacheForIndex(indexUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonList = URL(indexUrl).readText()
                if (jsonList != null) {
                    val linkArray = convertToArray(jsonList)
                    if (linkArray != null) {
                        val pathToIndex = indexUrl.substringBeforeLast("/")
                        for (i in 0 until linkArray.length()) {
                            val relativeUrl = linkArray[i] as String
                            val absoluteUrl = "$pathToIndex/$relativeUrl"
                            saveToCache(absoluteUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                addToLog("Error loading index: ${e.localizedMessage}")
            }
        }
    }
    private fun loadJsonDataFromAsset(fileName: String): String? {
        val jsonString: String
        try {
            jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ioException: java.io.IOException) {
            addToLog("Error of loading file $fileName: ${ioException.localizedMessage}")
            return null
        }
        return jsonString
    }

    private fun convertToArray(source: String): JSONArray? {
        return try {
            JSONArray(source)
        } catch (e: JSONException) {
            addToLog("Error of converting: ${e.localizedMessage}")
            null
        }
    }
}