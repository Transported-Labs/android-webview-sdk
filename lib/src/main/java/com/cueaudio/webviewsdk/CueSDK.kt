package com.cueaudio.webviewsdk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.*
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cueaudio.engine.CUEEngine
import com.cueaudio.engine.CUETrigger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.*

object PermissionConstant {
    const val ASK_MICROPHONE_REQUEST = 1001
    const val ASK_CAMERA_REQUEST = 1002
    const val ASK_SAVE_PHOTO_REQUEST = 1003
    const val ASK_MICROPHONE_TRIGGERS_REQUEST = 1004
}
class CueSDK (private val mContext: Context, private val webView: WebView) {
    private val API_KEY = "TQeAwPHVnLwJrs5HEWvSmphO9D2dDeHc" //EH0GHbslb0pNWAxPf57qA6n23w4Zgu5U

    private val torchServiceName = "torch"
    private val vibrationServiceName = "vibration"
    private val permissionsServiceName = "permissions"
    private val storageServiceName = "storage"
    private val audioTriggersServiceName = "audioTriggers"
    private val onMethodName = "on"
    private val offMethodName = "off"
    private val checkIsOnMethodName = "isOn"
    private val vibrateMethodName = "vibrate"
    private val sparkleMethodName = "sparkle"
    private val saveMediaMethodName = "saveMedia"
    private val askMicMethodName = "getMicPermission"
    private val askCamMethodName = "getCameraPermission"
    private val askSavePhotoMethodName = "getSavePhotoPermission"
    private val startListeningMethodName = "startListening"
    private val stopListeningMethodName = "stopListening"
    private val subscribeMethodName = "subscribe"
    private val testErrorMethodName = "testError"

    private var curRequestId: Int? = null
    private var isFlashlightOn = false

