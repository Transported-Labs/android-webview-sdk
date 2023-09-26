package com.cueaudio.webviewsdk

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.text.method.ScrollingMovementMethod
import android.text.method.TextKeyListener
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.cueaudio.engine.CUEEngine
import com.cueaudio.engine.CUEEngineError
import com.cueaudio.engine.CUEReceiverCallbackInterface
import com.cueaudio.engine.CUETrigger
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.regex.Pattern

class EngineActivity : AppCompatActivity() {
    private val TAG = "AndroidConsumer"

    private val REQUEST_RECORD_AUDIO = 13
    private val API_KEY = "TQeAwPHVnLwJrs5HEWvSmphO9D2dDeHc" //EH0GHbslb0pNWAxPf57qA6n23w4Zgu5U
    private val NOTIFICATION_ID = 1

    private var outputView: TextView? = null
    private var clearOutput: View? = null
    private var outputMode: Switch? = null
    private var sendButton: View? = null
    private var spinner: Spinner? = null
    private var messageLayout: TextInputLayout? = null
    private var messageInput: TextInputEditText? = null

    private var isShown = false

    /**
     * Used to validate the input.
     */
    private var inputMatcher: Pattern? = null
    private lateinit var hints: Array<String>
    private lateinit var regex: Array<String>
    private lateinit var errors: Array<String>

    private var restartListening = false

    private fun getModeByPosition(position: Int): Int {
        val realMode: Int = when (position) {
            0, 1 -> CUETrigger.MODE_TRIGGER
            2, 3 -> CUETrigger.MODE_MULTI_TRIGGER
            4 -> CUETrigger.MODE_LL
            else -> CUETrigger.MODE_DATA
        }
        return realMode
    }

