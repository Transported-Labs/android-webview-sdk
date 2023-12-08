package com.cueaudio.webviewsdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.Date


class WebViewActivity : AppCompatActivity() {
    @SuppressLint("SimpleDateFormat")
    private val fileFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    private val cueFolderName = "CUE Live"
    private val photoFilePrefix = "cue-"
    private val videoFilePrefix = "video-"

    private val cueSDKName = "cueSDK"
    private val openCameraMethodName = "openCamera"
    private val openPhotoCameraMethod = "openPhotoCamera"
    private val openVideoCameraMethod = "openVideoCamera"
    private lateinit var webViewLayout: View
    private lateinit var exitButton: ImageButton
    private lateinit var webView: WebView
    private lateinit var cameraTextureView: TextureView
    private lateinit var cameraLayout: View
    private lateinit var closeButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var imageButton: ImageButton
    lateinit var cameraManager: CameraManager
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var videoCaptureSession: CameraCaptureSession
    lateinit var imageCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var captureRequest: CaptureRequest
    lateinit var capReq: CaptureRequest.Builder
    lateinit var imageReader : ImageReader
    private lateinit var videoHandler: Handler
    private lateinit var cueSDK: CueSDK
    private val userAgentString =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1"
    private var isFlashlightOn = false
    var isCameraOn = false
    private var isRecording = false
    private var isSparkling = false
    private val videoHandlerThread: HandlerThread = HandlerThread("videoThread")
    private val torchCallback: CameraManager.TorchCallback =
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                super.onTorchModeChanged(cameraId, enabled)
                isFlashlightOn = enabled
            }
        }

    private var flashThread: Thread = Thread("flashThread")


    private val mediaRecorder by lazy {
        MediaRecorder()
    }
    private lateinit var currentVideoFilePath: String

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
        cameraTextureView.surfaceTexture?.setDefaultBufferSize(1080, 720)
        cameraTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }
        }

        cameraLayout.visibility = View.GONE
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.registerTorchCallback(torchCallback, null)
        videoHandlerThread.start()
        videoHandler = Handler((videoHandlerThread).looper)
        cueSDK = CueSDK(this, webView)
        webView.addJavascriptInterface(cueSDK, cueSDKName)

        val url = intent.getStringExtra("url")
        if (url != null) {
            webView.loadUrl(url)
        }

        exitButton.setOnClickListener {
            webView.loadUrl("about:blank")
            finish()
        }
        closeButton.setOnClickListener {
            closeCamera()
            runOnUiThread {
                cameraLayout.visibility = View.GONE
                webViewLayout.visibility = View.VISIBLE
            }
        }

        videoButton.setOnClickListener {

            if (isRecording) {
                if (!flashThread.isInterrupted) {

                    flashThread.interrupt()
                    isFlashlightOn = false
                }
                isRecording = false
                runOnUiThread {
                    videoButton.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.record_start_s))
                }
                stopRecordSession()
                Toast.makeText(applicationContext, "Stop recording", Toast.LENGTH_SHORT).show()
            } else {
                runOnUiThread {
                    videoButton.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.record_stop_s))
                }
                if (flashThread.isInterrupted) {
                    startRecordSession()
                } else {
                    flashThread.interrupt()
                    startRecordSession()
                    Toast.makeText(applicationContext, "Start recording", Toast.LENGTH_SHORT).show()
                }
            }
        }

        imageButton.setOnClickListener(){
            capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            capReq.addTarget(imageReader.surface)
            cameraCaptureSession.capture(capReq.build(), null, null)

        }

        imageReader = ImageReader.newInstance(1080, 720, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener(object:ImageReader.OnImageAvailableListener{
            override fun onImageAvailable(reader: ImageReader?) {
                try {
                    var image = reader?.acquireLatestImage()
                    var buffer= image!!.planes[0].buffer
                    var bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    var file = createImageFile()
                    var opStream = FileOutputStream(file)

                    opStream.write(bytes)
                    opStream.close()
                    image.close()
                    capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val surface = Surface(cameraTextureView.surfaceTexture)
                    capReq.addTarget(surface)
                    cameraCaptureSession.capture(capReq.build(),null,null)
                    Toast.makeText(applicationContext, "Image is captured", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    toastMessage("Error occurred: ${e.localizedMessage}")
                }
            }
        }, videoHandler)
    }


    private fun closeCamera() {
        isCameraOn = false
        if (this::cameraCaptureSession.isInitialized)
            cameraCaptureSession.close()
        if (this::cameraDevice.isInitialized)
            cameraDevice.close()
//        if(!flashThread.isInterrupted)
//            flashThread.interrupt()

    }

    fun startCamera(cameraMethod : String) {
        runOnUiThread {
            webViewLayout.visibility = View.GONE
            cameraLayout.visibility = View.VISIBLE
            when(cameraMethod){
                openCameraMethodName ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.VISIBLE
                }
                openVideoCameraMethod ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.GONE
                }
                openPhotoCameraMethod ->{
                    videoButton.visibility = View.GONE
                    imageButton.visibility = View.VISIBLE
                }
            }
        }
        if (cameraTextureView.isAvailable) {
            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        isCameraOn = true
        cameraManager.openCamera(cameraManager.cameraIdList[0],
            @SuppressLint("MissingPermission")
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        cameraDevice = camera
                        capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        cameraTextureView.surfaceTexture?.setDefaultBufferSize(1080,720)
                        val surface = Surface(cameraTextureView.surfaceTexture)
                        capReq.addTarget(surface)
                        capReq.set(CaptureRequest.JPEG_ORIENTATION, 90)
                        capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        capReq.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )


                        cameraDevice.createCaptureSession(
                            listOf(surface, imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    cameraCaptureSession = session
                                    cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {

                                }
                            },
                            videoHandler
                        )
                    } catch (e: Exception) {
                        toastMessage("Error occurred: ${e.localizedMessage}")
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice = camera
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice = camera
                    camera.close()
                }


            }, videoHandler
        )


    }

    private fun recordSession() {

        setupMediaRecorder()

        val surfaceTexture = cameraTextureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(1080, 720)
        val textureSurface = Surface(surfaceTexture)
        val recordSurface = mediaRecorder.surface

        capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        capReq.addTarget(textureSurface)
        capReq.addTarget(recordSurface)
        val surfaces = ArrayList<Surface>().apply {
            add(textureSurface)
            add(recordSurface)
        }

        cameraDevice.createCaptureSession(surfaces,
            object : CameraCaptureSession.StateCallback() {


                override fun onConfigured(session: CameraCaptureSession) {
                    if (session != null) {
                        videoCaptureSession = session
                        capReq.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                        videoCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                        isRecording = true
                        mediaRecorder.start()

                        cameraCaptureSession.close()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "creating record session failed!")
                }

            }, videoHandler
        )
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

    fun advancedFlashTurn(time: Int) {
        val advancedFlashTread = Thread {
            turnTorch(true)
        }
        advancedFlashTread.start()
        Handler(Looper.getMainLooper()).postDelayed({
            turnTorch(false)
            advancedFlashTread.interrupt()
        }, time.toLong())
    }

    fun turnTorch(isOn: Boolean, isJavaScriptCallbackNeeded: Boolean = true) {
        try {
            if (isOn) {
                capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            if (this::cameraCaptureSession.isInitialized) {
            } else if (this::videoCaptureSession.isInitialized) {
                videoCaptureSession.setRepeatingRequest(capReq.build(), null, videoHandler)
            }

            try {
                cameraCaptureSession.setRepeatingRequest(capReq.build(), null, videoHandler)
                return
            } catch (e: Exception) {
                Log.i("torch cameraCapture session", e.localizedMessage)
            }

            try {
                videoCaptureSession.setRepeatingRequest(capReq.build(), null, videoHandler)
                return
            } catch (e: Exception) {
                Log.i("torch video Capture session", e.localizedMessage)
            }
        } catch (e: Exception) {
            Log.i("torch CameraAccessDenied", e.localizedMessage)
        }
    }


    fun sparkle(duration: Int?) {
        if (duration != null) {
            isSparkling = true
            flashThread = Thread {
                var isOn = false
                val blinkDelay: Long = 50
                while (isSparkling) {
                    isOn = !isOn
                    turnTorch(isOn, false)
                    try {
                        Thread.sleep(blinkDelay)
                    } catch (e: InterruptedException) {
                        isSparkling = false
                    }
                }
            }
            flashThread.name = "flashThread"
            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
                isSparkling = false
            }, duration.toLong())
        } else {
            Log.i("Duration", "Duration is not valid value")
        }
    }

    private fun createVideoFileName(): String {
        val timestamp = fileFormat.format(Date())
        return "$videoFilePrefix${timestamp}.mp4"
    }

    private fun createVideoFile(): File {
        val videoFolder = Environment.DIRECTORY_MOVIES.toString()
        val videoFile = File(
            Environment.getExternalStoragePublicDirectory("$videoFolder${File.separator}$cueFolderName"),
            createVideoFileName()
        )
        currentVideoFilePath = videoFile.absolutePath
        return videoFile
    }


    private fun createImageFileName(): String {
        val timestamp = fileFormat.format(Date())
        return "$photoFilePrefix${timestamp}.jpg"
    }

    private fun createImageFile(): File {
        val photoFolder = Environment.DIRECTORY_PICTURES.toString()
        return File(
            Environment.getExternalStoragePublicDirectory("$photoFolder${File.separator}$cueFolderName"),
            createImageFileName()
        )
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when (key) {
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)!!
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)!!
            CameraCharacteristics.SENSOR_ORIENTATION -> characteristics.get(key)!!
            else -> throw IllegalArgumentException("Key not recognized")
        }
    }

    private fun cameraId(lens: Int): String {
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            deviceId = cameraIdList.filter {
                lens == cameraCharacteristics(
                    it,
                    CameraCharacteristics.LENS_FACING
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
        return deviceId[0]
    }


    private fun setupMediaRecorder() {
        val rotation = this.windowManager?.defaultDisplay?.rotation
        val sensorOrientation = cameraCharacteristics(
            cameraId(CameraCharacteristics.LENS_FACING_BACK),
            CameraCharacteristics.SENSOR_ORIENTATION
        )
        when (sensorOrientation) {
            SENSOR_DEFAULT_ORINTATION_DEGREES ->
                mediaRecorder.setOrientationHint(DEFAULT_ORIENTATION.get(rotation!!))

            SENSOR_INVERSE_ORINTATION_DEGREES ->
                mediaRecorder.setOrientationHint(INVERSE_ORIENTATION.get(rotation!!))
        }
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(createVideoFile())
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(1920, 1080)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            prepare()
        }
    }

    private fun stopMediaRecorder() {
        mediaRecorder.apply {
            try {
                stop()
                reset()
            } catch (e: Exception) {
                toastMessage("Error occurred: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 100
        private val TAG = WebViewActivity::class.qualifiedName
        @JvmStatic
        fun newInstance() = WebViewActivity
        private val SENSOR_DEFAULT_ORINTATION_DEGREES = 90
        private val SENSOR_INVERSE_ORINTATION_DEGREES = 270
        private val DEFAULT_ORIENTATION = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
        private val INVERSE_ORIENTATION = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }
    }

    private fun startRecordSession() {
        try {
            recordSession()
        } catch (e: Exception) {
            toastMessage("Error occurred: ${e.localizedMessage}")
        }
    }

    private fun stopRecordSession() {
        stopMediaRecorder()
        openCamera()
    }

}

