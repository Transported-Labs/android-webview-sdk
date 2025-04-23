package com.cueaudio.cuelightshow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

enum class CameraLayoutType {
    BOTH,
    PHOTO_ONLY,
    VIDEO_ONLY
}

class WebViewActivity : AppCompatActivity() {
    companion object {
        private const val MAXIMUM_LEVEL = 1f
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    private lateinit var webViewLayout: View
    private lateinit var exitButton: ImageButton
    private lateinit var webView: WebView
    private lateinit var cueWebChromeClient: CueWebChromeClient
    private lateinit var cameraPreview: CameraPreview
    private lateinit var cueSDK: CueSDK
    private lateinit var webViewLink: WebViewLink
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            cueWebChromeClient.handleActivityResult(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.lib_main)
        webViewLayout = findViewById(R.id.webViewLayout)
        exitButton = findViewById(R.id.exitButton)
        webView = findViewById(R.id.webView)
        cueWebChromeClient = CueWebChromeClient(this, activityResultLauncher)
        webView.webChromeClient = cueWebChromeClient
        cameraPreview = findViewById(R.id.cameraLayout)
        cueSDK = CueSDK(this, webView) // addJavascriptInterface is called inside this init
        webViewLink = WebViewLink(this, webView) // webViewClient is added inside this init
        webViewLink.onNetworkStatusChange = { status ->
            cueSDK.notifyInternetConnection(status)
            AppLog.addTo("Network connection is ${status.uppercase()} (Lightshow WebView)")
        }
        // Set up cueSDK <-> cameraPreview handlers
        cameraPreview.onCameraActivated = { cameraControl ->
            // Set up cameraControl to use flash during preview
            cueSDK.previewCameraControl = cameraControl
        }
        cueSDK.onCameraShow = { cameraLayoutType ->
            cameraPreview.startCamera(cameraLayoutType)
        }
        cameraPreview.checkAndRequestPermissions(this) {
            Toast.makeText(baseContext, "Please set up Camera permission", Toast.LENGTH_SHORT)
                .show()
        }
        if (!allPermissionsGranted()) {
            Toast.makeText(baseContext, "Please set up Microphone and Camera permissions", Toast.LENGTH_SHORT)
                .show()
        }

        val url = intent.getStringExtra("url")
        if (url != null) {
            webViewLink.navigateTo(url)
        }
        val isExitButtonHidden = intent.getBooleanExtra("isExitButtonHidden", false)
        exitButton.visibility = if (isExitButtonHidden) View.GONE else View.VISIBLE
        exitButton.setOnClickListener {
            webView.loadUrl("about:blank")
            finish()
        }
        setScreenBrightness(MAXIMUM_LEVEL)
    }

    private fun setScreenBrightness(x: Float) = window?.apply {
        attributes = attributes?.apply { screenBrightness = x }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onPause() {
        super.onPause()
        // To stop the audio / video playback
        webView.loadUrl("javascript:document.location=document.location")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST,
            PermissionConstant.ASK_MICROPHONE_REQUEST,
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                val granted = (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                cueSDK.callCurPermissionRequestGranted(granted)
            }
        }
    }
}

