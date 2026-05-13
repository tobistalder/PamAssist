/**
 * Cactus SDK — Kotlin JNI bindings for on-device AI inference.
 *
 * This file provides the Kotlin-facing API for the Cactus native library (libcactus.so),
 * which powers all local AI inference in MediAssist. The Cactus SDK enables running
 * Gemma 4 E2B (a ~4.7 GB quantized LLM) entirely on-device without any cloud dependency,
 * ensuring patient data privacy and fully offline operation.
 *
 * Architecture:
 *   Kotlin ViewModel → CactusManager → Cactus.kt (this file) → JNI → libcactus.so (C++)
 *
 * Key capabilities used by MediAssist:
 *   - [cactusInit]: Load the Gemma 4 E2B model weights into RAM
 *   - [cactusComplete]: Run chat completions with token-by-token streaming
 *   - [cactusStop]: Interrupt an ongoing inference (user-triggered stop)
 *   - [cactusDestroy]: Release native model resources from memory
 */
package com.cactus

/**
 * Functional interface for receiving tokens during streaming inference.
 *
 * Called by the native layer for each generated token, enabling real-time
 * UI updates as Gemma 4 produces its response character by character.
 */
fun interface CactusTokenCallback {
    fun onToken(token: String, tokenId: Int)
}

/**
 * Functional interface for receiving native-level log messages from the Cactus engine.
 * Useful for debugging model loading and inference issues.
 */
fun interface CactusLogCallback {
    fun onLog(level: Int, component: String, message: String)
}

/**
 * Private JNI bridge to the native Cactus library (libcactus.so).
 *
 * On instantiation, it loads the native shared library and initializes the
 * framework. All methods here are direct mappings to C++ functions; they should
 * only be called through the public wrapper functions below.
 */
private object CactusJNI {
    init {
        // Load the native Cactus library (libcactus.so) bundled in the APK's jniLibs
        System.loadLibrary("cactus")
        // Initialize the native framework (sets up internal thread pools, allocators, etc.)
        nativeSetFramework()
    }

    @JvmStatic external fun nativeSetFramework()
    @JvmStatic external fun nativeGetLastError(): String
    @JvmStatic external fun nativeSetCacheDir(cacheDir: String)
    @JvmStatic external fun nativeSetAppId(appId: String)
    @JvmStatic external fun nativeTelemetryFlush()
    @JvmStatic external fun nativeTelemetryShutdown()

    /**
     * Initialize a model from disk. Returns a non-zero handle on success, 0 on failure.
     * @param modelPath Path to the directory containing model weights (e.g., .cact files)
     * @param corpusDir Optional path for RAG corpus indexing (unused in MediAssist)
     * @param cacheIndex Whether to cache the corpus index on disk
     */
    @JvmStatic external fun nativeInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeReset(handle: Long)

    /** Signal the native engine to stop the current inference as soon as possible. */
    @JvmStatic external fun nativeStop(handle: Long)

