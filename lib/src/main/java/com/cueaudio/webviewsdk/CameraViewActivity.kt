package com.cueaudio.webviewsdk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PersistableBundle
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class CameraViewActivity : AppCompatActivity() {

    lateinit var cameraManager:CameraManager
    lateinit var textureView:TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var captureRequest: CaptureRequest
    lateinit var capReq: CaptureRequest.Builder
    lateinit var button: Button
    private lateinit var  videoHandler : Handler
    private var b = true

    private var isFlashlightOn = false
    private var isSparklingOn = false
    private val videoHandlerThread : HandlerThread = HandlerThread("videoThread")
    private val torchCallback: CameraManager.TorchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isFlashlightOn = enabled
        }
    }


    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_screen_layout)

        textureView = findViewById(R.id.textureView)
        button = findViewById(R.id.cameraButton)
        button.setOnClickListener{
//            var p = cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[0])
//            var surface = Surface(textureView.surfaceTexture)
//            if(b){
//            capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
//            capReq.set(
//                CaptureRequest.CONTROL_AE_MODE,
//                CaptureRequest.CONTROL_AE_MODE_OFF
//            )
//            b = !b
//            }else{
//                capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
//                capReq.set(
//                    CaptureRequest.CONTROL_AE_MODE,
//                    CaptureRequest.CONTROL_AE_MODE_ON
//                )
//                b = !b
//            }
//
//            cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)

        sparkle(5000)
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.registerTorchCallback(torchCallback, null)
        videoHandlerThread.start()
        videoHandler = Handler((videoHandlerThread ).looper)
        isSparklingOn  = intent.extras!!.getBoolean("isSparklingOn")
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
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

                if(isSparklingOn!=null ){
                    if(isSparklingOn == true){
                        sparkle(5000)
                        isSparklingOn = false
                        return
                    }
                }
                //sparkle(5000)
            }
        }
    }

    override fun onStart() {
        super.onStart()
    //    Toast.makeText(this, intent.extras?.getBoolean("isSparklingOn").toString(), Toast.LENGTH_SHORT).show()

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraDevice.close()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {

        //cameraManager.setTorchMode(cameraManager.cameraIdList[0],true)
        cameraManager.openCamera(cameraManager.cameraIdList[0],
            @SuppressLint("MissingPermission")
            object: CameraDevice.StateCallback(){
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL)
                    var surface = Surface(textureView.surfaceTexture)
                    capReq.addTarget(surface)
                    capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    capReq.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF
                    )

                    cameraDevice.createCaptureSession(listOf(surface),object :CameraCaptureSession.StateCallback(){
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session
                            cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {

                        }
                    },videoHandler)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    TODO("Not yet implemented")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    TODO("Not yet implemented")
                }



            }, videoHandler)



    }


    private fun turnTorch(isOn: Boolean, isJavaScriptCallbackNeeded: Boolean = true) {
        val cameraId = cameraManager.cameraIdList[0]
        try {
            var surface = Surface(textureView.surfaceTexture)
            if(isOn){
                capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                capReq.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )

            }else{
                capReq.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                capReq.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )

            }

            cameraCaptureSession.setRepeatingRequest(capReq.build(), null, null)
        } catch (e: CameraAccessException) {
            print("error: " + e.localizedMessage)

        //Toast.makeText(this, "Error: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
            //errorToJavaScript("Camera access denied")
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
             //   sendToJavaScript(null)
            }, duration.toLong())
        } else {
           // errorToJavaScript("Duration: $duration is not valid value")
        }
    }
}