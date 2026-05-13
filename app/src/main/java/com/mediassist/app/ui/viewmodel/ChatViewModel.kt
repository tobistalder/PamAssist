/**
 * ChatViewModel — Orchestrates the conversational AI interface and medication extraction.
 *
 * This ViewModel bridges the UI (ChatScreen) with the AI engine (CactusManager),
 * speech services (STT/TTS), and local database (Room). It is responsible for:
 * 1. Managing the chat message state (user inputs, AI responses, loading bubbles).
 * 2. Coordinating Speech-to-Text (Android SpeechRecognizer) and Text-to-Speech (TTS).
 * 3. Processing the special <MEDICATION_ADD> protocol: when Gemma 4 detects the user
 *    wants to add a medication, it emits a JSON block. This ViewModel intercepts it,
 *    parses the JSON, inserts the medication into Room, and schedules the offline alarms.
 *
 * Note: Model loading is done lazily here to prevent freezing the app at startup.
 */
package com.mediassist.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediassist.app.data.model.Medication
import com.mediassist.app.data.model.UserProfile
import com.mediassist.app.domain.AlarmScheduler
import com.mediassist.app.domain.CactusManager
import com.mediassist.app.domain.repository.MedicationRepository
import com.mediassist.app.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

/**
 * Represents a single chat bubble in the UI.
 * @param content The text content of the message.
 * @param isUser True if the message was sent by the patient, false if from Pam (AI).
 * @param isLoading True if this is an AI message currently being streamed (shows a typing indicator).
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

class ChatViewModel(
    application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val medicationRepository: MedicationRepository
) : AndroidViewModel(application), RecognitionListener, TextToSpeech.OnInitListener {

    /** Observable list of chat messages driving the LazyColumn in ChatScreen */
    val messages = mutableStateListOf<ChatMessage>()

    private val _isGenerating = mutableStateOf(false)
    /** True when Gemma 4 is actively streaming a response */
    val isGenerating: State<Boolean> = _isGenerating

    private val _inputText = mutableStateOf("")
    val inputText: State<String> = _inputText

    private val _isListening = mutableStateOf(false)
    /** True when the microphone is active and SpeechRecognizer is listening */
    val isListening: State<Boolean> = _isListening

    private val _isLoadingModel = mutableStateOf(false)
    /** True while the ~4.7 GB model is being loaded into RAM upon first chat open */
    val isLoadingModel: State<Boolean> = _isLoadingModel

    private val _modelLoadError = mutableStateOf<String?>(null)
    val modelLoadError: State<String?> = _modelLoadError

    // --- Android Speech Services ---
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
    private var tts: TextToSpeech? = null
    private var isTtsEnabled = false
    private var lastInputWasVoice = false
    private var isInterrupted = false

    init {
        // Initial greeting message from Pam
        messages.add(
            ChatMessage(
                content = """¡Hola! Soy Pam, tu asistente médica personal 😊

Puedo ayudarte con:
- 💬 Consultas médicas — preguntame cualquier duda sobre tu salud
- 💊 Agregar medicamentos — decime "quiero agregar un medicamento" y te guío paso a paso
- ⏰ Recordatorios — te aviso cuando es hora de tomar tus medicamentos
- 📷 Escanear productos — analizamos si un alimento o medicamento es adecuado para vos

¿En qué te puedo ayudar hoy?""",
                isUser = false,
                isLoading = false
            )
        )
        speechRecognizer.setRecognitionListener(this)
        tts = TextToSpeech(application, this)

        // Lazy model load: Only load the massive AI weights when the user actually enters the chat
        viewModelScope.launch {
            if (!CactusManager.isModelReady()) {
                _isLoadingModel.value = true
                val result = CactusManager.ensureModelLoaded(application)
                _isLoadingModel.value = false
                result.onFailure {
                    _modelLoadError.value = "Error al cargar el modelo: ${it.message}"
                }
            }
        }
    }

    /** Retry loading the model if it failed (e.g., due to temporary OOM) */
    fun retryModelLoad() {
        _modelLoadError.value = null
        viewModelScope.launch {
            _isLoadingModel.value = true
            val result = CactusManager.ensureModelLoaded(getApplication())
            _isLoadingModel.value = false
            result.onFailure {
                _modelLoadError.value = "Error al cargar el modelo: ${it.message}"
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    /** Submit the user's typed message to the AI */
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isGenerating.value) return

        _inputText.value = ""
        lastInputWasVoice = false
        isInterrupted = false
        processUserMessage(text)
    }

    /** Start the Android SpeechRecognizer to capture user voice input */
    fun startListening() {
        if (_isGenerating.value) return

        lastInputWasVoice = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
        }
        speechRecognizer.startListening(intent)
        _isListening.value = true
    }

    /** Stop capturing voice input */
    fun stopListening() {
        speechRecognizer.stopListening()
        _isListening.value = false
    }

    /**
     * Core AI pipeline: Sends the user's text to Gemma 4 and streams the response.
     * Also fetches the user's medical profile and active medications from Room to
     * provide personalized context to the LLM via CactusManager.
     */
    private fun processUserMessage(text: String) {
        messages.add(ChatMessage(content = text, isUser = true))

        // Add an empty AI message bubble that will be updated token-by-token
        val gemmaMessageIndex = messages.size
        messages.add(ChatMessage(content = "", isUser = false, isLoading = true))
        _isGenerating.value = true
        isInterrupted = false

        viewModelScope.launch {
            // Fetch personalized context from the persistence layer
            val userProfile = userProfileRepository.latestProfile.firstOrNull()
            val medications = medicationRepository.allMedications.firstOrNull() ?: emptyList()

            var fullResponse = ""
            val result = CactusManager.generateResponse(
                userMessage = text,
                userProfile = userProfile,
                medications = medications,
                onToken = { token ->
                    // Real-time streaming UI update
                    fullResponse += token
                    if (gemmaMessageIndex < messages.size) {
                        messages[gemmaMessageIndex] = messages[gemmaMessageIndex].copy(
                            content = fullResponse,
                            isLoading = false
                        )
                    }
                }
            )

            result.onSuccess { finalResponse ->
                // Look for the <MEDICATION_ADD> tag in the final AI response
                handleMedicationAdd(finalResponse, gemmaMessageIndex)

                // If the user spoke to the app, respond with synthesized speech
                if (!isInterrupted && lastInputWasVoice && isTtsEnabled) {
                    speak(finalResponse)
                } else {
                    _isGenerating.value = false
                }
            }.onFailure {
                _isGenerating.value = false
                if (gemmaMessageIndex < messages.size) {
                    messages[gemmaMessageIndex] = messages[gemmaMessageIndex].copy(
                        content = "Error al obtener respuesta.",
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * CRITICAL HACKATHON FEATURE: AI-driven database insertion.
     * Parses the LLM's response for a special <MEDICATION_ADD> XML tag. If found,
     * extracts the JSON payload, inserts the new medication into the Room DB,
     * and automatically schedules offline AlarmManager reminders.
     *
     * @param response The full text generated by Gemma 4
     * @param messageIndex The index of the AI message bubble to clean up after parsing
     */
    private fun handleMedicationAdd(response: String, messageIndex: Int) {
        try {
            // Regex to extract the JSON block hidden between <MEDICATION_ADD> tags
            val regex = Regex("<MEDICATION_ADD>\\s*(\\{.*?\\})\\s*</MEDICATION_ADD>", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(response) ?: return

            val jsonStr = match.groupValues[1]
            val json = JSONObject(jsonStr)

            // Extract structured data populated by the LLM
            val name = json.getString("name")
            val dose = json.getString("dose")
            val frequencyHours = json.optInt("frequencyHours", 0)
            val scheduleTimes = json.optString("scheduleTimes", "")

            val medication = Medication(
                name = name,
                dose = dose,
                frequencyHours = frequencyHours,
                scheduleTimes = scheduleTimes,
                active = true
            )

            viewModelScope.launch {
                try {
                    // Persist to Room
                    medicationRepository.insertMedication(medication)

                    // Reschedule exact alarms via AlarmScheduler for offline notifications
                    val allMeds = medicationRepository.allMedications.firstOrNull() ?: emptyList()
                    AlarmScheduler(getApplication<Application>().applicationContext).scheduleAll(allMeds)

                    // Clean up the UI message: replace the raw JSON tag with a friendly confirmation
                    val cleanMessage = "✅ Listo, agregué $name $dose a tus medicamentos."
                    if (messageIndex < messages.size) {
                        messages[messageIndex] = messages[messageIndex].copy(
                            content = cleanMessage,
                            isLoading = false
                        )
                    }
                    Log.d("ChatViewModel", "Medicamento agregado desde chat: $name $dose")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error insertando medicamento: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error parseando MEDICATION_ADD: ${e.message}")
            // If parsing fails, we gracefully leave the original message as-is
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    //  SpeechRecognizer Callbacks
    // ═══════════════════════════════════════════════════════════════════════════════

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _isListening.value = false
    }
    override fun onError(error: Int) {
        _isListening.value = false
    }

    /** Triggered when STT engine finishes converting user speech to text */
    override fun onResults(results: Bundle?) {
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = data?.get(0) ?: ""
        if (text.isNotEmpty()) {
            processUserMessage(text) // Feed the transcribed text directly to Gemma
        }
    }

    /** 
     * User-triggered stop: Interrupts both the native Cactus AI inference 
     * and the Android TTS engine immediately.
     */
    fun stop() {
        isInterrupted = true
        viewModelScope.launch {
            CactusManager.stopGeneration()
            tts?.stop()
            _isGenerating.value = false
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    // ═══════════════════════════════════════════════════════════════════════════════
    //  TextToSpeech (TTS) Integration
    // ═══════════════════════════════════════════════════════════════════════════════

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "AR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
            isTtsEnabled = true
            
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Keep isGenerating = true so the stop button stays visible while speaking
                }
                override fun onDone(utteranceId: String?) {
                    _isGenerating.value = false
                }
                override fun onError(utteranceId: String?) {
                    _isGenerating.value = false
                }
            })
        }
    }

    private fun speak(text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "gemma_response")
        }
        // Use QUEUE_FLUSH to immediately replace any existing speech
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "gemma_response")
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}

class ChatViewModelFactory(
    private val application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val medicationRepository: MedicationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, userProfileRepository, medicationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
