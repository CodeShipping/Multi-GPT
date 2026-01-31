package com.matrix.multigpt.localinference.data.source

import android.content.Context
import com.matrix.multigpt.localinference.data.model.LocalModel
import com.matrix.multigpt.localinference.data.model.LocalModelState
import com.matrix.multigpt.localinference.data.model.ModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model file downloads with progress tracking, pause/resume support.
 * Framework-agnostic - can be used with any UI framework.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    // Track download states
    private val _modelStates = MutableStateFlow<Map<String, LocalModelState>>(emptyMap())
    val modelStates: StateFlow<Map<String, LocalModelState>> = _modelStates.asStateFlow()

    // Active download jobs
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    
    // Pause flags
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()

    init {
        // Initialize states for existing downloaded models
        CoroutineScope(Dispatchers.IO).launch {
            scanDownloadedModels()
        }
    }

    /**
     * Get the state of a specific model.
     */
    fun getModelState(modelId: String): Flow<LocalModelState?> {
        return modelStates.map { it[modelId] }
    }

    /**
     * Start downloading a model.
     */
    suspend fun downloadModel(model: LocalModel, scope: CoroutineScope) {
        val modelId = model.id
        
        // Check if already downloading
        if (downloadJobs.containsKey(modelId)) {
            return
        }

        // Reset pause flag
        pauseFlags[modelId] = false

        val job = scope.launch(Dispatchers.IO) {
            try {
                updateState(modelId, ModelStatus.DOWNLOADING)
                
                val outputFile = getModelFile(model)
                val tempFile = File(outputFile.absolutePath + ".tmp")
                
                // Check for partial download (resume support)
                var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
                
                // Make HTTP request with range header for resume
                httpClient.prepareGet(model.downloadUrl) {
                    if (downloadedBytes > 0) {
                        header(HttpHeaders.Range, "bytes=$downloadedBytes-")
                    }
                }.execute { response ->
                    val contentLength = response.contentLength() ?: model.size
                    val totalBytes = if (downloadedBytes > 0) {
                        downloadedBytes + contentLength
                    } else {
                        contentLength
                    }

                    updateState(
                        modelId = modelId,
                        status = ModelStatus.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                    )

                    val channel = response.bodyAsChannel()
                    val file = RandomAccessFile(tempFile, "rw")
                    
                    try {
                        file.seek(downloadedBytes)
                        val buffer = ByteArray(8192)
                        
                        while (!channel.isClosedForRead) {
                            // Check for pause
                            if (pauseFlags[modelId] == true) {
                                updateState(
                                    modelId = modelId,
                                    status = ModelStatus.PAUSED,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                                return@execute
                            }

                            // Check for cancellation
                            if (!isActive) {
                                throw kotlinx.coroutines.CancellationException("Download cancelled")
                            }

                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead > 0) {
                                file.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                
                                updateState(
                                    modelId = modelId,
                                    status = ModelStatus.DOWNLOADING,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                        
                        // Download complete - rename temp file
                        file.close()
                        if (downloadedBytes >= totalBytes - 1) {
                            tempFile.renameTo(outputFile)
                            updateState(
                                modelId = modelId,
                                status = ModelStatus.DOWNLOADED,
                                downloadedBytes = totalBytes,
                                totalBytes = totalBytes,
                                localPath = outputFile.absolutePath
                            )
                        }
                    } finally {
                        file.close()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled - don't update state, let cancel handler do it
                throw e
            } catch (e: Exception) {
                updateState(
                    modelId = modelId,
                    status = ModelStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
            } finally {
                downloadJobs.remove(modelId)
            }
        }
        
        downloadJobs[modelId] = job
    }

    /**
     * Pause an active download.
     */
    fun pauseDownload(modelId: String) {
        pauseFlags[modelId] = true
    }

    /**
     * Resume a paused download.
     */
    suspend fun resumeDownload(model: LocalModel, scope: CoroutineScope) {
        val currentState = _modelStates.value[model.id]
        if (currentState?.status == ModelStatus.PAUSED) {
            downloadModel(model, scope)
        }
    }

    /**
     * Cancel and optionally delete partial download.
     */
    fun cancelDownload(modelId: String, deletePartial: Boolean = true) {
        // Cancel the job
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        pauseFlags.remove(modelId)

        if (deletePartial) {
            // Delete temp file
            val tempFile = File(modelsDir, "$modelId.gguf.tmp")
            tempFile.delete()
        }

        updateState(modelId, ModelStatus.NOT_DOWNLOADED)
    }

    /**
     * Delete a downloaded model.
     */
    fun deleteModel(modelId: String) {
        // Delete both the model file and any temp file
        File(modelsDir, "$modelId.gguf").delete()
        File(modelsDir, "$modelId.gguf.tmp").delete()
        
        updateState(modelId, ModelStatus.NOT_DOWNLOADED)
    }

    /**
     * Get the local path of a downloaded model.
     */
    fun getModelPath(modelId: String): String? {
        val file = File(modelsDir, "$modelId.gguf")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return File(modelsDir, "$modelId.gguf").exists()
    }

    /**
     * Mark an imported model as downloaded (for models imported from external storage).
     */
    fun markAsDownloaded(modelId: String, localPath: String, fileSize: Long) {
        updateState(
            modelId = modelId,
            status = ModelStatus.DOWNLOADED,
            downloadedBytes = fileSize,
            totalBytes = fileSize,
            localPath = localPath
        )
    }

    /**
     * Get the file where the model will be stored.
     */
    private fun getModelFile(model: LocalModel): File {
        return File(modelsDir, "${model.id}.gguf")
    }

    /**
     * Scan for already downloaded models and update states.
     */
    private suspend fun scanDownloadedModels() {
        val states = mutableMapOf<String, LocalModelState>()
        
        modelsDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".gguf") -> {
                    val modelId = file.nameWithoutExtension
                    states[modelId] = LocalModelState(
                        modelId = modelId,
                        status = ModelStatus.DOWNLOADED,
                        downloadProgress = 1f,
                        downloadedBytes = file.length(),
                        totalBytes = file.length(),
                        localPath = file.absolutePath
                    )
                }
                file.name.endsWith(".gguf.tmp") -> {
                    val modelId = file.name.removeSuffix(".gguf.tmp")
                    if (!states.containsKey(modelId)) {
                        states[modelId] = LocalModelState(
                            modelId = modelId,
                            status = ModelStatus.PAUSED,
                            downloadedBytes = file.length(),
                            totalBytes = 0 // Unknown until we query again
                        )
                    }
                }
            }
        }
        
        _modelStates.value = states
    }

    /**
     * Update the state of a model.
     */
    private fun updateState(
        modelId: String,
        status: ModelStatus,
        downloadedBytes: Long = 0,
        totalBytes: Long = 0,
        localPath: String? = null,
        errorMessage: String? = null
    ) {
        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
        
        _modelStates.update { currentStates ->
            currentStates + (modelId to LocalModelState(
                modelId = modelId,
                status = status,
                downloadProgress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                localPath = localPath,
                errorMessage = errorMessage
            ))
        }
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        httpClient.close()
    }
}
