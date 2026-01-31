package com.matrix.multigpt.data.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped singleton to track download state.
 * Persists across navigation because it's a Singleton.
 */
@Singleton
class LocalModelDownloadState @Inject constructor() {
    
    private val _downloadingModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadingModels: StateFlow<Set<String>> = _downloadingModels.asStateFlow()

    private val _downloadProgressMap = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Float>> = _downloadProgressMap.asStateFlow()

    private val _downloadedBytesMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val downloadedBytesMap: StateFlow<Map<String, Long>> = _downloadedBytesMap.asStateFlow()

    private val _totalBytesMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val totalBytesMap: StateFlow<Map<String, Long>> = _totalBytesMap.asStateFlow()

    fun startDownload(modelId: String, totalSize: Long) {
        _downloadingModels.value = _downloadingModels.value + modelId
        _downloadProgressMap.value = _downloadProgressMap.value + (modelId to 0f)
        _totalBytesMap.value = _totalBytesMap.value + (modelId to totalSize)
        _downloadedBytesMap.value = _downloadedBytesMap.value + (modelId to 0L)
    }

    fun updateProgress(modelId: String, progress: Float, downloadedBytes: Long, totalBytes: Long) {
        _downloadProgressMap.value = _downloadProgressMap.value + (modelId to progress)
        _downloadedBytesMap.value = _downloadedBytesMap.value + (modelId to downloadedBytes)
        if (totalBytes > 0) {
            _totalBytesMap.value = _totalBytesMap.value + (modelId to totalBytes)
        }
    }

    fun completeDownload(modelId: String) {
        _downloadingModels.value = _downloadingModels.value - modelId
        _downloadProgressMap.value = _downloadProgressMap.value - modelId
        _downloadedBytesMap.value = _downloadedBytesMap.value - modelId
        _totalBytesMap.value = _totalBytesMap.value - modelId
    }

    fun isDownloading(modelId: String): Boolean {
        return _downloadingModels.value.contains(modelId)
    }

    fun getProgress(modelId: String): Float {
        return _downloadProgressMap.value[modelId] ?: 0f
    }
}
