/**
 * CactusManager — Singleton orchestrator for on-device Gemma 4 E2B AI inference.
 *
 * This is the central piece of MediAssist's AI architecture. It manages the full
 * lifecycle of the local LLM: downloading weights from HuggingFace, loading them
 * into RAM via Cactus SDK, constructing personalized medical prompts, and streaming
 * token-by-token responses back to the UI layer.
 *
 * Key design decisions:
 * - Singleton (object) because only one model instance can fit in RAM (~2 GB footprint)
 * - Download uses raw HttpURLConnection with manual redirect handling because
 *   HuggingFace returns 302 redirects that OkHttp's default policy doesn't follow
 *   for large binary files
 * - System prompt is dynamically assembled with the patient's profile and active
 *   medications so Gemma 4 can detect drug interactions in real time
 * - A special <MEDICATION_ADD> XML tag protocol allows the LLM to emit structured
 *   JSON that the ChatViewModel parses to auto-insert medications into Room
 */
package com.mediassist.app.domain

import com.cactus.cactusInit
import com.cactus.cactusComplete
import com.cactus.cactusStop
import com.cactus.cactusDestroy
import com.cactus.CactusTokenCallback
import com.mediassist.app.data.model.Medication
import com.mediassist.app.data.model.UserProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object CactusManager {
    /** Directory name where the Gemma 4 E2B model weights are stored on-device */
    const val MODEL_DIR_NAME = "gemma-4-E2B-it"

    /** HuggingFace URL for the INT4-quantized Gemma 4 E2B model (~4.7 GB zip) */
    const val MODEL_ZIP_URL = "https://huggingface.co/Cactus-Compute/gemma-4-E2B-it/resolve/main/weights/gemma-4-e2b-it-int4.zip"

    /** HuggingFace URL for the model configuration file */
    const val CONFIG_URL = "https://huggingface.co/Cactus-Compute/gemma-4-E2B-it/resolve/main/config.json"
    const val ZIP_FILENAME = "gemma-4-e2b-it-int4.zip"

    /** Native handle returned by cactusInit(); 0L means no model is loaded */
    private var modelHandle: Long = 0L

    /** When true, all inference calls return placeholder text (for UI testing without AI) */
    private var isDemoMode: Boolean = false

    /** Check if the model is ready for inference (either loaded in RAM or in demo mode) */
    fun isModelReady(): Boolean = modelHandle != 0L || isDemoMode

    /**
     * Verify that model weight files actually exist on disk.
     * Checks for .cact weight files or config files inside the model directory.
     * This prevents stale SharedPreferences flags from a previous install from
     * tricking the app into thinking the model is available.
     */
    fun isModelDownloaded(context: Context): Boolean {
        val modelDir = File(context.filesDir, "models/$MODEL_DIR_NAME")
        return modelDir.exists() && 
               modelDir.isDirectory && 
               (modelDir.listFiles()?.any { 
                   it.name.endsWith(".cact") || 
                   it.name == "config.json" || 
                   it.name == "config.txt" 
               } == true)
    }

    /** Enable demo mode — all AI calls return placeholder text instead of real inference */
    fun enableDemoMode() {
        isDemoMode = true
    }

    /**
     * Download the Gemma 4 E2B model from HuggingFace and extract it to local storage.
     *
     * Download flow:
     * 1. Create models directory structure under app's internal storage
     * 2. Download config.json (small metadata file)
     * 3. Download the INT4-quantized weights ZIP (~4.7 GB) with progress reporting
     * 4. Extract the ZIP to the model directory
     *
     * HuggingFace redirects are handled manually because the CDN uses 302/307 redirects
     * that require explicit follow-through for large file downloads.
     *
     * @param onProgress Called periodically with (bytesDownloaded, totalBytes).
     *                   When both are -1, it signals the extraction phase has begun.
     */
    suspend fun downloadModel(
        context: Context,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            val modelDir = File(modelsDir, MODEL_DIR_NAME)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // STEP 1 — Download config.json (lightweight model metadata)
            Log.d("CactusManager", "Descargando config.json...")
            val configFile = File(modelDir, "config.json")
            if (!configFile.exists()) {
                downloadFile(CONFIG_URL, configFile)
            }

            // STEP 2 — Download the model weights ZIP (~4.7 GB)
            // Uses a .tmp extension during download so partial downloads are never
            // mistaken for complete ones
            Log.d("CactusManager", "Descargando ZIP: $MODEL_ZIP_URL")
            val tmpZipFile = File(modelsDir, "$MODEL_DIR_NAME.zip.tmp")
            if (tmpZipFile.exists()) {
                tmpZipFile.delete()
            }

            // Manual redirect handling: HuggingFace CDN returns 302/307 redirects
            // that must be followed explicitly for large binary downloads
            var url = URL(MODEL_ZIP_URL)
            var connection: HttpURLConnection

            var redirectCount = 0
            while (true) {
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                // Disable auto-redirect to handle HuggingFace CDN redirects manually
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308
                ) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    // Safety limit: abort after 5 redirects to prevent infinite loops
                    if (redirectUrl == null || redirectCount++ > 5) {
                        return@withContext Result.failure(Exception("Demasiados redirects o Location faltante"))
                    }
                    url = if (redirectUrl.startsWith("/")) {
                        URL(url.protocol, url.host, url.port, redirectUrl)
                    } else {
                        URL(redirectUrl)
                    }
                    continue
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@withContext Result.failure(Exception("HTTP error: $responseCode"))
                }
                break
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L
            val buffer = ByteArray(8192)
            var lastProgressReport = 0L

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(tmpZipFile).use { output ->
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Throttle progress callbacks to every 512KB to avoid
                            // overwhelming the UI thread with recompositions
                            if (bytesDownloaded - lastProgressReport >= 512 * 1024) {
                                lastProgressReport = bytesDownloaded
                                Log.d("CactusManager", "Progreso ZIP: $bytesDownloaded / $totalBytes")
                                withContext(Dispatchers.Main) {
                                    onProgress(bytesDownloaded, totalBytes)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Clean up partial download on failure
                if (tmpZipFile.exists()) tmpZipFile.delete()
                Log.e("CactusManager", "Error en descarga: ${e.message}", e)
                return@withContext Result.failure(e)
            } finally {
                connection.disconnect()
            }

            // Final progress report (100%)
            withContext(Dispatchers.Main) {
                onProgress(bytesDownloaded, totalBytes)
            }

            // STEP 3 — Extract the ZIP into the model directory
            // Signal extraction phase with special (-1, -1) progress values
            Log.d("CactusManager", "Extrayendo ZIP...")
            withContext(Dispatchers.Main) {
                onProgress(-1L, -1L) // Indicates extracting
            }

            try {
                ZipInputStream(FileInputStream(tmpZipFile)).use { zis ->
                    var entry = zis.nextEntry
                    val bufferZip = ByteArray(8192)
                    while (entry != null) {
                        val newFile = File(modelDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zis.read(bufferZip).also { len = it } > 0) {
                                    fos.write(bufferZip, 0, len)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                // Clean up both the ZIP and extracted files on extraction failure
                if (tmpZipFile.exists()) tmpZipFile.delete()
                modelDir.deleteRecursively()
                Log.e("CactusManager", "Error extrayendo ZIP: ${e.message}", e)
                return@withContext Result.failure(Exception("Error extrayendo ZIP", e))
            }

            // Delete the temporary ZIP now that extraction is complete
            if (tmpZipFile.exists()) {
                tmpZipFile.delete()
            }

            Log.d("CactusManager", "Extracción completa. Archivos: ${modelDir.listFiles()?.map { it.name }}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CactusManager", "Error general: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download a single file with manual HTTP redirect handling.
     * Used for small files like config.json where progress tracking is unnecessary.
     */
    private fun downloadFile(urlString: String, targetFile: File) {
        var url = URL(urlString)
        var connection: HttpURLConnection
        var redirectCount = 0

        while (true) {
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308
            ) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (redirectUrl == null || redirectCount++ > 5) {
                    throw Exception("Demasiados redirects o Location faltante")
                }
                url = if (redirectUrl.startsWith("/")) {
                    URL(url.protocol, url.host, url.port, redirectUrl)
                } else {
                    URL(redirectUrl)
                }
                continue
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw Exception("HTTP error: $responseCode")
            }
            break
        }

        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()
    }

    /**
     * Load the Gemma 4 E2B model weights into RAM via the Cactus native engine.
     *
     * This is a heavy operation (~3–8 seconds) that allocates ~2 GB of RAM for the
     * INT4-quantized model. Called lazily — only when the user first navigates to
     * ChatScreen or ScanScreen, not at app startup.
     */
    suspend fun initModel(context: Context): Result<Unit> {
        isDemoMode = false
        return try {
            val modelDir = File(context.filesDir, "models/$MODEL_DIR_NAME").absolutePath
            Log.d("CactusManager", "Iniciando modelo desde carpeta: $modelDir")
            // cactusInit loads the .cact weight files and config.json into a native context
            modelHandle = cactusInit(modelDir, null, false)
            Log.d("CactusManager", "Modelo iniciado. Handle: $modelHandle")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CactusManager", "Error en initModel: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Ensure the model is loaded into RAM, initializing it if necessary.
     * This is the lazy-loading entry point used by ViewModels — it's a no-op
     * if the model is already loaded or if demo mode is active.
     */
    suspend fun ensureModelLoaded(context: Context): Result<Unit> {
        if (isDemoMode) return Result.success(Unit)
        if (isModelReady()) return Result.success(Unit)
        return initModel(context)
    }

    /** Download the model without initializing it (used by ModelLoadScreen) */
    suspend fun downloadOnly(
        context: Context,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        return downloadModel(context, onProgress)
    }

    /** Download the model AND load it into RAM in one step */
    suspend fun downloadAndInit(
        context: Context,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        downloadModel(context, onProgress).onFailure { return Result.failure(it) }
        return initModel(context)
    }

    /**
     * Generate a chat response from Gemma 4 E2B with full medical context.
     *
     * This is the primary inference function for the chat feature. It:
     * 1. Builds a dynamic system prompt containing the patient's profile, medical
     *    history, and active medications — so Gemma 4 can detect drug interactions
     *    and give personalized advice
     * 2. Includes the <MEDICATION_ADD> tag protocol in the prompt so the LLM can
     *    output structured JSON when the user wants to add a new medication
     * 3. Streams tokens one-by-one to [onToken] for real-time UI updates
     *
     * @param userMessage The patient's text input
     * @param userProfile The patient's demographic data (age, weight, medical history)
     * @param medications All medications from Room DB (active ones are injected into prompt)
     * @param onToken Called for each generated token — drives the streaming chat UI
     * @return The complete response text, or failure if inference fails
     */
    suspend fun generateResponse(
        userMessage: String,
        userProfile: UserProfile?,
        medications: List<Medication>,
        onToken: (String) -> Unit
    ): Result<String> {
        if (isDemoMode) {
            onToken("Modo demo activo. Conectá el modelo para respuestas reales.")
            return Result.success("Modo demo activo. Conectá el modelo para respuestas reales.")
        }
        if (modelHandle == 0L) return Result.failure(Exception("Modelo no inicializado"))

        return try {
            // ── Build the dynamic system prompt ──────────────────────────────
            // The system prompt is personalized with the patient's medical data so
            // Gemma 4 can provide contextually aware responses and detect potential
            // drug interactions or contraindications in real time
            val systemPrompt = buildString {
                append("Sos Pam, una asistente médica y terapéutica personal. ")
                append("Tu usuario es un adulto mayor que necesita compañía, contención y ayuda con su salud. ")
                append("Sé cálida, compasiva, paciente y usá un tono cercano y cariñoso. ")
                append("Respondé siempre en español rioplatense, de forma simple y clara.\\n\\n")
                append("Tu misión principal es:\\n")
                append("1. Ayudar con dudas médicas (sin reemplazar a un profesional, aclaralo si es necesario).\\n")
                append("2. Guiar al usuario para agregar medicamentos paso a paso.\\n")
                append("3. Hacerle compañía: podés charlar de temas cotidianos, escucharlo, preguntarle cómo se siente, hablar del clima, de la familia, de recuerdos. Sos su compañera.\\n\\n")
                append("IMPORTANTE: Si el usuario te hace preguntas técnicas no médicas (programación, matemáticas avanzadas, código, etc.), rechazalas amablemente diciendo que solo podés ayudar con temas de salud y compañía.\\n\\n")

                // Inject patient profile into prompt so the LLM has full medical context
                if (userProfile != null) {
                    append("El paciente es: ${userProfile.name} ${userProfile.surname}, ")
                    append("${userProfile.calculateAge()} años, ${userProfile.weight}kg.\\n")
                    if (userProfile.medicalHistory.isNotBlank()) {
                        append("Historial médico: ${escapeJson(userProfile.medicalHistory)}\\n")
                    }
                }

                // Include active medications so Gemma 4 can detect drug interactions
                // and give personalized advice about potential contraindications
                val activeMeds = medications.filter { it.active }
                if (activeMeds.isNotEmpty()) {
                    val medList = activeMeds.joinToString(", ") { "${it.name} ${it.dose}" }
                    append("Medicación actual: ${escapeJson(medList)}\\n")
                }

                // Medication-add protocol: instruct the LLM to emit structured JSON
                // wrapped in <MEDICATION_ADD> tags when the user wants to add a medication.
                // The ChatViewModel parses this tag to auto-insert into Room DB.
                append("\\nSi el usuario quiere agregar un medicamento, guialo paso a paso con estas preguntas en orden:\\n")
                append("1. ¿Cómo se llama el medicamento?\\n")
                append("2. ¿Qué dosis tomás? (ej: 10mg, 1 comprimido)\\n")
                append("3. ¿Cada cuántas horas lo tomás?\\n")
                append("4. ¿A qué hora/s lo tomás? (podés decir varias horas)\\n")
                append("Cuando tengas todos los datos, respondé EXACTAMENTE con este formato JSON en tu mensaje, sin texto adicional antes ni después:\\n")
                append("<MEDICATION_ADD>\\n")
                append("{\\\"name\\\":\\\"...\\\",\\\"dose\\\":\\\"...\\\",\\\"frequencyHours\\\":...,\\\"scheduleTimes\\\":\\\"HH:MM,HH:MM\\\"}\\n")
                append("</MEDICATION_ADD>\\n")
                append("Luego confirmá al usuario que fue agregado exitosamente.\\n")
            }

            // Build OpenAI-compatible messages JSON for the Cactus SDK
            val messagesJson = "[{\"role\":\"system\",\"content\":\"${escapeJson(systemPrompt)}\"},{\"role\":\"user\",\"content\":\"${escapeJson(userMessage)}\"}]"

            Log.d("CactusManager", "Enviando messagesJson: $messagesJson")

            // ── Stream tokens from Gemma 4 ──────────────────────────────────
            // Each token is delivered to the callback as it's generated, enabling
            // the chat UI to display the response character-by-character
            val fullResponse = StringBuilder()
            withContext(Dispatchers.IO) {
                cactusComplete(modelHandle, messagesJson, null, null, CactusTokenCallback { token, _ ->
                    fullResponse.append(token)
                    onToken(token)
                })
            }
            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            Log.e("CactusManager", "Error en generateResponse: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Interrupt the current token generation immediately.
     * Called when the user taps the stop button during streaming.
     */
    fun stopGeneration() {
        if (modelHandle != 0L) {
            cactusStop(modelHandle)
        }
    }

    /**
     * Run a generic inference with custom system/user prompts (non-streaming).
     * Used internally for tasks that don't need token-by-token UI updates.
     */
    suspend fun runInference(systemPrompt: String, userPrompt: String): String {
        if (isDemoMode) {
            return "Función disponible solo con IA activada"
        }
        if (modelHandle == 0L) return "Error: Model not loaded"

        return try {
            val messagesJson = "[{\"role\":\"system\",\"content\":\"${escapeJson(systemPrompt)}\"},{\"role\":\"user\",\"content\":\"${escapeJson(userPrompt)}\"}]"

            val fullResponse = java.lang.StringBuilder()
            withContext(Dispatchers.IO) {
                cactusComplete(modelHandle, messagesJson, null, null, CactusTokenCallback { token, _ ->
                    fullResponse.append(token)
                })
            }
            fullResponse.toString()
        } catch (e: Exception) {
            Log.e("CactusManager", "Error en runInference: ${e.message}", e)
            "Error al procesar"
        }
    }

    /**
     * Analyze a product label scanned via CameraX + ML Kit OCR using Gemma 4.
     *
     * This powers the "Scan Product" feature. The OCR-extracted text from a food/medication
     * label is sent to Gemma 4 along with the patient's profile and active medications.
     * The LLM determines whether the product is safe for the patient considering their
     * medical conditions and current drug regimen.
     *
     * Pipeline: CameraX capture → ML Kit OCR → extracted text → Gemma 4 analysis
     *
     * @param extractedText Raw text from ML Kit OCR (product ingredients, dosage info, etc.)
     * @param userProfile Patient demographics and medical history for personalized analysis
     * @param medications Active medications to check for drug-food/drug-drug interactions
     * @param onToken Streaming callback for real-time UI updates during analysis
     */
    suspend fun analyzeProductText(
        extractedText: String,
        userProfile: UserProfile?,
        medications: List<Medication>,
        onToken: (String) -> Unit
    ): Result<String> {
        if (modelHandle == 0L) return Result.failure(Exception("Modelo no inicializado"))

        return try {
            // Build patient context string for the analysis prompt
            val profileContext = buildString {
                if (userProfile != null) {
                    append("Paciente: ${userProfile.name} ${userProfile.surname}, ")
                    append("${userProfile.calculateAge()} años, ${userProfile.weight}kg. ")
                    if (userProfile.medicalHistory.isNotBlank()) {
                        append("Historial: ${userProfile.medicalHistory}. ")
                    }
                }
                val activeMeds = medications.filter { it.active }
                if (activeMeds.isNotEmpty()) {
                    append("Medicación actual: ")
                    append(activeMeds.joinToString(", ") { "${it.name} ${it.dose}" })
                }
            }

            // Construct the user prompt with the OCR-extracted label text
            val userMessage = "Se escaneó la etiqueta de un producto. " +
                "El texto extraído es:\\n\\\"\\\"\\\"\\n${escapeJson(extractedText)}\\n\\\"\\\"\\\"\\n\\n" +
                "Perfil del paciente: ${escapeJson(profileContext)}\\n\\n" +
                "Decime solamente si el paciente PUEDE o NO PUEDE consumir este producto y por qué. " +
                "Máximo 3 o 4 líneas. No des explicaciones largas ni listas."

            // System prompt instructs Gemma 4 to be ultra-concise for scan results
            val systemPrompt = "Sos Pam, asistente médica. Cuando te muestren un producto escaneado, " +
                "respondé SOLO si el paciente puede o no consumirlo y el motivo principal. " +
                "Sé ultra breve: máximo 3-4 líneas. Sin introducciones, sin listas, sin explicaciones largas. Directo al punto."

            val messagesJson = "[{\"role\":\"system\",\"content\":\"${escapeJson(systemPrompt)}\"},{\"role\":\"user\",\"content\":\"$userMessage\"}]"

            Log.d("CactusManager", "analyzeProductText messagesJson: $messagesJson")

            // Stream the analysis result token by token
            val fullResponse = StringBuilder()
            withContext(Dispatchers.IO) {
                cactusComplete(
                    modelHandle,
                    messagesJson,
                    null,
                    null,
                    CactusTokenCallback { token, _ ->
                        fullResponse.append(token)
                        onToken(token)
                    }
                )
            }
            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            Log.e("CactusManager", "Error en analyzeProductText: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stop an ongoing inference at the native level.
     * Used by ScanViewModel when the user cancels a product analysis mid-stream.
     */
    fun stopInference() {
        if (modelHandle == 0L) return
        try {
            cactusStop(modelHandle)
            Log.d("CactusManager", "Inferencia detenida nativamente")
        } catch (e: Exception) {
            Log.e("CactusManager", "Error al detener inferencia: ${e.message}")
        }
    }

    /** Release all native model resources. Called when the app is being destroyed. */
    fun destroy() {
        if (modelHandle != 0L) {
            cactusDestroy(modelHandle)
            modelHandle = 0L
        }
    }

    /** Escape special characters for safe embedding in JSON string values */
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
