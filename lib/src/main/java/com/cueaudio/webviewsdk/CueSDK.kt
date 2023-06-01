package com.cueaudio.webviewsdk

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONException


class CueSDK (private val mContext: Context, private val webView: WebView) {

    private val torchServiceName = "torch"
    private val onMethodName = "on"
    private val offMethodName = "off"
    private val checkIsOnMethodName = "isOn"
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
    private fun turnTorch(isOn: Boolean) {
        val cameraId = cameraManager.cameraIdList[0]
        try {
            cameraManager.setTorchMode(cameraId, isOn)
            sendToJavaScript(null)
        } catch (e: CameraAccessException) {
            errorToJavaScript("Camera access denied")
        }
    }

    private fun checkIsTorchOn() {
        sendToJavaScript(isFlashlightOn)
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
                            testErrorMethodName -> errorToJavaScript("This is the test error message")
                        }
                    } else {
                        errorToJavaScript("Only serviceName '$torchServiceName' is supported")
                    }
                }
            } else {
                errorToJavaScript("No correct serviceName or/and methodName were passsed")
            }
        }
    }

    private fun errorToJavaScript(errorMessage: String) {
        print(errorMessage)
        sendToJavaScript(null, errorMessage)
    }

    private fun sendToJavaScript(result: Any?, errorMessage: String = "") {
        webView.post(Runnable {
            doSendToJavaScript(result, errorMessage)
        })
    }

    private fun doSendToJavaScript(result: Any?, errorMessage: String = "") {
        if (curRequestId != null) {
            var params = JSONArray()
            params.put(curRequestId)
            if (result != null) {
                params.put(result)
            } else if (errorMessage != "") {
                params.put(null)
                params.put(errorMessage)
            }
            val paramData = params.toString()
            val js2 = "cueSDKCallback(JSON.stringify($paramData))"
            print("Sent to Javascript: $js2")
            webView.evaluateJavascript(js2) { returnValue ->
                    print(returnValue)
            }
        } else {
            print("curRequestId is null")
        }
    }
}