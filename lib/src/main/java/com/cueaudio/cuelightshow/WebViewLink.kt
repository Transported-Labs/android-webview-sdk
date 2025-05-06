package com.cueaudio.cuelightshow

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.net.URL

enum class ContentLoadType {
    NONE,
    PREFETCH,
    NAVIGATE
}

typealias LogHandler = (String) -> Unit

class WebViewLink(private val context: Context,private val webView: WebView, webViewClient: CueWebViewClient? = null){
    private lateinit var mainOrigin: String
    private var cachePattern = "/files/"
    private val ignorePattern = "https://services"
    private val indexFileName = "index.json"
    private val gameAssetsPath = "games/light-show"
    private var contentLoadType = ContentLoadType.NONE
    // Property to hold the custom handler
    var onNetworkStatusChange: ((String) -> Unit)? = null
    private val cueWebViewClient = webViewClient ?: CueWebViewClient()
    
    private var networkStatus: String = ""
        set(value) {
            if (field != value ) {
                field = value
                onNetworkStatusChange?.invoke(field)
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

    // Additional constructor for Java compatibility
    constructor(context: Context, webView: WebView) : this(context, webView, null)

    init {
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        adjustWebViewSettings(webView)
        networkStatus = receiveNetworkStatus()
        cueWebViewClient.onInterceptUrlLoad = { urlString ->
            when (contentLoadType) {
                ContentLoadType.NONE -> null // No operation
                ContentLoadType.PREFETCH -> {
                    saveToCacheFromWebView(urlString)
                    null
                }
                ContentLoadType.NAVIGATE -> {
                    if (urlString.contains(ignorePattern)) {
                        null // Ignore URLs matching the pattern
                    } else {
                        loadFromCache(urlString) ?: run {
                            // Log and save to cache if not loaded from cache
                            addToLog("Loaded NOT from cache, from url: $urlString")
                            saveToCacheFromWebView(urlString)
                            null
                        }
                    }
                }
            }
        }
        webView.webViewClient = cueWebViewClient
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

    fun prefetchJSONData(url: String) {
        val urlObj = URL(url)
        val platformIndexUrl = "${urlObj.protocol}://${urlObj.host}/$indexFileName"
        val gameIndexUrl = "${urlObj.protocol}://${urlObj.host}/$gameAssetsPath/$indexFileName"
        makeCacheForIndex(platformIndexUrl)
        makeCacheForIndex(gameIndexUrl)
    }

    fun prefetch(urlPreload: String) {
        if (networkStatus == "on") {
            contentLoadType = ContentLoadType.PREFETCH
            adjustOriginParams(urlPreload)
            addToLog("*** Started new PREFETCH process ***")
            prefetchJSONData(urlPreload)
            webView.loadUrl(urlPreload)
        } else {
            addToLog("*** Skipped PREFETCH for OFFLINE mode ***")
        }
    }

    private fun isOnline(): Boolean {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                println("Internet ON, NetworkCapabilities.NET_CAPABILITY_VALIDATED")
                return true
            }
        }
        return false
    }

    private fun adjustOriginParams(url: String) {
        val urlObj = URL(url)
        mainOrigin = "${urlObj.protocol}://${urlObj.host}"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun adjustWebViewSettings(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
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
            saveToCache(url, false)
        }
    }

    private fun saveToCache(url: String, isOverwrite: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val logMessage = IoUtils.downloadToFile(context, url, isOverwrite)
            addToLog(logMessage)
        }
    }

    private fun addToLog(logMessage: String) {
        AppLog.addTo(logMessage)
    }

    private fun hasNewLink(path: String, linkArray: JSONArray): Boolean {
        for (i in 0 until linkArray.length()) {
            val relativeUrl = linkArray[i] as String
            val url = "$path/$relativeUrl"
            val fileName = IoUtils.makeFileNameFromUrl(context, url)
            val outFile = File(fileName)
            if (!outFile.exists()) {
                addToLog("File is not found in cache, need to update cache. File: $relativeUrl")
                return true
            }
        }
        addToLog("All files listed in JSON for url: $path are already in cache.")
        return false
    }

    private fun downloadFiles(path: String, linkArray: JSONArray) {
        for (i in 0 until linkArray.length()) {
            val relativeUrl = linkArray[i] as String
            val absoluteUrl = "$path/$relativeUrl"
            saveToCache(absoluteUrl, true)
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
                        if (hasNewLink(pathToIndex, linkArray)) {
                            downloadFiles(pathToIndex, linkArray)
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