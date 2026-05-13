/**
 * ScanViewModel — Manages the CameraX + ML Kit + Gemma 4 product analysis pipeline.
 *
 * This ViewModel bridges the optical character recognition (OCR) capabilities with
 * the local AI engine to determine if a product (food or medication) is safe for
 * the patient to consume.
 *
 * Pipeline Flow:
 * 1. Image Capture: CameraX saves a high-res photo to a temporary file.
 * 2. OCR Extraction: ML Kit Text Recognition extracts raw text from the image.
 * 3. AI Analysis: CactusManager sends the extracted text + patient profile + active
 *    medications to Gemma 4.
 * 4. Streaming Result: The LLM streams a concise "YES/NO and why" response back to the UI.
 *
 * This prevents Out-of-Memory (OOM) errors by avoiding passing heavy raw image buffers
 * to the AI model, utilizing the lightweight ML Kit for the vision step instead.
 */
package com.mediassist.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mediassist.app.domain.CactusManager
import com.mediassist.app.domain.repository.MedicationRepository
import com.mediassist.app.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Represents the current phase of the product scanning lifecycle */
enum class ScanState {
    PREVIEWING, // Camera is active, waiting for user to capture
    ANALYZING,  // Image captured, currently running OCR + Gemma inference
    RESULT,     // Analysis complete, showing AI determination
    CANCELLED,  // User aborted the analysis mid-stream
    ERROR       // OCR failure, model load error, or empty text
}

class ScanViewModel(
    application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val medicationRepository: MedicationRepository
) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow(ScanState.PREVIEWING)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _analysisResult = MutableStateFlow("")
    /** The token-by-token streamed response from Gemma 4 regarding product safety */
    val analysisResult: StateFlow<String> = _analysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _capturedImagePath = MutableStateFlow<String?>(null)
    /** Path to the temporary JPEG file captured by CameraX */
    val capturedImagePath: StateFlow<String?> = _capturedImagePath.asStateFlow()

    private val _isLoadingModel = MutableStateFlow(false)
    /** True while the ~4.7 GB model is being loaded into RAM (lazy loading) */
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel.asStateFlow()

    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError.asStateFlow()

    init {
        // Lazy loading: Only init the massive AI model when the user enters the scanner
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

    /** Retry loading the model if it failed (e.g., due to temporary OOM on device) */
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

    /**
     * Core Analysis Pipeline:
     * Takes the absolute path of the captured JPEG and runs the OCR -> AI sequence.
     */
    fun analyzeImageWithOCR(imagePath: String) {
        _capturedImagePath.value = imagePath
        _scanState.value = ScanState.ANALYZING
        _analysisResult.value = ""
        _errorMessage.value = ""

        viewModelScope.launch {
            try {
                // Step 1: Extract raw text from the label using Google ML Kit.
                // This is performed on an IO dispatcher to avoid blocking the main thread.
                val extractedText = withContext(Dispatchers.IO) {
                    extractTextFromImage(imagePath)
                }

                Log.d("ScanViewModel", "Texto extraído: $extractedText")

                // If ML Kit couldn't find any text (blurry image, wrong angle), fail early
                if (extractedText.isBlank()) {
                    _errorMessage.value = "No se pudo leer texto en la imagen. " +
                        "Asegurate de que la etiqueta sea legible."
                    _scanState.value = ScanState.ERROR
                    return@launch
                }

                // Step 2: Feed the OCR text to Gemma 4 for medical analysis
                // We fetch the patient's profile and active meds so the AI can check for contraindications
                val userProfile = userProfileRepository.latestProfile.firstOrNull()
                val medications = medicationRepository.allMedications.firstOrNull()
                    ?: emptyList()

                var fullResponse = ""
                // analyzeProductText streams the answer token-by-token
                val result = CactusManager.analyzeProductText(
                    extractedText = extractedText,
                    userProfile = userProfile,
                    medications = medications,
                    onToken = { token ->
                        fullResponse += token
                        _analysisResult.value = fullResponse // Drive the UI typewriter effect
                    }
                )

                result.onSuccess {
                    _scanState.value = ScanState.RESULT
                }.onFailure { e ->
                    _errorMessage.value = "Error al analizar: ${e.message}"
                    _scanState.value = ScanState.ERROR
                }

            } catch (e: Exception) {
                Log.e("ScanViewModel", "Error en OCR: ${e.message}", e)
                _errorMessage.value = "Error al procesar la imagen."
                _scanState.value = ScanState.ERROR
            }
        }
    }

    /** Cancel the ongoing AI analysis by signaling the native Cactus SDK */
    fun stopAnalysis() {
        CactusManager.stopInference()
        _scanState.value = ScanState.CANCELLED
    }

    /** Rerun the analysis on the same previously captured image */
    fun retryAnalysis() {
        _capturedImagePath.value?.let { path ->
            analyzeImageWithOCR(path)
        }
    }

    /**
     * Executes Google ML Kit Text Recognition on the provided image file.
     * Uses coroutine suspendCoroutine to bridge the callback-based ML Kit API into Kotlin Coroutines.
     */
    private suspend fun extractTextFromImage(imagePath: String): String {
        return suspendCoroutine { continuation ->
            val image = InputImage.fromFilePath(
                getApplication(),
                Uri.fromFile(File(imagePath))
            )
            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )
            // Asynchronous vision processing
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /** Discard the current image and return the UI to the live camera preview */
    fun resetScanner() {
        deleteTempFile() // Ensure we don't leak storage space
        _scanState.value = ScanState.PREVIEWING
        _analysisResult.value = ""
        _errorMessage.value = ""
        _capturedImagePath.value = null
    }

    /** Utility to clean up the temporary JPEG file written by CameraX */
    private fun deleteTempFile() {
        _capturedImagePath.value?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    Log.d("ScanViewModel", "Archivo temporal eliminado: $path")
                }
            } catch (e: Exception) {
                Log.e("ScanViewModel", "Error eliminando archivo: ${e.message}")
            }
        }
    }

    /** Ensure temp files are cleaned up when the ViewModel is destroyed */
    override fun onCleared() {
        super.onCleared()
        deleteTempFile()
    }
}

class ScanViewModelFactory(
    private val application: Application,
    private val userProfileRepository: UserProfileRepository,
    private val medicationRepository: MedicationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScanViewModel(application, userProfileRepository, medicationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
