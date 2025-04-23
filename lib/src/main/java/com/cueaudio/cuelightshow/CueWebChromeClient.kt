package com.cueaudio.cuelightshow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/// Features:
/// 1. Asks app for Permission being requested from WebView
/// 2. Shows standard Camera dialog being asked for File to choose
open class CueWebChromeClient(
    private val context: Context,
    private val activityResultLauncher: ActivityResultLauncher<Intent>
) : WebChromeClient() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraFilePath: String? = null
    private var activity: Activity? = null

    init {
        if (context is Activity) {
            activity = context
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        activity?.runOnUiThread {
            request.grant(request.resources)
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        this.filePathCallback = filePathCallback

        // Create an intent to open the camera
        val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            // Create a file to store the image
            val photoFile: File? = try {
                createImageFile()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    it
                )
                cameraFilePath = it.absolutePath
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI)
            }

            // Launch the camera intent directly
            activityResultLauncher.launch(takePictureIntent)
        }
        return true
    }

    private fun createImageFile(): File {
        // Create a unique image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")

        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val results: Array<Uri>? = cameraFilePath?.let { path ->
                val file = File(path)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                arrayOf(uri)
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        } else {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }
}
