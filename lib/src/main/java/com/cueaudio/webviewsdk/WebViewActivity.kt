package com.cueaudio.webviewsdk

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.media.MediaActionSound.SHUTTER_CLICK
import android.media.MediaActionSound.START_VIDEO_RECORDING
import android.media.MediaActionSound.STOP_VIDEO_RECORDING
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.updateLayoutParams
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CameraLayoutType {
    BOTH,
    PHOTO_ONLY,
    VIDEO_ONLY
}

class WebViewActivity : AppCompatActivity() {
    companion object {
        private val TAG = WebViewActivity::class.qualifiedName
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss"
        private const val PHOTO_FILE_PREFIX = "cue-"
        private const val VIDEO_FILE_PREFIX = "video-"
        private const val CUE_FOLDER_NAME = "CUE Live"
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
    private val cueSDKName = "cueSDK"
    private lateinit var webViewLayout: View
    private lateinit var exitButton: ImageButton
    private lateinit var webView: WebView
    private lateinit var cameraTextureView: PreviewView
    private lateinit var cameraLayout: View
    private lateinit var closeButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var imageButton: ImageButton

    private var curCameraLayoutType = CameraLayoutType.BOTH
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cueSDK: CueSDK
    private val userAgentString =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.lib_main)
        webViewLayout = findViewById(R.id.webViewLayout)
        exitButton = findViewById(R.id.exitButton)
        webView = findViewById(R.id.webView)
        cameraTextureView = findViewById(R.id.cameraTextureView)
        cameraLayout = findViewById(R.id.cameraLayout)
        closeButton = findViewById(R.id.closeButton)
        videoButton = findViewById(R.id.videoButton)
        imageButton = findViewById(R.id.imageButton)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.userAgentString = userAgentString
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (errorResponse != null) {
                    toastMessage(errorResponse.reasonPhrase)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (description != null) {
                    toastMessage(description.toString())
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }


        }

        cameraLayout.visibility = View.GONE
        cueSDK = CueSDK(this, webView)
        webView.addJavascriptInterface(cueSDK, cueSDKName)

        val url = intent.getStringExtra("url")
        if (url != null) {
            webView.loadUrl(url)
        }
        val isExitButtonHidden = intent.getBooleanExtra("isExitButtonHidden", false)
        exitButton.visibility = if (isExitButtonHidden) View.GONE else View.VISIBLE
        exitButton.setOnClickListener {
            webView.loadUrl("about:blank")
            finish()
        }
        closeButton.setOnClickListener {
            runOnUiThread {
                cameraLayout.visibility = View.GONE
                webViewLayout.visibility = View.VISIBLE
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        videoButton.setOnClickListener { captureVideo() }
        imageButton.setOnClickListener { takePhoto() }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = PHOTO_FILE_PREFIX+SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$CUE_FOLDER_NAME")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    playSound(SHUTTER_CLICK)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        videoButton.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = VIDEO_FILE_PREFIX+SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$CUE_FOLDER_NAME")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@WebViewActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        playSound(START_VIDEO_RECORDING)
                        videoButton.apply {
                            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_record_video_active))
                            isEnabled = true
                        }
                        adjustButtonsVisibility(true)
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        playSound(STOP_VIDEO_RECORDING)
                        videoButton.apply {
                            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_record_video_inactive))
                            isEnabled = true
                        }
                        adjustButtonsVisibility(false)
                    }
                }
            }
    }

    fun playSound(soundType: Int) {
        val sound = MediaActionSound()
        sound.play(soundType)
    }

    private fun adjustButtonsVisibility(isRecording: Boolean) {
        if (curCameraLayoutType == CameraLayoutType.BOTH) {
            imageButton.visibility = if (isRecording) View.GONE else View.VISIBLE
        }
        closeButton.visibility = if (isRecording) View.GONE else View.VISIBLE
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun initCamera() {
        if (!allPermissionsGranted()) {
            toastMessage("Please set up Microphone and Camera permissions")
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraTextureView.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture)
                camera.apply {
                    if (cameraInfo.hasFlashUnit()) {
                        // Set up cameraControl to use flash during preview
                        cueSDK.previewCameraControl = cameraControl
                    }
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun startCamera(cameraLayoutType : CameraLayoutType) {
        curCameraLayoutType = cameraLayoutType
        if ((imageCapture == null) || (videoCapture == null)) {
            initCamera()
        }
        runOnUiThread {
            webViewLayout.visibility = View.GONE
            cameraLayout.visibility = View.VISIBLE
            when(curCameraLayoutType){
                CameraLayoutType.BOTH ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.VISIBLE
                    imageButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        endToEnd = ConstraintLayout.LayoutParams.UNSET
                    }
                }
                CameraLayoutType.VIDEO_ONLY ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.GONE
                }
                CameraLayoutType.PHOTO_ONLY ->{
                    videoButton.visibility = View.GONE
                    imageButton.visibility = View.VISIBLE
                    imageButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
            }
        }
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

    private fun toastMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}

