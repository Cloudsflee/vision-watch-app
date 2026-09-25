package com.example.visionwatchapp

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import com.iflytek.sparkchain.core.tts.OnlineTTS
import com.iflytek.sparkchain.core.tts.TTSCallbacks
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "VisionWatchApp"
    }

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var voiceButton: Button
    private lateinit var submitButton: Button
    private lateinit var backButton: Button
    private lateinit var statusText: TextView
    private lateinit var voiceText: TextView
    private lateinit var resultText: TextView
    private lateinit var voiceAnimation: View

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var capturedImageFile: File? = null
    private var voiceInputText: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())

    private var asr: ASR? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var tts: OnlineTTS? = null
    private var ttsAudioTrack: AudioTrack? = null
    private var ttsTrackReleaseRunnable: Runnable? = null
    private var sparkReady = false
    private var isAwaitingAsrFinal = false
    private var asrFinalizeTimeoutRunnable: Runnable? = null
    private var asrRecognizingSinceMs: Long = 0L
    private var isVoiceInputActive = false
    private var isSubmitting = false
    private var activeModelCall: Call? = null
    private var submittingTimeoutRunnable: Runnable? = null
    private var submitStartAtMs: Long = 0L
    private var submitRequestId: String = ""
    private var submitStage: String = "idle"
    private var pipelineStage: String = "idle"
    private var asrStartAtMs: Long = 0L
    private var asrLastText: String = ""
    private var asrLastError: String = ""
    private var asrBytesSent: Long = 0L
    private var asrReadErrors: Int = 0
    private var asrLastReadCode: Int = 0
    private var asrAudioSourceUsed: Int = MediaRecorder.AudioSource.MIC
    private var asrPeakAmplitude: Int = 0
    private var asrNonZeroFrames: Long = 0L
    private var modelHttpCode: Int = -1
    private var modelElapsedMs: Long = 0L
    private var modelResponsePreview: String = ""
    private var ttsStartAtMs: Long = 0L
    private var ttsElapsedMs: Long = 0L
    private var ttsLastError: String = ""
    private var ttsTextLength: Int = 0
    private var selectedButtonIndex = 0
    private val visibleButtons = mutableListOf<Button>()

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resetDiagnostics()
        initViews()
        initSparkChain()
        checkPermissions()
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        voiceButton = findViewById(R.id.voiceButton)
        submitButton = findViewById(R.id.submitButton)
        backButton = findViewById(R.id.backButton)
        statusText = findViewById(R.id.statusText)
        voiceText = findViewById(R.id.voiceText)
        resultText = findViewById(R.id.resultText)
        voiceAnimation = findViewById(R.id.voiceAnimation)

        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener {
            captureImage()
        }

        voiceButton.setOnClickListener {
            startVoiceInput()
        }

        submitButton.setOnClickListener {
            submitToModel()
        }

        backButton.setOnClickListener {
            resetApp()
        }

        updateVisibleButtons()
    }

    private fun setPipelineStage(stage: String, uiText: String? = null) {
        pipelineStage = stage
        if (uiText != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                statusText.text = uiText
            } else {
                handler.post { statusText.text = uiText }
            }
        }
        Log.i(TAG, "pipeline_stage=$pipelineStage")
    }

    private fun resetDiagnostics() {
        pipelineStage = "idle"
        asrStartAtMs = 0L
        asrLastText = ""
        asrLastError = ""
        asrBytesSent = 0L
        asrReadErrors = 0
        asrLastReadCode = 0
        asrAudioSourceUsed = MediaRecorder.AudioSource.MIC
        asrPeakAmplitude = 0
        asrNonZeroFrames = 0L
        modelHttpCode = -1
        modelElapsedMs = 0L
        modelResponsePreview = ""
        ttsStartAtMs = 0L
        ttsElapsedMs = 0L
        ttsLastError = ""
        ttsTextLength = 0
    }

    private fun initSparkChain() {
        val workDir = externalCacheDir?.absolutePath ?: filesDir.absolutePath
        val config = SparkChainConfig.builder()
            .appID(ApiConfig.XFYUN_APPID)
            .apiKey(ApiConfig.XFYUN_API_KEY)
            .apiSecret(ApiConfig.XFYUN_API_SECRET)
            .workDir(workDir)
            .logLevel(2)
        
        val ret = SparkChain.getInst().init(applicationContext, config)
        sparkReady = ret == 0
        if (ret != 0) {
            Toast.makeText(this, "SDK init failed: $ret", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Spark init failed ret=$ret")
        } else {
            Log.i(TAG, "Spark init success")
        }
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                previewView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "startCamera failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImageFile = photoFile
                    handler.post {
                        setPipelineStage("image_captured")
                        captureButton.visibility = View.GONE
                        voiceButton.visibility = View.VISIBLE
                        statusText.text = "Press voice button to start recording"
                        updateVisibleButtons()
                        selectedButtonIndex = 0
                        updateButtonSelection()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    handler.post {
                        Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun startVoiceInput() {
        if (isAwaitingAsrFinal) {
            Toast.makeText(this, "Still recognizing voice, please wait", Toast.LENGTH_SHORT).show()
            setPipelineStage("asr_wait_final")
            return
        }
        if (!isVoiceInputActive) {
            setPipelineStage("asr_start")
            isVoiceInputActive = true
            voiceButton.isEnabled = true
            voiceAnimation.visibility = View.VISIBLE
            startVoiceAnimation()
            voiceInputText = ""
            asrLastText = ""
            asrLastError = ""
            asrStartAtMs = System.currentTimeMillis()
            asrRecognizingSinceMs = 0L
            voiceButton.text = "Stop Voice"
            setPipelineStage("asr_recording", "Recording...")

            asr = ASR()
            asr?.language("zh_cn")
            asr?.domain("iat")
            asr?.accent("mandarin")

            val callbacks = object : AsrCallbacks {
                override fun onResult(result: ASR.ASRResult, usrTag: Any?) {
                    val text = result.getBestMatchText()
                    val status = result.getStatus()
                    
                    handler.post {
                        if (text.isNotEmpty() && text.length >= voiceInputText.length) {
                            voiceInputText = text
                            asrLastText = text
                        }
                        
                        if (status == 2) {
                            setPipelineStage("asr_final_received")
                            cancelAsrFinalizeTimeout()
                            isAwaitingAsrFinal = false
                            finishVoiceInput()
                        }
                    }
                }

                override fun onError(error: ASR.ASRError, usrTag: Any?) {
                    handler.post {
                        cancelAsrFinalizeTimeout()
                        isAwaitingAsrFinal = false
                        asrLastError = error.getErrMsg()
                        setPipelineStage("asr_error", "ASR error")
                        stopVoiceAnimation()
                        voiceAnimation.visibility = View.GONE
                        voiceButton.text = "Voice Input"
                        voiceButton.isEnabled = true
                        isVoiceInputActive = false
                        Toast.makeText(this@MainActivity, "ASR error: ${error.getErrMsg()}", Toast.LENGTH_SHORT).show()
                        stopAudioRecording()
                    }
                }
            }

            asr?.registerCallbacks(callbacks)
            asr?.start("voice_input")
            
            startAudioRecording()
        } else {
            stopVoiceInput()
        }
    }

    private fun stopVoiceInput() {
        setPipelineStage("asr_stop_requested")
        isVoiceInputActive = false
        isAwaitingAsrFinal = true
        asrRecognizingSinceMs = System.currentTimeMillis()
        stopAudioRecording()
        // Revert to the previously working stop mode.
        asr?.stop(false)
        
        stopVoiceAnimation()
        voiceAnimation.visibility = View.GONE
        voiceButton.text = "Recognizing..."
        voiceButton.isEnabled = false
        setPipelineStage("asr_recognizing", "Recognizing voice...")
        startAsrFinalizeTimeout()
    }

    private fun finishVoiceInput() {
        setPipelineStage("asr_finished")
        isVoiceInputActive = false
        isAwaitingAsrFinal = false
        cancelAsrFinalizeTimeout()
        stopAudioRecording()
        
        stopVoiceAnimation()
        voiceAnimation.visibility = View.GONE
        voiceButton.visibility = View.GONE
        voiceButton.isEnabled = true
        
        if (voiceInputText.isNotEmpty()) {
            voiceText.visibility = View.VISIBLE
            voiceText.text = voiceInputText
            submitButton.visibility = View.VISIBLE
            backButton.visibility = View.VISIBLE
            setPipelineStage("asr_ready_for_submit", "Press submit to send")
            updateVisibleButtons()
            selectedButtonIndex = 0
            updateButtonSelection()
        } else {
            voiceButton.visibility = View.VISIBLE
            voiceButton.text = "Voice"
            voiceButton.isEnabled = true
            setPipelineStage("asr_empty_result", "Press voice button to start recording")
            updateVisibleButtons()
            selectedButtonIndex = 0
            updateButtonSelection()
        }
    }

    private fun startAudioRecording() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        asrAudioSourceUsed = MediaRecorder.AudioSource.MIC

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            asrLastError = "audio_record_init_failed"
            setPipelineStage("asr_audio_init_failed", "Audio init failed")
            Toast.makeText(this, "Audio init failed", Toast.LENGTH_SHORT).show()
            showDetailedFailure("ASR audio init failed")
            return
        }

        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            asrLastError = "audio_record_start_failed"
            setPipelineStage("asr_audio_start_failed", "Audio start failed")
            Toast.makeText(this, "Audio start failed", Toast.LENGTH_SHORT).show()
            showDetailedFailure("ASR audio start failed")
            return
        }

        isRecording = true

        Thread {
            val buffer = ByteArray(1280)
            var frameCount = 0
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    frameCount += 1
                    asrBytesSent += read.toLong()
                    var localPeak = 0
                    var hasNonZero = false
                    var i = 0
                    while (i + 1 < read) {
                        val lo = buffer[i].toInt() and 0xFF
                        val hi = buffer[i + 1].toInt()
                        val sample = (hi shl 8) or lo
                        val amp = kotlin.math.abs(sample)
                        if (amp > localPeak) localPeak = amp
                        if (amp > 0) hasNonZero = true
                        i += 2
                    }
                    if (localPeak > asrPeakAmplitude) asrPeakAmplitude = localPeak
                    if (hasNonZero) asrNonZeroFrames += 1
                    asr?.write(buffer.copyOfRange(0, read))

                    // About 2 seconds with 40ms/frame. If still pure zero signal, fail fast.
                    if (frameCount >= 50 && asrPeakAmplitude == 0 && asrNonZeroFrames == 0L) {
                        asrLastError = "mic_zero_signal_source_$asrAudioSourceUsed"
                        handler.post {
                            setPipelineStage("asr_mic_zero_signal", "No mic signal detected")
                            Toast.makeText(this, "No mic signal. Check device mic.", Toast.LENGTH_SHORT).show()
                            showDetailedFailure("ASR mic zero signal")
                            stopVoiceInput()
                        }
                        break
                    }
                } else if (read < 0) {
                    asrReadErrors += 1
                    asrLastReadCode = read
                    if (asrReadErrors >= 10) {
                        asrLastError = "audio_read_error_$read"
                        handler.post {
                            setPipelineStage("asr_audio_read_failed", "Audio read failed")
                            showDetailedFailure("ASR audio read failed")
                        }
                        break
                    }
                }
                Thread.sleep(40)
            }
        }.start()
    }

    private fun stopAudioRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop ignored", e)
        }
        audioRecord?.release()
        audioRecord = null
    }

    private fun startVoiceAnimation() {
        val scaleUp = ScaleAnimation(1f, 1.2f, 1f, 1.2f)
        scaleUp.duration = 500
        scaleUp.repeatCount = ScaleAnimation.INFINITE
        scaleUp.repeatMode = ScaleAnimation.REVERSE
        voiceAnimation.startAnimation(scaleUp)
    }

    private fun stopVoiceAnimation() {
        voiceAnimation.clearAnimation()
    }

    private fun startAsrFinalizeTimeout() {
        cancelAsrFinalizeTimeout()
        asrFinalizeTimeoutRunnable = Runnable {
            if (!isAwaitingAsrFinal) return@Runnable
            isAwaitingAsrFinal = false
            voiceButton.isEnabled = true
            if (voiceInputText.isNotBlank()) {
                setPipelineStage("asr_timeout_with_partial", "ASR finalize timeout, using current text")
                finishVoiceInput()
            } else {
                val elapsed = System.currentTimeMillis() - asrRecognizingSinceMs
                voiceButton.visibility = View.VISIBLE
                voiceButton.text = "Voice"
                asrLastError = "timeout_no_text_${elapsed}ms"
                setPipelineStage("asr_timeout_no_text", "ASR timeout (${elapsed}ms), please retry")
                updateVisibleButtons()
                selectedButtonIndex = 0
                updateButtonSelection()
                Toast.makeText(this, "ASR timeout, no text received", Toast.LENGTH_SHORT).show()
                showDetailedFailure("ASR timeout", responseBody = "no text in finalize window")
            }
        }
        handler.postDelayed(asrFinalizeTimeoutRunnable!!, 20_000L)
    }

    private fun cancelAsrFinalizeTimeout() {
        asrFinalizeTimeoutRunnable?.let { handler.removeCallbacks(it) }
        asrFinalizeTimeoutRunnable = null
    }

    private fun submitToModel() {
        if (isSubmitting) {
            return
        }
        setPipelineStage("model_prepare")
        submitStartAtMs = System.currentTimeMillis()
        submitRequestId = "req-${submitStartAtMs}"
        submitStage = "validate_input"
        modelHttpCode = -1
        modelElapsedMs = 0L
        modelResponsePreview = ""

        if (capturedImageFile == null || voiceInputText.isEmpty()) {
            Toast.makeText(this, "Please capture and speak first", Toast.LENGTH_SHORT).show()
            return
        }
        submitStage = "decode_image"
        val bitmap = BitmapFactory.decodeFile(capturedImageFile?.absolutePath)
        if (bitmap == null) {
            statusText.text = "Image decode failed"
            Toast.makeText(this, "Image decode failed", Toast.LENGTH_SHORT).show()
            showDetailedFailure("Image decode failed")
            return
        }

        setSubmittingState(true)
        scheduleSubmittingWatchdog()
        setPipelineStage("model_processing", "Processing...")
        submitButton.visibility = View.GONE
        updateVisibleButtons()
        selectedButtonIndex = 0
        updateButtonSelection()

        submitStage = "build_payload"
        val base64Image = bitmapToBase64(bitmap)
        val json = JSONObject()
        json.put("model", ApiConfig.VISION_MODEL)
        val messages = JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        val content = JSONArray()
        val textContent = JSONObject()
        textContent.put("type", "text")
        textContent.put("text", voiceInputText)
        content.put(textContent)
        val imageContent = JSONObject()
        imageContent.put("type", "image_url")
        val imageUrl = JSONObject()
        imageUrl.put("url", "data:image/jpeg;base64,$base64Image")
        imageContent.put("image_url", imageUrl)
        content.put(imageContent)
        message.put("content", content)
        messages.put(message)
        json.put("messages", messages)
        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(ApiConfig.VISION_API_URL + "/chat/completions")
            .addHeader("Authorization", "Bearer ${ApiConfig.VISION_API_KEY}")
            .post(requestBody)
            .build()
        submitStage = "send_request"
        val call = client.newCall(request)
        activeModelCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                handler.post {
                    submitStage = "network_failure"
                    modelElapsedMs = System.currentTimeMillis() - submitStartAtMs
                    modelHttpCode = 0
                    modelResponsePreview = e.message ?: ""
                    setPipelineStage("model_network_failure", "Request failed")
                    endSubmitting()
                    submitButton.visibility = View.VISIBLE
                    updateVisibleButtons()
                    updateButtonSelection()
                    Toast.makeText(this@MainActivity, "Request failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    showDetailedFailure(
                        title = "Network failure",
                        throwable = e
                    )
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string().orEmpty()
                Log.i(TAG, "Model response code=${response.code}, body=${abbreviate(responseBody)}")
                handler.post {
                    submitStage = "response_received"
                    modelElapsedMs = System.currentTimeMillis() - submitStartAtMs
                    modelHttpCode = response.code
                    modelResponsePreview = abbreviate(responseBody, 500)
                    endSubmitting()
                    if (!response.isSuccessful) {
                        submitStage = "http_failure"
                        setPipelineStage("model_http_failure", "Request failed: HTTP ${response.code}")
                        submitButton.visibility = View.VISIBLE
                        updateVisibleButtons()
                        updateButtonSelection()
                        Toast.makeText(
                            this@MainActivity,
                            "HTTP ${response.code}: ${abbreviate(responseBody)}",
                            Toast.LENGTH_SHORT
                        ).show()
                        showDetailedFailure(
                            title = "HTTP failure",
                            httpCode = response.code,
                            responseBody = responseBody
                        )
                        return@post
                    }
                    try {
                        submitStage = "parse_response"
                        val contentText = extractModelContent(responseBody)
                        if (contentText.isBlank()) {
                            setPipelineStage("model_empty_content", "No model content")
                            resultText.visibility = View.VISIBLE
                            resultText.text = "Raw response:\n${abbreviate(responseBody, 1000)}"
                            submitButton.visibility = View.VISIBLE
                            updateVisibleButtons()
                            updateButtonSelection()
                            showDetailedFailure(
                                title = "Empty model content",
                                responseBody = responseBody
                            )
                            return@post
                        }
                        submitStage = "render_result"
                        setPipelineStage("model_success")
                        voiceText.visibility = View.GONE
                        voiceText.text = ""
                        resultText.visibility = View.VISIBLE
                        resultText.text = contentText
                        val elapsed = System.currentTimeMillis() - submitStartAtMs
                        setPipelineStage("model_done", "Done (${elapsed}ms), press A to restart")
                        updateVisibleButtons()
                        selectedButtonIndex = 0
                        updateButtonSelection()
                        if (isDevDiagnosticsEnabled()) {
                            Log.i(
                                TAG,
                                "requestId=$submitRequestId stage=$submitStage elapsedMs=$elapsed contentLen=${contentText.length}"
                            )
                        }
                        speakText(contentText)
                    } catch (e: Exception) {
                        setPipelineStage("model_parse_failed", "Parse failed")
                        resultText.visibility = View.VISIBLE
                        resultText.text = "Raw response:\n${abbreviate(responseBody, 1000)}"
                        submitButton.visibility = View.VISIBLE
                        updateVisibleButtons()
                        updateButtonSelection()
                        Toast.makeText(this@MainActivity, "Parse failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to parse model response", e)
                        showDetailedFailure(
                            title = "Parse failure",
                            throwable = e,
                            responseBody = responseBody
                        )
                    }
                }
            }
        })
    }

    private fun extractModelContent(responseBody: String): String {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val contentAny = message?.opt("content")
            return when (contentAny) {
                is String -> contentAny.trim()
                is JSONArray -> {
                    val parts = mutableListOf<String>()
                    for (i in 0 until contentAny.length()) {
                        val item = contentAny.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val text = item.optString("text")
                                if (text.isNotBlank()) parts.add(text)
                            }
                            is String -> if (item.isNotBlank()) parts.add(item)
                        }
                    }
                    parts.joinToString("\n").trim()
                }
                is JSONObject -> contentAny.optString("text", "").trim()
                else -> ""
            }
        }
        val directFields = listOf("result", "output_text", "text")
        for (field in directFields) {
            val value = root.optString(field)
            if (value.isNotBlank()) return value.trim()
        }
        return ""
    }
    private fun setSubmittingState(submitting: Boolean) {
        isSubmitting = submitting
        submitButton.isEnabled = !submitting
        captureButton.isEnabled = !submitting
        voiceButton.isEnabled = !submitting
        backButton.isEnabled = !submitting
    }

    private fun scheduleSubmittingWatchdog() {
        submittingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        submittingTimeoutRunnable = Runnable {
            if (!isSubmitting) return@Runnable
            submitStage = "timeout_watchdog"
            activeModelCall?.cancel()
            endSubmitting()
            statusText.text = "Request timeout"
            submitButton.visibility = View.VISIBLE
            updateVisibleButtons()
            updateButtonSelection()
            showDetailedFailure("Request timed out")
        }
        handler.postDelayed(submittingTimeoutRunnable!!, 70_000L)
    }

    private fun endSubmitting() {
        submittingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        submittingTimeoutRunnable = null
        activeModelCall = null
        setSubmittingState(false)
    }

    private fun showDetailedFailure(
        title: String,
        throwable: Throwable? = null,
        responseBody: String? = null,
        httpCode: Int? = null
    ) {
        if (!isDevDiagnosticsEnabled()) {
            return
        }
        val elapsed = if (submitStartAtMs > 0) System.currentTimeMillis() - submitStartAtMs else 0L
        val builder = StringBuilder()
        builder.appendLine("[DEV DIAG] $title")
        builder.appendLine("pipeline_stage=$pipelineStage")
        builder.appendLine("request_id=$submitRequestId")
        builder.appendLine("stage=$submitStage")
        builder.appendLine("elapsed_ms=$elapsed")
        builder.appendLine("model=${ApiConfig.VISION_MODEL}")
        builder.appendLine("endpoint=${ApiConfig.VISION_API_URL}/chat/completions")
        builder.appendLine("voice_text_len=${voiceInputText.length}")
        builder.appendLine("asr_elapsed_ms=${if (asrStartAtMs > 0) (System.currentTimeMillis() - asrStartAtMs) else 0}")
        builder.appendLine("asr_last_text_len=${asrLastText.length}")
        builder.appendLine("asr_bytes_sent=$asrBytesSent")
        builder.appendLine("asr_read_errors=$asrReadErrors")
        builder.appendLine("asr_last_read_code=$asrLastReadCode")
        builder.appendLine("asr_audio_source=$asrAudioSourceUsed")
        builder.appendLine("asr_peak_amplitude=$asrPeakAmplitude")
        builder.appendLine("asr_non_zero_frames=$asrNonZeroFrames")
        if (asrLastError.isNotBlank()) builder.appendLine("asr_error=$asrLastError")
        builder.appendLine("model_http_code=$modelHttpCode")
        builder.appendLine("model_elapsed_ms=$modelElapsedMs")
        if (modelResponsePreview.isNotBlank()) builder.appendLine("model_response_preview=${abbreviate(modelResponsePreview, 500)}")
        builder.appendLine("tts_text_len=$ttsTextLength")
        builder.appendLine("tts_elapsed_ms=$ttsElapsedMs")
        if (ttsLastError.isNotBlank()) builder.appendLine("tts_error=$ttsLastError")
        httpCode?.let { builder.appendLine("http_code=$it") }
        throwable?.let {
            builder.appendLine("error_type=${it.javaClass.simpleName}")
            builder.appendLine("error_msg=${it.message ?: "null"}")
        }
        if (!responseBody.isNullOrBlank()) {
            builder.appendLine("response_preview=${abbreviate(responseBody, 1500)}")
        }

        resultText.visibility = View.VISIBLE
        resultText.text = builder.toString()
    }

    private fun abbreviate(text: String, maxLen: Int = 300): String {
        return if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
    }

    private fun isDevDiagnosticsEnabled(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun speakText(text: String) {
        setPipelineStage("tts_prepare")
        if (!sparkReady) {
            Log.e(TAG, "TTS skipped: Spark SDK not ready")
            ttsLastError = "sdk_not_ready"
            showDetailedFailure("TTS skipped: SDK not ready")
            return
        }

        val ttsText = buildTtsText(text)
        if (ttsText.isBlank()) {
            Log.w(TAG, "TTS skipped: empty text after sanitize")
            ttsLastError = "empty_text_after_sanitize"
            showDetailedFailure("TTS skipped: empty output")
            return
        }
        ttsLastError = ""
        ttsTextLength = ttsText.length
        ttsStartAtMs = System.currentTimeMillis()
        ttsElapsedMs = 0L

        stopTtsPlayback()
        startTtsPlayback()

        tts = OnlineTTS(ApiConfig.XFYUN_TTS_VCN)
        tts?.aue("raw")
        tts?.sampleRate(16000)
        tts?.channels(1)
        tts?.bitDepth(16)
        tts?.bgs(0)
        tts?.speed(50)
        tts?.pitch(50)
        tts?.volume(50)

        val callbacks = object : TTSCallbacks {
            override fun onResult(result: com.iflytek.sparkchain.core.tts.TTS.TTSResult, usrTag: Any?) {
                ttsElapsedMs = System.currentTimeMillis() - ttsStartAtMs
                writeTtsData(result.getData(), result.getLen())
                val status = result.getStatus()
                if (status == 2 || status == 3) {
                    scheduleTtsPlaybackRelease(200)
                    setPipelineStage("tts_done", "Done")
                } else {
                    scheduleTtsPlaybackRelease(1200)
                    setPipelineStage("tts_streaming")
                }
            }

            override fun onError(error: com.iflytek.sparkchain.core.tts.TTS.TTSError, usrTag: Any?) {
                Log.e(TAG, "TTS error: ${error.getErrMsg()}")
                handler.post {
                    ttsLastError = error.getErrMsg()
                    ttsElapsedMs = System.currentTimeMillis() - ttsStartAtMs
                    stopTtsPlayback()
                    setPipelineStage("tts_error", "TTS error")
                    Toast.makeText(this@MainActivity, "TTS error: ${error.getErrMsg()}", Toast.LENGTH_SHORT).show()
                    showDetailedFailure("TTS failure: ${error.getErrMsg()}")
                }
            }
        }

        tts?.registerCallbacks(callbacks)
        Log.i(TAG, "TTS start len=${ttsText.length}")
        setPipelineStage("tts_start", "Speaking...")
        tts?.aRun(ttsText)
    }

    private fun startTtsPlayback() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) {
            ttsLastError = "audio_track_min_buffer_invalid"
            Log.e(TAG, "AudioTrack min buffer invalid: $minBuffer")
            return
        }

        ttsAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(audioFormat)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        ttsAudioTrack?.play()
    }

    private fun writeTtsData(data: ByteArray?, len: Int) {
        val audioTrack = ttsAudioTrack ?: return
        if (data == null || len <= 0) {
            return
        }
        val writeLen = minOf(len, data.size)
        if (writeLen <= 0) {
            return
        }
        try {
            audioTrack.write(data, 0, writeLen, AudioTrack.WRITE_BLOCKING)
        } catch (t: Throwable) {
            ttsLastError = "audio_track_write_failed:${t.message}"
            Log.e(TAG, "AudioTrack write failed", t)
        }
    }

    private fun scheduleTtsPlaybackRelease(delayMs: Long) {
        ttsTrackReleaseRunnable?.let { handler.removeCallbacks(it) }
        ttsTrackReleaseRunnable = Runnable { stopTtsPlayback() }
        handler.postDelayed(ttsTrackReleaseRunnable!!, delayMs)
    }

    private fun stopTtsPlayback() {
        ttsTrackReleaseRunnable?.let { handler.removeCallbacks(it) }
        ttsTrackReleaseRunnable = null
        try {
            ttsAudioTrack?.stop()
        } catch (_: Throwable) {
        }
        try {
            ttsAudioTrack?.release()
        } catch (_: Throwable) {
        }
        ttsAudioTrack = null
    }

    private fun buildTtsText(raw: String): String {
        var text = raw
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[*_#>\\-]{1,}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.length > 500) {
            text = text.substring(0, 500)
        }
        return text
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            when {
                captureButton.visibility == View.VISIBLE -> {
                    captureImage()
                    return true
                }
                voiceButton.visibility == View.VISIBLE -> {
                    startVoiceInput()
                    return true
                }
                submitButton.visibility == View.VISIBLE -> {
                    submitToModel()
                    return true
                }
                backButton.visibility == View.VISIBLE -> {
                    resetApp()
                    return true
                }
                resultText.visibility == View.VISIBLE -> {
                    resetApp()
                    return true
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (visibleButtons.size > 1) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    selectedButtonIndex = (selectedButtonIndex - 1 + visibleButtons.size) % visibleButtons.size
                } else {
                    selectedButtonIndex = (selectedButtonIndex + 1) % visibleButtons.size
                }
                updateButtonSelection()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun resetApp() {
        activeModelCall?.cancel()
        endSubmitting()
        submitStage = "reset"
        resetDiagnostics()
        isAwaitingAsrFinal = false
        cancelAsrFinalizeTimeout()
        capturedImageFile = null
        voiceInputText = ""
        voiceText.visibility = View.GONE
        voiceText.text = ""
        resultText.visibility = View.GONE
        resultText.text = ""
        captureButton.visibility = View.VISIBLE
        voiceButton.visibility = View.GONE
        voiceButton.text = "Voice"
        submitButton.visibility = View.GONE
        backButton.visibility = View.GONE
        statusText.text = "Welcome to VisionWatch"
        updateVisibleButtons()
        selectedButtonIndex = 0
        updateButtonSelection()
    }

    override fun onDestroy() {
        activeModelCall?.cancel()
        endSubmitting()
        submitStage = "destroyed"
        pipelineStage = "destroyed"
        isAwaitingAsrFinal = false
        cancelAsrFinalizeTimeout()
        stopVoiceAnimation()
        stopAudioRecording()
        stopTtsPlayback()
        asr = null
        tts = null
        client.dispatcher.cancelAll()
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun updateVisibleButtons() {
        visibleButtons.clear()
        if (captureButton.visibility == View.VISIBLE) {
            visibleButtons.add(captureButton)
        }
        if (voiceButton.visibility == View.VISIBLE) {
            visibleButtons.add(voiceButton)
        }
        if (submitButton.visibility == View.VISIBLE) {
            visibleButtons.add(submitButton)
        }
        if (backButton.visibility == View.VISIBLE) {
            visibleButtons.add(backButton)
        }
    }

    private fun updateButtonSelection() {
        visibleButtons.forEachIndexed { index, button ->
            if (index == selectedButtonIndex) {
                button.setBackgroundResource(R.drawable.button_selected)
            } else {
                button.setBackgroundResource(R.drawable.button_border)
            }
        }
    }
}

