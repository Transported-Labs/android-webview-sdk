package com.cueaudio.cuelightshow

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors


class CameraPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CameraPreview"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss"
        private const val PHOTO_FILE_PREFIX = "cue-"
        private const val VIDEO_FILE_PREFIX = "video-"
        private const val CUE_FOLDER_NAME = "CUE Live"

    }
    private lateinit var cameraTextureView: PreviewView
    private lateinit var cameraLayout: View
    private lateinit var closeButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var imageButton: ImageButton
    // Property to hold the custom handler
    var onCameraActivated: ((CameraControl) -> Unit)? = null

    private var curCameraLayoutType = CameraLayoutType.BOTH
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider

    // Property to hold reference to the activity
    private var activity: Activity? = null
    private var lifecycleOwner: LifecycleOwner? = null

    init {
        if (context is Activity) {
            activity = context // Assign the calling activity to the property
        }
        if (context is LifecycleOwner) {
            lifecycleOwner = context
        }
        // Inflate the custom layout into this ConstraintLayout
        LayoutInflater.from(context).inflate(R.layout.camera_preview_layout, this, true)

        // Additional initialization logic can go here
        cameraTextureView = findViewById(R.id.cameraTextureView)
        cameraLayout = findViewById(R.id.cameraLayout)
        closeButton = findViewById(R.id.closeButton)
        videoButton = findViewById(R.id.videoButton)
        imageButton = findViewById(R.id.imageButton)

        cameraLayout.visibility = View.GONE
        closeButton.setOnClickListener {
            activity?.runOnUiThread {
                cameraLayout.visibility = View.GONE
            }
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        videoButton.setOnClickListener { captureVideo() }
        imageButton.setOnClickListener { takePhoto() }

    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cameraExecutor.shutdown()
    }

    private fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkAndRequestPermissions(context: Context, onPermissionNeeded: () -> Unit) {
        if (!hasCameraPermission(context)) {
            onPermissionNeeded()
        }
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

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
                val camera = lifecycleOwner?.let {
                    cameraProvider.bindToLifecycle(
                        it, cameraSelector, preview, imageCapture, videoCapture)
                }
                camera?.apply {
                    if (cameraInfo.hasFlashUnit()) {
                        onCameraActivated?.invoke(cameraControl)
                    }
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(cameraLayoutType : CameraLayoutType) {
        curCameraLayoutType = cameraLayoutType
        if ((imageCapture == null) || (videoCapture == null)) {
            initCamera()
        }
        activity?.runOnUiThread {
            cameraLayout.visibility = View.VISIBLE
            when(curCameraLayoutType){
                CameraLayoutType.BOTH ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.VISIBLE
                    imageButton.updateLayoutParams<LayoutParams> {
                        endToEnd = LayoutParams.UNSET
                    }
                }
                CameraLayoutType.VIDEO_ONLY ->{
                    videoButton.visibility = View.VISIBLE
                    imageButton.visibility = View.GONE
                }
                CameraLayoutType.PHOTO_ONLY ->{
                    videoButton.visibility = View.GONE
                    imageButton.visibility = View.VISIBLE
                    imageButton.updateLayoutParams<LayoutParams> {
                        endToEnd = LayoutParams.PARENT_ID
                    }
                }
            }
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = PHOTO_FILE_PREFIX + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${CUE_FOLDER_NAME}")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    playSound(MediaActionSound.SHUTTER_CLICK)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
        val name = VIDEO_FILE_PREFIX + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/${CUE_FOLDER_NAME}")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(context,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        playSound(MediaActionSound.START_VIDEO_RECORDING)
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
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        playSound(MediaActionSound.STOP_VIDEO_RECORDING)
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


    // Function to release the camera when done
    fun releaseCamera() {
        try {
            cameraProvider.unbindAll()
            Log.d(TAG, "Camera released successfully")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to release camera", exc)
        }
    }

}
