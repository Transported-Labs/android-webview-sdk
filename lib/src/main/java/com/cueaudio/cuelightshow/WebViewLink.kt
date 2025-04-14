package com.cueaudio.cuelightshow

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
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

class WebViewLink (private val context: Context, private val webView: WebView, private val cueSDK: CueSDK, private val tag: String = "") {
    private lateinit var mainOrigin: String
    private var cachePattern = ".com/files/"
    private var ignorePattern = "https://services"
    private var indexFileName = "index.json"
    private var gameAssetsPath = "games/light-show"
    private var contentLoadType = ContentLoadType.NONE

    private var networkStatus: String = ""
        set(value) {
            if (field != value ) {
                field = value
                cueSDK.notifyInternetConnection(field)
                addToLog("Network connection is ${field.uppercase()} ($tag)")
            }
        }

    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // network is available for use
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateNetworkStatus()
        }

        // lost network connection
        override fun onLost(network: Network) {
            super.onLost(network)
            updateNetworkStatus()
        }
    }


    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        attachEventHandlers(webView)
        networkStatus = receiveNetworkStatus()
    }

    private fun receiveNetworkStatus() = if (isOnline()) "on" else "off"

    private fun updateNetworkStatus() {
        Handler(Looper.getMainLooper()).postDelayed({
            networkStatus = receiveNetworkStatus()
        }, 50)
    }

    fun navigateTo(url: String) {
        contentLoadType = ContentLoadType.NAVIGATE
        adjustOriginParams(url)
        val isOnline = isOnline()
        val offlineParam = if (!isOnline) { "&offline=true" } else { "" }
        val urlNavigate = "$url$offlineParam"
        addToLog("*** Started new NAVIGATE process, offline mode = ${!isOnline }***")
        webView.loadUrl(urlNavigate)
    }

    fun prefetch(url: String) {
        contentLoadType = ContentLoadType.PREFETCH
        adjustOriginParams(url)
        addToLog("*** Started new PREFETCH process ***")
        val urlObj = URL(url)
        val platformIndexUrl = "${urlObj.protocol}://${urlObj.host}/$indexFileName"
        val gameIndexUrl = "${urlObj.protocol}://${urlObj.host}/$gameAssetsPath/$indexFileName"
        makeCacheForIndex(platformIndexUrl)
        makeCacheForIndex(gameIndexUrl)
        webView.loadUrl(url)
//        makeCacheByList()

    }

    private fun isOnline(): Boolean {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                println("Internet ON, NetworkCapabilities.NET_CAPABILITY_VALIDATEDT")
                return true
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
        LogHandlerHolder.logHandler?.let { it(logMessage) }
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