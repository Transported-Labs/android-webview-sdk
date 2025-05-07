package com.cueaudio.cuelightshow

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.camera.core.CameraControl
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.util.Base64
import java.util.Calendar
import kotlin.math.roundToInt

object PermissionConstant {
    const val ASK_MICROPHONE_REQUEST = 1001
    const val ASK_CAMERA_REQUEST = 1002
    const val ASK_SAVE_PHOTO_REQUEST = 1003
}
class CueSDK (private val mContext: Context, private val webView: WebView) {
    companion object {
        private const val CUE_SDK_NAME = "cueSDK"
        private const val torchServiceName = "torch"
        private const val vibrationServiceName = "vibration"
        private const val permissionsServiceName = "permissions"
        private const val storageServiceName = "storage"
        private const val cameraServiceName = "camera"
        private const val networkServiceName = "network"
        private const val timelineServiceName = "timeline"
        private const val onMethodName = "on"
        private const val offMethodName = "off"
        private const val openCameraMethodName = "openCamera"
        private const val openPhotoCameraMethod = "openPhotoCamera"
        private const val openVideoCameraMethod = "openVideoCamera"
        private const val checkIsOnMethodName = "isOn"
        private const val vibrateMethodName = "vibrate"
        private const val sparkleMethodName = "sparkle"
        private const val advancedSparkleMethodName = "advancedSparkle"
        private const val saveMediaMethodName = "saveMedia"
        private const val saveCacheFileName = "saveCacheFile"
        private const val getCacheFileName = "getCacheFile"
        private const val askMicMethodName = "getMicPermission"
        private const val askCamMethodName = "getCameraPermission"
        private const val askSavePhotoMethodName = "getSavePhotoPermission"
        private const val hasMicMethodName = "hasMicPermission"
        private const val hasCamMethodName = "hasCameraPermission"
        private const val hasSavePhotoMethodName = "hasSavePhotoPermission"
        private const val getStateMethodName = "getState"
        private const val testErrorMethodName = "testError"
        private const val startMethodName = "start"
        private const val stopMethodName = "stop"
    }

    private var isFlashlightOn = false
    private var networkStatus = ""