    private fun getTriggerAsNumberByPosition(position: Int): Boolean {
        return position == 1 || position == 3
    }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine)
        checkPermission()
        messageLayout = findViewById<TextInputLayout>(R.id.message_layout)
        messageInput = findViewById<TextInputEditText>(R.id.message)
        sendButton = findViewById<View>(R.id.send)
        outputView = findViewById<TextView>(R.id.outputView)
        outputMode = findViewById<Switch>(R.id.output_mode)
        clearOutput = findViewById<View>(R.id.clear_output)
        hints = getResources().getStringArray(R.array.message_hints)
        regex = getResources().getStringArray(R.array.message_regex)
        errors = getResources().getStringArray(R.array.message_errors)
        spinner = findViewById<Spinner>(R.id.message_mode)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.message_modes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner!!.adapter = adapter
        spinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                selectMode(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        messageInput!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.toString().isNotEmpty()) {
                    validateInput(s.toString())
                }
            }
        })
        outputView!!.movementMethod = ScrollingMovementMethod()
        sendButton!!.isEnabled = false
        sendButton!!.setOnClickListener {
            val input = messageInput!!.text.toString()
            val position = spinner!!.selectedItemPosition
            val mode = getModeByPosition(position)
            val triggerAsNumber = getTriggerAsNumberByPosition(position)
            Log.v(TAG, String.format("triggerAsNumber %b", triggerAsNumber))
            queueInput(input, mode, triggerAsNumber)
        }
        clearOutput!!.setOnClickListener {
            outputView!!.setText(null)
            clearOutput!!.visibility = View.GONE
        }
    }

    protected override fun onStart() {
        super.onStart()
        isShown = true
        if (restartListening) {
            CUEEngine.getInstance().startListening()
        }
    }

    protected override fun onStop() {
        restartListening = CUEEngine.getInstance().isListening()
        CUEEngine.getInstance().stopListening()
        isShown = false
        super.onStop()
    }

    private fun selectMode(position: Int) {
        refreshKeyboard(messageInput!!)
        messageInput!!.setHint(hints[position])
//        messageLayout.setHint(null)
        inputMatcher = Pattern.compile(regex[position])
        val mode = getModeByPosition(position)
        when (mode) {
            CUETrigger.MODE_TRIGGER, CUETrigger.MODE_MULTI_TRIGGER, CUETrigger.MODE_LL -> {
                messageInput!!.inputType = InputType.TYPE_CLASS_NUMBER
                messageInput!!.setKeyListener(DigitsKeyListener.getInstance("0123456789."))
            }
            CUETrigger.MODE_DATA -> {
                messageInput!!.inputType = InputType.TYPE_CLASS_TEXT
                messageInput!!.setKeyListener(TextKeyListener.getInstance())
            }
        }
        validateInput(messageInput!!.text.toString())
    }

    private fun validateInput(input: String) {
        val matches = inputMatcher!!.matcher(input).matches()
        sendButton!!.isEnabled = matches
        if (!matches) {
            val position = spinner!!.selectedItemPosition
            // HACK: to prevent error message to be cut https://stackoverflow.com/a/55468225/322955
            messageLayout!!.error = null
            messageLayout!!.error = errors[position]
        } else {
            messageLayout!!.error = null
        }
    }

    private fun queueInput(input: String, mode: Int, triggerAsNumber: Boolean) {
        val result: Int
        when (mode) {
            CUETrigger.MODE_TRIGGER -> if (triggerAsNumber) {
                val number = input.toLong()
                result = CUEEngine.getInstance().queueTriggerAsNumber(number)
                if (result == CUEEngineError.TRIGGER_AS_NUMBER_MAX_NUMBER_EXCEEDED) {
                    messageLayout!!.error =
                        "Triggers as number can not exceed 461 for a CUEEngine g1 or 98611127 for a CUEEngine g2"
                } else if (result < 0) {
                    messageLayout!!.error = "Triggers as number sending: unknown error"
                }
            } else {
                result = CUEEngine.getInstance().queueTrigger(input)
                if (result == CUEEngineError.NUMBER_OF_SYMBOLS_MISMATCH
                    || result == CUEEngineError.INDEX_VALUE_EXCEEDED
                ) {
                    messageLayout!!.error =
                        "Triggers must be of the format [0-461] for a CUEEngine generation 1 " +
                                "or [0-461].[0-461].[0-461] for a CUEEngine generation 2"
                } else if (result < 0) {
                    messageLayout!!.error = "Triggers as number sending: unknown error"
                }
            }
            CUETrigger.MODE_MULTI_TRIGGER -> if (triggerAsNumber) {
                val number = input.toLong()
                result = CUEEngine.getInstance().queueMultiTriggerAsNumber(number)
                if (result == CUEEngineError.G1_QUEUE_MULTI_TRIGGER_UNSUPPORTED) {
                    messageLayout!!.error =
                        "Queue multi-trigger as number: unsupported for CUEEngine generation 1"
                } else if (result == CUEEngineError.MULTI_TRIGGER_AS_NUMBER_MAX_NUMBER_EXCEEDED) {
                    messageLayout!!.error =
                        "Queue multi-trigger as number can not exceed 9724154565432383"
                } else if (result < 0) {
                    messageLayout!!.error = "Queue multi-trigger as number: unknown error"
                }
            } else {
                result = CUEEngine.getInstance().queueMultiTrigger(input)
                if (result == CUEEngineError.G1_QUEUE_MULTI_TRIGGER_UNSUPPORTED) {
                    messageLayout!!.error =
                        "Queue multi-trigger: unsupported for CUEEngine generation 1"
                } else if (result < 0) {
                    messageLayout!!.error = "Queue multi-trigger: unknown error"
                }
            }
            CUETrigger.MODE_LL -> {
                result = CUEEngine.getInstance().queueLL(input)
                if (result == CUEEngineError.G1_QUEUE_LL_UNSUPPORTED) {
                    messageLayout!!.error =
                        "LL triggers sending is unsupported for engine generation 1"
                } else if (result == CUEEngineError.G2_QUEUE_LL_MODE_LL_ONLY_OR_MODE_BASIC_SHOULD_BE_SET) {
                    messageLayout!!.error =
                        "Can not queue ll: please set config mode to 'basic' or to 'll_only'"
                } else if (result == CUEEngineError.G2_LL_IS_ON_IN_BASIC_CAN_NOT_QUEUE) {
                    Toast.makeText(
                        this,
                        "LL is already on in a config 'basic' mode, can not queue, please wait while it is off",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (result < 0) {
                    messageLayout!!.error = "Queue ll: unknown error"
                }
            }
            CUETrigger.MODE_DATA -> {
                result = CUEEngine.getInstance().queueMessage(input)
                if (result == CUEEngineError.G1_QUEUE_MESSAGE_UNSUPPORTED) {
                    messageLayout!!.error =
                        "Queue message or data: unsupported for CUEEngine generation 1"
                } else if (result == CUEEngineError.G2_MESSAGE_STRING_SIZE_IN_BYTES_EXCEEDED) {
                    messageLayout!!.error = "Text can't contain more then 512 bytes for G2"
                } else if (result < 0) {
                    messageLayout!!.error = "Queue message: unknown error"
                }
            }
        }
    }

    private fun checkPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //check if permission was granted, and confirm that permission was mic access
        val permCondition =
            (requestCode == REQUEST_RECORD_AUDIO) && (grantResults.size == 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)
        // permission is not granted yet
        if (!permCondition) {
            checkPermission()
            return
        }
        CUEEngine.getInstance().setupWithAPIKey(this, API_KEY)
        CUEEngine.getInstance().setDefaultGeneration(2)
        CUEEngine.getInstance().setReceiverCallback { json ->
            val model: CUETrigger = CUETrigger.parse(json)
            runOnUiThread(
                Runnable { onTriggerHeard(model) }
            )
        }
        enableListening(true)
        val config: String = CUEEngine.getInstance().getConfig()
        Log.v(TAG, config)
        CUEEngine.getInstance().isTransmittingEnabled = true
    }

    private fun onTriggerHeard(model: CUETrigger) {
        if (!isShown) {
            showNotification(model.rawIndices)
        }
        if (outputMode!!.isChecked) {
            outputView!!.append(model.toString())
        } else {
            outputView!!.append(model.toShortString())
        }
        outputView!!.append("\n")
        outputView!!.append("\n")
        clearOutput!!.visibility = View.VISIBLE
        val triggerNum: Long = model.triggerAsNumber
        Log.i("triggerAsNumber: ", java.lang.Long.toString(triggerNum))


        // scroll to end
        // https://stackoverflow.com/a/43290961
        val editable = outputView!!.text as Editable
        Selection.setSelection(editable, editable.length)
    }

    private fun showNotification(message: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId: String = getString(R.string.notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.notification_channel_name)
            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        val intent = Intent(this, com.cueaudio.webviewsdk.EngineActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 0
        )
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(1000, 1000, 1000))
            .setAutoCancel(true)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun enableListening(enable: Boolean) {
        if (enable) {
            CUEEngine.getInstance().startListening()
        } else {
            CUEEngine.getInstance().stopListening()
        }
    }

    private fun refreshKeyboard(view: View) {
        val imm = view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        imm.showSoftInput(view, 0)
    }
}