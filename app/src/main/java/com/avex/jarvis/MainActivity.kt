package com.avex.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var cameraManager: CameraManager
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tvStatus: TextView
    private var isTorchOn = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudio = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val camera = permissions[Manifest.permission.CAMERA] ?: false

        if (recordAudio && camera) {
            initSpeechRecognizer()
        } else {
            tvStatus.text = "Permissions Denied!"
            speak("Permissions are required for AVEX.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        tts = TextToSpeech(this, this)
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initSpeechRecognizer()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { startListening() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
            speak("AVEX online hai sir, boliye kya karna hai")
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus.text = "Processing..."
            }
            override fun onError(error: Int) {
                startListening()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val cmd = matches?.get(0)?.lowercase(Locale.getDefault()) ?: ""
                tvStatus.text = "Command: $cmd"
                handleCommand(cmd)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(cmd: String) {
        when {
            cmd.contains("torch") || cmd.contains("light") || cmd.contains("flash") -> toggleTorch()
            cmd.contains("whatsapp") -> openPackage("com.whatsapp", "WhatsApp khol raha hun")
            cmd.contains("youtube") -> openPackage("com.google.android.youtube", "YouTube khol raha hun")
            cmd.contains("camera") -> openCamera()
            cmd.contains("chrome") -> openPackage("com.android.chrome", "Chrome khol raha hun")
            else -> speak("Samajh gaya $cmd")
        }
    }

    private fun toggleTorch() {
        try {
            val camId = cameraManager.cameraIdList[0]
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(camId, isTorchOn)
            if (isTorchOn) speak("Torch on kar diya") else speak("Torch band kar diya")
        } catch (e: Exception) {
            speak("Torch switch karne me error aaya")
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            speak("Camera open kar raha hun")
            startActivity(intent)
        } else {
            speak("Camera launch nahi ho saka")
        }
    }

    private fun openPackage(pkg: String, msg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            speak(msg)
            startActivity(intent)
        } else {
            speak("App install nahi hai")
        }
    }

    private fun speak(text: String) {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AVEX_SPEECH")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AVEX_SPEECH")
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy()
    }
}
