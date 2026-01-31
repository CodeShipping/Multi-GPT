package com.matrix.multigpt.util

import android.app.Activity
import android.content.Context
import com.google.android.play.core.splitinstall.*
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for Play Feature Delivery on-demand module installation.
 */
@Singleton
class FeatureDeliveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(context)

    companion object {
        const val LOCAL_INFERENCE_MODULE = "localinference"
    }

    /**
     * Check if a feature module is installed.
     */
    fun isModuleInstalled(moduleName: String): Boolean {
        return splitInstallManager.installedModules.contains(moduleName)
    }

    /**
     * Check if local inference module is installed.
     */
    fun isLocalInferenceInstalled(): Boolean {
        return isModuleInstalled(LOCAL_INFERENCE_MODULE)
    }

    /**
     * Request installation of a feature module.
     * Returns a Flow that emits installation progress.
     */
    fun installModule(moduleName: String): Flow<FeatureInstallState> = callbackFlow {
        val request = SplitInstallRequest.newBuilder()
            .addModule(moduleName)
            .build()

        val listener = SplitInstallStateUpdatedListener { state ->
            val installState = when (state.status()) {
                SplitInstallSessionStatus.PENDING -> FeatureInstallState.Pending
                SplitInstallSessionStatus.DOWNLOADING -> {
                    val progress = if (state.totalBytesToDownload() > 0) {
                        state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                    } else 0f
                    FeatureInstallState.Downloading(
                        bytesDownloaded = state.bytesDownloaded(),
                        totalBytes = state.totalBytesToDownload(),
                        progress = progress
                    )
                }
                SplitInstallSessionStatus.DOWNLOADED -> FeatureInstallState.Downloaded
                SplitInstallSessionStatus.INSTALLING -> FeatureInstallState.Installing
                SplitInstallSessionStatus.INSTALLED -> FeatureInstallState.Installed
                SplitInstallSessionStatus.FAILED -> FeatureInstallState.Failed(
                    errorCode = state.errorCode(),
                    message = getErrorMessage(state.errorCode())
                )
                SplitInstallSessionStatus.CANCELING -> FeatureInstallState.Canceling
                SplitInstallSessionStatus.CANCELED -> FeatureInstallState.Canceled
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    FeatureInstallState.RequiresConfirmation(state)
                }
                else -> FeatureInstallState.Unknown(state.status())
            }
            trySend(installState)
            
            // Close the flow on terminal states
            if (state.status() == SplitInstallSessionStatus.INSTALLED ||
                state.status() == SplitInstallSessionStatus.FAILED ||
                state.status() == SplitInstallSessionStatus.CANCELED) {
                close()
            }
        }

        splitInstallManager.registerListener(listener)

        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                trySend(FeatureInstallState.Started(sessionId))
            }
            .addOnFailureListener { exception ->
                trySend(FeatureInstallState.Failed(
                    errorCode = -1,
                    message = exception.message ?: "Unknown error"
                ))
                close()
            }

        awaitClose {
            splitInstallManager.unregisterListener(listener)
        }
    }

    /**
     * Install local inference module.
     */
    fun installLocalInference(): Flow<FeatureInstallState> {
        return installModule(LOCAL_INFERENCE_MODULE)
    }

    /**
     * Start user confirmation activity for large downloads.
     */
    fun startConfirmationActivity(
        activity: Activity,
        state: SplitInstallSessionState,
        requestCode: Int
    ) {
        splitInstallManager.startConfirmationDialogForResult(state, activity, requestCode)
    }

    /**
     * Cancel an ongoing installation.
     */
    suspend fun cancelInstall(sessionId: Int): Boolean = suspendCancellableCoroutine { continuation ->
        splitInstallManager.cancelInstall(sessionId)
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { continuation.resume(false) }
    }

    /**
     * Uninstall a feature module (deferred).
     */
    suspend fun uninstallModule(moduleName: String): Boolean = suspendCancellableCoroutine { continuation ->
        splitInstallManager.deferredUninstall(listOf(moduleName))
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { continuation.resume(false) }
    }

    /**
     * Get currently installed modules.
     */
    fun getInstalledModules(): Set<String> {
        return splitInstallManager.installedModules
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SplitInstallErrorCode.NO_ERROR -> "No error"
            SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> "Active sessions limit exceeded"
            SplitInstallErrorCode.MODULE_UNAVAILABLE -> "Module unavailable"
            SplitInstallErrorCode.INVALID_REQUEST -> "Invalid request"
            SplitInstallErrorCode.SESSION_NOT_FOUND -> "Session not found"
            SplitInstallErrorCode.API_NOT_AVAILABLE -> "API not available"
            SplitInstallErrorCode.NETWORK_ERROR -> "Network error"
            SplitInstallErrorCode.ACCESS_DENIED -> "Access denied"
            SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION -> "Incompatible with existing session"
            SplitInstallErrorCode.SERVICE_DIED -> "Service died"
            SplitInstallErrorCode.INSUFFICIENT_STORAGE -> "Insufficient storage"
            SplitInstallErrorCode.SPLITCOMPAT_VERIFICATION_ERROR -> "SplitCompat verification error"
            SplitInstallErrorCode.SPLITCOMPAT_EMULATION_ERROR -> "SplitCompat emulation error"
            SplitInstallErrorCode.SPLITCOMPAT_COPY_ERROR -> "SplitCompat copy error"
            SplitInstallErrorCode.PLAY_STORE_NOT_FOUND -> "Play Store not found"
            SplitInstallErrorCode.APP_NOT_OWNED -> "App not owned"
            else -> "Unknown error: $errorCode"
        }
    }
}

/**
 * Represents the installation state of a feature module.
 */
sealed class FeatureInstallState {
    data object Pending : FeatureInstallState()
    data class Started(val sessionId: Int) : FeatureInstallState()
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : FeatureInstallState()
    data object Downloaded : FeatureInstallState()
    data object Installing : FeatureInstallState()
    data object Installed : FeatureInstallState()
    data class Failed(val errorCode: Int, val message: String) : FeatureInstallState()
    data object Canceling : FeatureInstallState()
    data object Canceled : FeatureInstallState()
    data class RequiresConfirmation(val state: SplitInstallSessionState) : FeatureInstallState()
    data class Unknown(val status: Int) : FeatureInstallState()
}