    /**
     * Run a full chat completion with optional streaming via [callback].
     * @param messagesJson JSON array of {role, content} messages (OpenAI-compatible format)
     * @param optionsJson Optional generation parameters (temperature, top_k, etc.)
     * @param callback If non-null, called for each generated token (enables streaming UI)
     * @return The complete generated response as a single string
     */
    @JvmStatic external fun nativeComplete(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String
    @JvmStatic external fun nativePrefill(handle: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, pcmData: ByteArray?): String
    @JvmStatic external fun nativeTranscribe(handle: Long, audioPath: String?, prompt: String?, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String
    @JvmStatic external fun nativeEmbed(handle: Long, text: String, normalize: Boolean): FloatArray
    @JvmStatic external fun nativeRagQuery(handle: Long, query: String, topK: Int): String
    @JvmStatic external fun nativeTokenize(handle: Long, text: String): IntArray
    @JvmStatic external fun nativeScoreWindow(handle: Long, tokens: IntArray, start: Int, end: Int, context: Int): String
    @JvmStatic external fun nativeImageEmbed(handle: Long, imagePath: String): FloatArray
    @JvmStatic external fun nativeAudioEmbed(handle: Long, audioPath: String): FloatArray
    @JvmStatic external fun nativeVad(handle: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
    @JvmStatic external fun nativeDiarize(handle: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
    @JvmStatic external fun nativeEmbedSpeaker(handle: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?, maskWeights: FloatArray?): String
    @JvmStatic external fun nativeStreamTranscribeInit(handle: Long, optionsJson: String?): Long
    @JvmStatic external fun nativeStreamTranscribeProcess(handle: Long, pcmData: ByteArray): String
    @JvmStatic external fun nativeStreamTranscribeStop(handle: Long): String
    @JvmStatic external fun nativeIndexInit(indexDir: String, embeddingDim: Int): Long
    @JvmStatic external fun nativeIndexAdd(handle: Long, ids: IntArray, documents: Array<String>, metadatas: Array<String>?, embeddings: Array<FloatArray>, embeddingDim: Long): Int
    @JvmStatic external fun nativeIndexDelete(handle: Long, ids: IntArray): Int
    @JvmStatic external fun nativeIndexGet(handle: Long, ids: IntArray): String
    @JvmStatic external fun nativeIndexQuery(handle: Long, embedding: FloatArray, topK: Long, optionsJson: String?): String
    @JvmStatic external fun nativeIndexCompact(handle: Long): Int
    @JvmStatic external fun nativeIndexDestroy(handle: Long)
    @JvmStatic external fun nativeDetectLanguage(handle: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
    @JvmStatic external fun nativeLogSetLevel(level: Int)
    @JvmStatic external fun nativeLogSetCallback(callback: CactusLogCallback?)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PUBLIC API — Used by CactusManager to drive Gemma 4 E2B inference
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Initialize the Gemma 4 E2B model from disk and return a native handle.
 *
 * This is a blocking operation that loads ~4.7 GB of quantized weights into RAM.
 * It typically takes 3–8 seconds on a mid-range device (e.g., Samsung A15).
 *
 * @param modelPath Absolute path to the model directory (contains .cact weight files + config.json)
 * @param corpusDir Optional corpus directory for RAG (not used in MediAssist)
 * @param cacheIndex Whether to cache the index on disk
 * @return A non-zero native handle for subsequent inference calls
 * @throws RuntimeException if initialization fails (e.g., corrupt weights, OOM)
 */
fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long {
    val h = CactusJNI.nativeInit(modelPath, corpusDir, cacheIndex)
    if (h == 0L) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Failed to initialize model" })
    return h
}

/** Release all native resources held by the model. Must be called on app shutdown. */
fun cactusDestroy(model: Long) = CactusJNI.nativeDestroy(model)

/** Reset the model's conversation state (clears KV-cache, not the model weights). */
fun cactusReset(model: Long) = CactusJNI.nativeReset(model)

/**
 * Signal the native engine to stop the current inference immediately.
 * Used when the user presses the "Stop" button during token streaming.
 * The [cactusComplete] call will return with whatever tokens were generated so far.
 */
fun cactusStop(model: Long) = CactusJNI.nativeStop(model)

/** Retrieve the last error message from the native layer. */
fun cactusGetLastError(): String = CactusJNI.nativeGetLastError()

/** Configure the telemetry cache directory (required by Cactus SDK). */
fun cactusSetTelemetryEnvironment(cacheDir: String) = CactusJNI.nativeSetCacheDir(cacheDir)

/** Set the application ID for telemetry tracking. */
fun cactusSetAppId(appId: String) = CactusJNI.nativeSetAppId(appId)

/** Flush any pending telemetry data. */
fun cactusTelemetryFlush() = CactusJNI.nativeTelemetryFlush()

/** Shut down the telemetry subsystem. */
fun cactusTelemetryShutdown() = CactusJNI.nativeTelemetryShutdown()

/**
 * Run a chat completion against the loaded Gemma 4 E2B model.
 *
 * This is the core inference function used by both the chat and scan features.
 * It accepts an OpenAI-compatible messages JSON array and streams tokens via [callback].
 *
 * @param model Native handle from [cactusInit]
 * @param messagesJson JSON array: [{"role":"system","content":"..."},{"role":"user","content":"..."}]
 * @param optionsJson Optional generation config (temperature, top_k, max_tokens)
 * @param toolsJson Optional tool definitions for function calling (unused in MediAssist)
 * @param callback Token-by-token streaming callback; each invocation delivers one token
 * @return The complete generated text (concatenation of all streamed tokens)
 */
fun cactusComplete(model: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray? = null): String =
    CactusJNI.nativeComplete(model, messagesJson, optionsJson, toolsJson, callback, pcmData)

/** Pre-fill the KV cache with a conversation prefix without generating output. */
fun cactusPrefill(model: Long, messagesJson: String, optionsJson: String?, toolsJson: String?, pcmData: ByteArray? = null): String =
    CactusJNI.nativePrefill(model, messagesJson, optionsJson, toolsJson, pcmData)

/** Transcribe audio to text using the model's speech recognition capabilities. */
fun cactusTranscribe(model: Long, audioPath: String?, prompt: String?, optionsJson: String?, callback: CactusTokenCallback?, pcmData: ByteArray?): String =
    CactusJNI.nativeTranscribe(model, audioPath, prompt, optionsJson, callback, pcmData)

/** Generate text embeddings for semantic similarity tasks. */
fun cactusEmbed(model: Long, text: String, normalize: Boolean): FloatArray =
    CactusJNI.nativeEmbed(model, text, normalize)

/** Generate image embeddings for multimodal tasks. */
fun cactusImageEmbed(model: Long, imagePath: String): FloatArray =
    CactusJNI.nativeImageEmbed(model, imagePath)

/** Generate audio embeddings. */
fun cactusAudioEmbed(model: Long, audioPath: String): FloatArray =
    CactusJNI.nativeAudioEmbed(model, audioPath)

/** Perform voice activity detection on audio data. */
fun cactusVad(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String =
    CactusJNI.nativeVad(model, audioPath, optionsJson, pcmData)

/** Perform speaker diarization on audio data. */
fun cactusDiarize(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String =
    CactusJNI.nativeDiarize(model, audioPath, optionsJson, pcmData)

/** Generate speaker embeddings for identification tasks. */
fun cactusEmbedSpeaker(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?, maskWeights: FloatArray? = null): String =
    CactusJNI.nativeEmbedSpeaker(model, audioPath, optionsJson, pcmData, maskWeights)

/** Query a pre-built RAG index with a text query. */
fun cactusRagQuery(model: Long, query: String, topK: Int): String =
    CactusJNI.nativeRagQuery(model, query, topK)

/** Tokenize text into token IDs using the model's tokenizer. */
fun cactusTokenize(model: Long, text: String): IntArray =
    CactusJNI.nativeTokenize(model, text)

/** Score a window of tokens for perplexity analysis. */
fun cactusScoreWindow(model: Long, tokens: IntArray, start: Int, end: Int, context: Int): String =
    CactusJNI.nativeScoreWindow(model, tokens, start, end, context)

/**
 * Initialize a streaming transcription session.
 * @return A session handle for subsequent process/stop calls
 * @throws RuntimeException if session creation fails
 */
fun cactusStreamTranscribeStart(model: Long, optionsJson: String?): Long {
    val h = CactusJNI.nativeStreamTranscribeInit(model, optionsJson)
    if (h == 0L) throw RuntimeException(CactusJNI.nativeGetLastError().ifEmpty { "Failed to create stream transcriber" })
    return h
}

/** Feed PCM audio chunks to an active streaming transcription session. */
fun cactusStreamTranscribeProcess(stream: Long, pcmData: ByteArray): String =
    CactusJNI.nativeStreamTranscribeProcess(stream, pcmData)

/** End a streaming transcription session and get the final result. */
fun cactusStreamTranscribeStop(stream: Long): String =
    CactusJNI.nativeStreamTranscribeStop(stream)

/**
 * Initialize a vector index for RAG (Retrieval-Augmented Generation).
 * @throws RuntimeException if index creation fails
 */
fun cactusIndexInit(indexDir: String, embeddingDim: Int): Long {
    val h = CactusJNI.nativeIndexInit(indexDir, embeddingDim)
    if (h == 0L) throw RuntimeException("Failed to initialize index")
    return h
}

/** Add documents with embeddings to the vector index. */
fun cactusIndexAdd(index: Long, ids: IntArray, documents: Array<String>, embeddings: Array<FloatArray>, metadatas: Array<String>?): Int =
    CactusJNI.nativeIndexAdd(index, ids, documents, metadatas, embeddings, embeddings[0].size.toLong())

/** Delete documents from the vector index by ID. */
fun cactusIndexDelete(index: Long, ids: IntArray): Int =
    CactusJNI.nativeIndexDelete(index, ids)

/** Retrieve documents from the vector index by ID. */
fun cactusIndexGet(index: Long, ids: IntArray): String =
    CactusJNI.nativeIndexGet(index, ids)

/** Query the vector index for nearest neighbors. */
fun cactusIndexQuery(index: Long, embedding: FloatArray, optionsJson: String?): String =
    CactusJNI.nativeIndexQuery(index, embedding, 1000L, optionsJson)

/** Compact the vector index to reclaim space from deleted documents. */
fun cactusIndexCompact(index: Long): Int =
    CactusJNI.nativeIndexCompact(index)

/** Destroy a vector index and release its resources. */
fun cactusIndexDestroy(index: Long) =
    CactusJNI.nativeIndexDestroy(index)

/** Detect the spoken language in an audio file or PCM data. */
fun cactusDetectLanguage(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String =
    CactusJNI.nativeDetectLanguage(model, audioPath, optionsJson, pcmData)

/** Set the native logging verbosity level. */
fun cactusLogSetLevel(level: Int) =
    CactusJNI.nativeLogSetLevel(level)

/** Set a callback for receiving native log messages in Kotlin. */
fun cactusLogSetCallback(callback: CactusLogCallback?) =
    CactusJNI.nativeLogSetCallback(callback)
