package com.cueaudio.cuelights

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.cueaudio.cuelightshow.InvalidUrlError
import com.cueaudio.cuelightshow.WebViewController
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
                "https://services.developdxp.com/v1/light-show/api/get-version-url?version=$VERSION"
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
        return try {
            val connection = URL(url).openConnection()
            val reader = BufferedReader(InputStreamReader(connection.getInputStream()))
            reader.use { it.readText() } // Safely read and close the reader
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
            ""
        }
    }

}