    private val cameraManager: CameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val torchCallback: TorchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isFlashlightOn = enabled
        }
    }
    var previewCameraControl: CameraControl? = null
    var onCameraShow: ((CameraLayoutType) -> Unit)? = null
    var onSwitchTimelineActive: ((Boolean) -> Unit)? = null

    init {
        webView.addJavascriptInterface(this, CUE_SDK_NAME)
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    /// Post message from the web page
    @JavascriptInterface
    fun postMessage(message: String) {
        println("Received message from Javascript: $message")
        if (message != "") {
            val params = convertToParamsArray(message)
            processParams(params)
        }
    }

    fun notifyInternetConnection(param: String) {
        networkStatus = param
        notifyJavaScript("network-state", networkStatus)
    }

    fun notifyTimelineBreak() {
        notifyJavaScript("timeline", "break")
    }

    private fun switchTimelineActive(requestId: Int, newState: Boolean) {
        onSwitchTimelineActive?.invoke(newState)
        sendToJavaScript(requestId,true)
    }

    private fun checkNetworkState(requestId: Int) {
        sendToJavaScript(requestId, networkStatus)
    }

    private fun turnTorchToLevel(requestId: Int, level: Float, isJavaScriptCallbackNeeded: Boolean = true) {
        //  Currently there is no way to control the strength while the CameraX is opened
        val cameraControl = this.previewCameraControl
        if (cameraControl != null) {
            cameraControl.enableTorch(true)
            if (isJavaScriptCallbackNeeded) {
                sendToJavaScript(requestId, null)
            }
        } else {
            try {
                val cameraId = cameraManager.cameraIdList[0]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val supportedMaxLevel =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                    // Check if camera supports Torch Strength Control
                    if ((supportedMaxLevel != null) && (supportedMaxLevel > 1)) {
                        val strengthLevel = (supportedMaxLevel * level).roundToInt()
                        if (strengthLevel > 0) {
                            cameraManager.turnOnTorchWithStrengthLevel(cameraId, strengthLevel)
                        }
                        if (isJavaScriptCallbackNeeded) {
                            sendToJavaScript(requestId, null)
                        }
                    } else {
                        //Simply turn torch on
                        turnTorch(requestId, true, isJavaScriptCallbackNeeded)
                    }
                } else {
                    //Simply turn torch on
                    turnTorch(requestId, true, isJavaScriptCallbackNeeded)
                }
            } catch (e: CameraAccessException) {
                errorToJavaScript(requestId, "Method turnTorchToLevel - Camera access denied: " + e.localizedMessage)
            }
        }
    }

    // Using methods of CameraX/Camera2 API to turn torch on/off when front camera is active
    private fun turnTorch(requestId: Int, isOn: Boolean, isJavaScriptCallbackNeeded: Boolean = true) {
        val cameraControl = this.previewCameraControl
        if (cameraControl != null) {
            cameraControl.enableTorch(isOn)
            if (isJavaScriptCallbackNeeded) {
                sendToJavaScript(requestId, null)
            }
        } else {
            try {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, isOn)
                if (isJavaScriptCallbackNeeded) {
                    sendToJavaScript(requestId, null)
                }
            } catch (e: CameraAccessException) {
                errorToJavaScript(requestId, "Method turnTorch - Camera access denied")
            }
        }
    }

    private fun adjustedIntenseLevel(level: Float): Float {
        val minLevel = 0.001F
        val maxLevel = 1.0F
        return if (level < minLevel) minLevel else (if (level > maxLevel) maxLevel else level)
    }

    private fun debugMessageToJS(requestId: Int, message: String) {
        // Is used for debug purposes
//        sendToJavaScript(requestId, null, message)
        println("debugMessageToJS, requestId: $requestId, message: $message")
    }

    private fun nowMs(): Long {
        return Calendar.getInstance().timeInMillis
    }
    private fun advancedSparkle(requestId: Int, rampUpMs: Int?, sustainMs: Int?, rampDownMs: Int?, intensity: Double?) {
        val blinkDelayMs: Long = 50
        if ((rampUpMs != null) && (sustainMs != null) && (rampDownMs != null) && (intensity != null)) {
            val totalDuration = rampUpMs + sustainMs + rampDownMs
            val intenseLevel = adjustedIntenseLevel(intensity.toFloat())
            val flashThread = Thread {
                try {
                    val rampUpStart = nowMs()
                    var currentRampUpTime: Long = 0
                    while (currentRampUpTime < rampUpMs) {
                        val upIntensity: Float = (currentRampUpTime.toFloat() / rampUpMs.toFloat()) * intenseLevel
                        debugMessageToJS(requestId, "rampUp: $upIntensity")
                        turnTorchToLevel(requestId, adjustedIntenseLevel(upIntensity), false)
                        Thread.sleep(blinkDelayMs)
                        currentRampUpTime = nowMs() - rampUpStart
                    }
                    if (sustainMs > 0) {
                        debugMessageToJS(requestId, "sustain: $intenseLevel")
                        turnTorchToLevel(requestId, adjustedIntenseLevel(intenseLevel), false)
                        Thread.sleep(sustainMs.toLong())
                    }
                    val rampDownStart = nowMs()
                    var currentRampDownTime: Long = 0
                    while (currentRampDownTime < rampDownMs){
                        val downIntensity = (1.0 - currentRampDownTime.toFloat() / rampDownMs.toFloat()) * intenseLevel
                        debugMessageToJS(requestId, "rampDown: $downIntensity")
                        turnTorchToLevel(requestId, adjustedIntenseLevel(downIntensity.toFloat()), false)
                        Thread.sleep(blinkDelayMs)
                        currentRampDownTime = nowMs() - rampDownStart
                    }
                } catch (e: InterruptedException) {
                    debugMessageToJS(requestId, "interrupted by time: $totalDuration ms")
                }
                debugMessageToJS(requestId, "turned off inside")
                turnTorch(requestId, false, false)
                sendToJavaScript(requestId, null)
            }
            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
            }, totalDuration.toLong())
        } else {
            errorToJavaScript(requestId, "Cannot be null rampUpMs: $rampUpMs, sustainMs: $sustainMs, rampDownMs: $rampDownMs, intensity: $intensity")
        }
    }

    private fun sparkle(requestId: Int, duration: Int?) {
        if (duration != null) {
            val flashThread = Thread {
                var isOn = false
                var isSparkling = true
                val blinkDelay: Long = 50
                while (isSparkling) {
                    isOn = !isOn
                    turnTorch(requestId, isOn, false)
                    try {
                        Thread.sleep(blinkDelay)
                    } catch (e: InterruptedException) {
                        turnTorch(requestId, false, false)
                        isSparkling = false
                    }
                }
            }

            flashThread.start()
            Handler(Looper.getMainLooper()).postDelayed({
                flashThread.interrupt()
                sendToJavaScript(requestId, null)
            }, duration.toLong())
        } else {
            errorToJavaScript(requestId, "Duration: $duration is not valid value")
        }
    }

    private fun saveMedia(requestId: Int, data: String?, filename: String?) {
        if (data != null) {
            if (filename != null) {
                try {
                    val pictureBytes = Base64.getDecoder().decode(data)
                    val path = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DCIM
                        ), filename
                    )
                    sendToJavaScript(requestId, null)
                    path.writeBytes(pictureBytes)
                } catch (e: FileNotFoundException) {
                    e.localizedMessage?.let { errorToJavaScript(requestId, it) }
                }
            } else {
                errorToJavaScript(requestId, "Filename is null")
            }
        } else {
            errorToJavaScript(requestId, "Data are null")
        }
    }
    private fun saveCacheFile(requestId: Int, filename: String?, data: String?) {
        if (data != null) {
            if (filename != null) {
                val logMessage = IoUtils.saveMediaToCacheFile(mContext, filename, data, true)
                println(logMessage)
                if (logMessage.contains("Error")) {
                    errorToJavaScript(requestId, "$logMessage, file: $filename")
                } else {
                    sendToJavaScript(requestId, null)
                }
            } else {
                errorToJavaScript(requestId, "Filename is null")
            }
        } else {
            errorToJavaScript(requestId, "Data are null")
        }
    }

    private fun sendCacheFileToJavascript(requestId: Int, filename: String?) {
        if (filename != null) {
            val (inputStream, logMessage) = IoUtils.loadMediaFromCacheFile(mContext, filename)
            println(logMessage)
            if (inputStream != null) {
                val inputAsString = inputStream.bufferedReader().use { it.readText() }  // defaults to UTF-8
                sendToJavaScript(requestId, inputAsString)
            } else {
                errorToJavaScript(requestId, "Error with file $filename: $logMessage")
            }
        } else {
            errorToJavaScript(requestId, "Filename is null")
        }
    }

    @Suppress("DEPRECATION")
    private fun makeVibration(requestId: Int, duration: Int?) {
        if (duration != null) {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(duration.toLong())
            sendToJavaScript(requestId, null)
        } else {
            errorToJavaScript(requestId, "Duration: $duration is not valid value")
        }
    }
    private fun hasPermission(requestId: Int, requestCode: Int) {
        var permissionType = ""
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST -> {
                permissionType = Manifest.permission.CAMERA
            }
            PermissionConstant.ASK_MICROPHONE_REQUEST -> {
                permissionType = Manifest.permission.RECORD_AUDIO
            }
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Not ask for permission for Android 11+
                    sendToJavaScript(requestId, true)
                    return
                } else {
                    permissionType = Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
        }
        if (permissionType != "") {
            val permission: Int =
                ContextCompat.checkSelfPermission(mContext, permissionType)
            val result = (permission == PackageManager.PERMISSION_GRANTED)
            sendToJavaScript(requestId, result)
        } else {
            errorToJavaScript(requestId, "PermissionID can not be empty")
        }
    }

    private fun askForPermission(requestId: Int, requestCode: Int) {
        var permissionType = ""
        when (requestCode) {
            PermissionConstant.ASK_CAMERA_REQUEST -> {
                permissionType = Manifest.permission.CAMERA
            }
            PermissionConstant.ASK_MICROPHONE_REQUEST -> {
                permissionType = Manifest.permission.RECORD_AUDIO
            }
            PermissionConstant.ASK_SAVE_PHOTO_REQUEST -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    // Not ask for permission for Android 11+
                    sendToJavaScript(requestId, true)
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
                    mContext as Activity, arrayOf<String>(permissionType), requestId
                )
            } else {
                sendToJavaScript(requestId, true)
            }
        } else {
            errorToJavaScript(requestId, "PermissionID can not be empty")
        }
    }

    fun callCurPermissionRequestGranted(requestId: Int, granted: Boolean) {
        sendToJavaScript(requestId, granted)
    }

    private fun checkIsTorchOn(requestId: Int) {
        sendToJavaScript(requestId, isFlashlightOn)
    }

    private fun convertToParamsArray(source: String): JSONArray? {
        return try {
            JSONArray(source)
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    // Params like 1.00 come from JSON.stringify as Int and give null as? Double
    private fun getAsDouble(param: Any?): Double? {
        var result = param as? Double
        if (result == null) {
            result = (param as? Int)?.toDouble()
        }
        return result
    }

    private fun processParams(params: JSONArray?) {
        params?.let {
            val requestId = params[0] as? Int
            if (requestId != null) {
                val serviceName = params[1] as? String
                val methodName = params[2] as? String
                if ((serviceName != null) && (methodName != null)) {
                    when (serviceName) {
                        torchServiceName -> handleTorchService(requestId, methodName, params)
                        vibrationServiceName -> handleVibrationService(requestId, methodName, params)
                        storageServiceName -> handleStorageService(requestId, methodName, params)
                        permissionsServiceName -> handlePermissionsService(requestId, methodName)
                        cameraServiceName -> handleCameraService(requestId, methodName)
                        networkServiceName -> handleNetworkService(requestId, methodName)
                        timelineServiceName -> handleTimelineService(requestId, methodName)
                        else -> errorToJavaScript(requestId, "Unsupported service: '$serviceName'")
                    }
                } else {
                    errorToJavaScript(requestId, "No correct serviceName or/and methodName were passed")
                }
            }  else {
                println("No correct requestId was passed")
            }
        }
    }

    private fun handleTorchService(requestId: Int, methodName: String, params: JSONArray) {
        when(methodName){
            onMethodName -> {
                if (params.length() > 3) {
                    val level = params[3] as? Double
                    if (level != null) {
                        turnTorchToLevel(requestId, level.toFloat())
                    } else {
                        val levelInt = params[3] as? Int
                        if (levelInt != null) {
                            turnTorchToLevel(requestId, levelInt.toFloat())
                        } else {
                            errorToJavaScript(requestId, "Level cannot be null")
                        }
                    }
                } else {
                    turnTorch(requestId, true)
                }
            }
            offMethodName -> turnTorch(requestId, false)
            checkIsOnMethodName -> checkIsTorchOn(requestId)
            sparkleMethodName -> {
                val duration = params[3] as? Int
                sparkle(requestId, duration)
            }
            advancedSparkleMethodName -> {
                if (params.length() > 6) {
                    val rampUpMs = params[3] as? Int
                    val sustainMs = params[4] as? Int
                    val rampDownMs = params[5] as? Int
                    val intensity = getAsDouble(params[6])
                    advancedSparkle(requestId, rampUpMs, sustainMs, rampDownMs, intensity)
                } else {
                    errorToJavaScript(requestId, "Needed more params for advancedSparkle: rampUpMs: Int, sustainMs: Int, rampDownMs: Int, intensity: Float")
                }
            }
            testErrorMethodName -> errorToJavaScript(requestId, "This is the test error message")
        }
    }

    private fun handleVibrationService(requestId: Int, methodName: String, params: JSONArray) {
        when (methodName) {
            vibrateMethodName ->  {
                val duration = params[3] as? Int
                makeVibration(requestId, duration)
            }
        }
    }

    private fun handleStorageService(requestId: Int, methodName: String, params: JSONArray) {
        when (methodName) {
            saveMediaMethodName ->  {
                val data = params[3] as? String
                val filename = params[4] as? String
                saveMedia(requestId, data, filename)
            }
            saveCacheFileName ->  {
                val filename = params[3] as? String
                val data = params[4] as? String
                saveCacheFile(requestId, filename, data)
            }
            getCacheFileName ->  {
                val filename = params[3] as? String
                sendCacheFileToJavascript(requestId, filename)
            }
        }
    }

    private fun handlePermissionsService(requestId: Int, methodName: String) {
        when (methodName) {
            askMicMethodName ->  {
                askForPermission(requestId, PermissionConstant.ASK_MICROPHONE_REQUEST)
            }
            askCamMethodName ->  {
                askForPermission(requestId, PermissionConstant.ASK_CAMERA_REQUEST)
            }
            askSavePhotoMethodName ->  {
                askForPermission(requestId, PermissionConstant.ASK_SAVE_PHOTO_REQUEST)
            }
            hasMicMethodName ->  {
                hasPermission(requestId, PermissionConstant.ASK_MICROPHONE_REQUEST)
            }
            hasCamMethodName ->  {
                hasPermission(requestId, PermissionConstant.ASK_CAMERA_REQUEST)
            }
            hasSavePhotoMethodName ->  {
                hasPermission(requestId, PermissionConstant.ASK_SAVE_PHOTO_REQUEST)
            }
        }
    }

    private fun handleCameraService(requestId: Int, methodName: String) {
        when (methodName) {
            openCameraMethodName ->  {
                openCamera(requestId, CameraLayoutType.BOTH)
            }
            openPhotoCameraMethod ->  {
                openCamera(requestId, CameraLayoutType.PHOTO_ONLY)
            }
            openVideoCameraMethod ->  {
                openCamera(requestId, CameraLayoutType.VIDEO_ONLY)
            }
        }
    }

    private fun handleNetworkService(requestId: Int, methodName: String) {
        when (methodName) {
            getStateMethodName ->  {
                checkNetworkState(requestId)
            }
        }
    }

    private fun handleTimelineService(requestId: Int, methodName: String) {
        when (methodName) {
            startMethodName ->  {
                switchTimelineActive(requestId, true)
            }
            stopMethodName ->  {
                switchTimelineActive(requestId, false)
            }
        }
    }

    private fun openCamera(requestId: Int, cameraLayoutType : CameraLayoutType) {
        onCameraShow?.invoke(cameraLayoutType)
        sendToJavaScript(requestId, null)
    }

    private fun errorToJavaScript(requestId: Int, errorMessage: String) {
        println(errorMessage)
        sendToJavaScript(requestId, null, errorMessage)
    }

    private fun sendToJavaScript(requestId: Int, result: Any?, errorMessage: String = "") {
        val params = JSONArray()
        params.put(requestId)
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
    }
    private fun notifyJavaScript(channel:String, result: Any?, errorMessage: String = "") {
        val params = JSONArray()
        params.put(channel)
        if (result != null) {
            params.put(result)
        } else if (errorMessage != "") {
            params.put(null)
            params.put(errorMessage)
        }
        val paramData = params.toString()
        webView.post {
            val js2 = "cueSDKNotification(JSON.stringify($paramData))"
            println("Sent Notification to Javascript: $js2")
            webView.evaluateJavascript(js2) { returnValue ->
                println(returnValue)
            }
        }
    }
}