    private val cameraManager: CameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchCallback: TorchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isFlashlightOn = enabled
        }
    }

    init {
        cameraManager.registerTorchCallback(torchCallback, null)
        initEngine()
    }

    /// Post message from the web page
    @JavascriptInterface
    fun postMessage(message: String) {
        if (message != "") {
            val params = convertToParamsArray(message)
            processParams(params)
        }
    }

    // Using methods of camera2 API to turn torch on/off when front camera is active
    private fun turnTorch(isOn: Boolean, isJavaScriptCallbackNeeded: Boolean = true) {
        val cameraId = cameraManager.cameraIdList[0]
        try {
            cameraManager.setTorchMode(cameraId, isOn)
            if (isJavaScriptCallbackNeeded) {
                sendToJavaScript(null)
            }
        } catch (e: CameraAccessException) {
            errorToJavaScript("Camera access denied")
        }
    }

    private fun sparkle(duration: Int?) {
        if (duration != null) {
            val flashThread = Thread {
                var isOn = false
                var isSparkling = true
                val blinkDelay: Long = 50
                while (isSparkling) {
                    isOn = !isOn
                    turnTorch(isOn, false)
                    try {
                        Thread.sleep(blinkDelay)
                    } catch (e: InterruptedException) {
                        turnTorch(false, false)
                        isSparkling = false
                    }
                }
            }

            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
                sendToJavaScript(null)
            }, duration.toLong())
        } else {
            errorToJavaScript("Duration: $duration is not valid value")
        }
    }

    private fun saveMedia(data: String?, filename: String?) {
        if (data != null) {
            if (filename != null) {
                try {
                    val pictureBytes = Base64.getDecoder().decode(data)
                    val path = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DCIM
                        ), filename
                    )
                    sendToJavaScript(null)
                    path.writeBytes(pictureBytes)
                } catch (e: FileNotFoundException) {
                    e.localizedMessage?.let { errorToJavaScript(it) }
                }
            } else {
                errorToJavaScript("Filename: $filename is not valid value")
            }
        } else {
            errorToJavaScript("Data: $data is not valid value")
        }
    }

    @Suppress("DEPRECATION")
    private fun makeVibration(duration: Int?) {
        if (duration != null) {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(duration.toLong())
            sendToJavaScript(null)
        } else {
            errorToJavaScript("Duration: $duration is not valid value")
        }
    }
    private fun askForPermission(requestCode: Int) {
        var permissionType = ""
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST -> {
                permissionType = Manifest.permission.CAMERA
            }
            PermissionConstant.ASK_MICROPHONE_REQUEST,
            PermissionConstant.ASK_MICROPHONE_TRIGGERS_REQUEST -> {
                permissionType = Manifest.permission.RECORD_AUDIO
            }
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Not ask for permission for Android 11+
                    sendToJavaScript(true)
                    return
                } else {
                    permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
        }
        if (permissionType != "") {
            val permission: Int =
                ContextCompat.checkSelfPermission(mContext, permissionType)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    mContext as Activity, arrayOf<String>(permissionType), requestCode
                )
            } else {
                sendToJavaScript(true)
                if (requestCode == PermissionConstant.ASK_MICROPHONE_TRIGGERS_REQUEST) {
                    initTriggers()
                }
            }
        } else {
            errorToJavaScript("PermissionID can not be empty")
        }
    }

    fun callCurPermissionRequestGranted(granted: Boolean) {
        sendToJavaScript(granted)
    }

    private fun checkIsTorchOn() {
        sendToJavaScript(isFlashlightOn)
    }

    public fun initEngine() {
        println("Trying to init engine")
        askForPermission(PermissionConstant.ASK_MICROPHONE_TRIGGERS_REQUEST)
    }
    public fun doneTriggers() {
        println("Trying to stop triggers")
        stopListeningTriggers()
    }

    public fun initTriggers() {
        //assume that permission RECORD_AUDIO was granted
        CUEEngine.getInstance().setupWithAPIKey(mContext, API_KEY)
        CUEEngine.getInstance().setDefaultGeneration(2)
        CUEEngine.getInstance().setReceiverCallback { jsonString ->
            val model: CUETrigger = CUETrigger.parse(jsonString)
            (mContext as AppCompatActivity).runOnUiThread {
                onTriggerHeard(model)
            }
        }
//        enableListening(true)
        val config: String = CUEEngine.getInstance().config
        println("initTriggers with config: $config")
        CUEEngine.getInstance().isTransmittingEnabled = true
    }

    private fun onTriggerHeard(model: CUETrigger) {
        webView.post {
            val modelString = model.rawJson
            val js = "cueAudioTriggerCallback(JSON.stringify($modelString))"
            println("Sent to Javascript AudioTrigger: $js")
            webView.evaluateJavascript(js) { returnValue ->
                println(returnValue)
            }
        }
    }

    private fun startListeningTriggers() {
        (mContext as AppCompatActivity).runOnUiThread {
            if (!CUEEngine.getInstance().isListening) {
                CUEEngine.getInstance().startListening()
                println("Triggers are started")
            }
        }
    }

    private fun stopListeningTriggers() {
        (mContext as AppCompatActivity).runOnUiThread {
            if (CUEEngine.getInstance().isListening) {
                CUEEngine.getInstance().stopListening()
                println("Triggers are stopped")
            }
        }
    }

    private fun convertToParamsArray(source: String): JSONArray? {
        return try {
            JSONArray(source)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    private fun processParams(params: JSONArray?) {
        params?.let {
            val requestId = params[0] as? Int
            if (requestId != null) {
                curRequestId = requestId
                val serviceName = params[1] as? String
                val methodName = params[2] as? String
                if ((serviceName != null) && (methodName != null)) {
                    if (serviceName == torchServiceName) {
                        when(methodName){
                            onMethodName -> turnTorch(true)
                            offMethodName -> turnTorch(false)
                            checkIsOnMethodName -> checkIsTorchOn()
                            sparkleMethodName -> {
                                val duration = params[3] as? Int
                                sparkle(duration)
                            }
                            testErrorMethodName -> errorToJavaScript("This is the test error message")
                        }
                    } else if (serviceName == vibrationServiceName) {
                        when (methodName) {
                            vibrateMethodName ->  {
                                val duration = params[3] as? Int
                                makeVibration(duration)
                            }
                        }
                    }  else if (serviceName == storageServiceName) {
                        when (methodName) {
                            saveMediaMethodName ->  {
                                val data = params[3] as? String
                                val filename = params[4] as? String
                                saveMedia(data, filename)
                            }
                        }
                    } else if (serviceName == permissionsServiceName) {
                        when (methodName) {
                            askMicMethodName ->  {
                                askForPermission(PermissionConstant.ASK_MICROPHONE_REQUEST)
                            }
                            askCamMethodName ->  {
                                askForPermission(PermissionConstant.ASK_CAMERA_REQUEST)
                            }
                            askSavePhotoMethodName ->  {
                                askForPermission(PermissionConstant.ASK_SAVE_PHOTO_REQUEST)
                            }
                        }
                    } else if (serviceName == audioTriggersServiceName) {
                        when (methodName) {
                            startListeningMethodName ->  {
                                startListeningTriggers()
                                sendToJavaScript(null)
                            }
                            stopListeningMethodName ->  {
                                stopListeningTriggers()
                                sendToJavaScript(null)
                            }
                            subscribeMethodName ->  {
                                //subscribeTriggers()
                                sendToJavaScript(null)
                            }
                        }
                    } else {
                        errorToJavaScript("Only services '$torchServiceName', '$vibrationServiceName', '$permissionsServiceName', '$storageServiceName' are supported")
                    }
                }
            } else {
                errorToJavaScript("No correct serviceName or/and methodName were passed")
            }
        }
    }

    private fun errorToJavaScript(errorMessage: String) {
        println(errorMessage)
        sendToJavaScript(null, errorMessage)
    }

    private fun sendToJavaScript(result: Any?, errorMessage: String = "") {
        if (curRequestId != null) {
            val params = JSONArray()
            params.put(curRequestId)
            if (result != null) {
                params.put(result)
            } else if (errorMessage != "") {
                params.put(null)
                params.put(errorMessage)
            }
            val paramData = params.toString()
            webView.post {
                val js2 = "cueSDKCallback(JSON.stringify($paramData))"
                println("Sent to Javascript: $js2")
                webView.evaluateJavascript(js2) { returnValue ->
                    println(returnValue)
                }
            }
        } else {
            println("curRequestId is null")
        }
    }
}