package com.cueaudio.cuelights

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.cueaudio.webviewsdk.InvalidUrlError
import com.cueaudio.webviewsdk.WebViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        private const val VERSION = "test"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val webViewController = WebViewController(this)
        webViewController.isExitButtonHidden = true
        val mainActivity = this

        CoroutineScope(Dispatchers.IO).launch {
            val url =
                "https://dev-dxp.azurewebsites.net/api/light-show/get-version-url?version=$VERSION"
            val targetUrl = loadTargetUrl(url)
            try {
                webViewController.navigateTo(targetUrl)
                // Finish activity to keep only activity with webview
                mainActivity.finish()
            } catch (e: InvalidUrlError) {
                // Show invalid URL error message
                runOnUiThread {
                    Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTargetUrl(url: String): String {
        val connection = URL(url).openConnection()
        val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
        return reader.readText()
    }
}

