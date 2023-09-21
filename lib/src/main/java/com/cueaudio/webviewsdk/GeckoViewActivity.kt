package com.cueaudio.webviewsdk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.*
import org.mozilla.geckoview.BuildConfig
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback
import org.mozilla.geckoview.WebExtension.PortDelegate


class GeckoViewActivity : Activity() {
    companion object {
        val TAG: String = GeckoViewActivity::class.java.simpleName
        var geckoRuntime: GeckoRuntime? = null
    }

    private lateinit var geckoView: GeckoView
    private lateinit var cueSDK: CueSDK
    private val geckoSession = GeckoSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.lib_gecko)
//        vb.button.setOnClickListener {
//            appWebExtensionPortDelegate.port?.postMessage(JSONObject(mapOf("text" to "Hello Web!")))
//        }

        var runtime = geckoRuntime
        if (runtime == null) {
            val builder = GeckoRuntimeSettings.Builder()
            if (BuildConfig.DEBUG) {
                builder.remoteDebuggingEnabled(true)
                builder.consoleOutput(true)
            }
            runtime = GeckoRuntime.create(this, builder.build())
            geckoRuntime = runtime
        }

        geckoSession.open(runtime)
        geckoView = findViewById<GeckoView>(R.id.geckoView)
        geckoView.setSession(geckoSession)

        val context = this;
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/messaging/", "gecko@cuelive.com")
            .accept(object : GeckoResult.Consumer<WebExtension> {
                @SuppressLint("WrongThread")
                override fun accept(extension: WebExtension?) {
                    Log.d(TAG, "extension accepted: ${extension!!.metaData.description}")
                    geckoSession.webExtensionController.setMessageDelegate(extension,
                        object : WebExtension.MessageDelegate {
                            override fun onMessage(nativeApp: String, message: Any,
                                                   sender: WebExtension.MessageSender): GeckoResult<Any>? {
                                Log.d(TAG, "onMessage: $nativeApp, $message, $sender")
                                return null
                            }

                            override fun onConnect(port: WebExtension.Port) {
                                Log.d(TAG, "onConnect: $port")
                                port.setDelegate(appWebExtensionPortDelegate)
                                appWebExtensionPortDelegate.port = port
                                cueSDK = CueSDK(context, null, port)
                            }
                        }, "browser")
                    geckoSession.permissionDelegate = object : PermissionDelegate {
                        override fun onAndroidPermissionsRequest(
                            session: GeckoSession,
                            permissions: Array<String>?,
                            callbackParam: PermissionDelegate.Callback
                        ) {
                            var callback = callbackParam
                            if (ContextCompat.checkSelfPermission(
                                    this@GeckoViewActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                Log.i(TAG, "Android Permission Granted: RECORD_AUDIO")
                                callback.grant()
                            }
                            if (ContextCompat.checkSelfPermission(
                                    this@GeckoViewActivity,
                                    Manifest.permission.MODIFY_AUDIO_SETTINGS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                Log.i(TAG, "Android Permission Granted: MODIFY_AUDIO_SETTINGS")
                                callback.grant()
                            }
                            if (ContextCompat.checkSelfPermission(
                                    this@GeckoViewActivity,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                Log.i(TAG, "Android Permission Granted: MODIFY_AUDIO_SETTINGS")
                                callback.grant()
                            }
                        }
                        override fun onContentPermissionRequest(
                            session: GeckoSession,
                            perm: GeckoSession.PermissionDelegate.ContentPermission
                        ): GeckoResult<Int>? {
                            Log.i(TAG, "Content Permission Needed")
                            return super.onContentPermissionRequest(session, perm)
                        }
                        override fun onMediaPermissionRequest(
                            session: GeckoSession,
                            uri: String,
                            video: Array<PermissionDelegate.MediaSource>?,
                            audio: Array<PermissionDelegate.MediaSource>?,
                            callback: MediaCallback
                        ) {
                            Log.i(TAG, "Media Permission Needed: $uri")
                            if (video != null) {
                                for (v in video) {
                                    Log.i(TAG, "Granted for video: $v")
                                    callback.grant(v,null)
                                }
                            }
                            if (audio != null) {
                                for (a in audio) {
                                    Log.i(TAG, "Granted for audio: $a")
                                    callback.grant(null, a)
                                }
                            }
                            return super.onMediaPermissionRequest(session, uri, video, audio, callback)
                        }
                    }
                }
            }
            ) { e -> Log.e(TAG, "Error registering WebExtension", e) }

        val url = intent.getStringExtra("url")
        if (url != null) {
            geckoSession.loadUri(url)
        }
    }

    private val appWebExtensionPortDelegate = object : PortDelegate {
        var port: WebExtension.Port? = null
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
//            Toast.makeText(this@GeckoViewActivity, message.toString(), Toast.LENGTH_SHORT).show()
            cueSDK.postMessage(message as String)
        }

        override fun onDisconnect(port: WebExtension.Port) {
            Log.d(TAG, "onDisconnect: $port")
            this.port = null
        }
    }
}