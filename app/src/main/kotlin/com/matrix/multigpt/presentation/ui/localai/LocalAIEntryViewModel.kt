package com.matrix.multigpt.presentation.ui.localai

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.matrix.multigpt.util.FeatureDeliveryManager
import com.matrix.multigpt.util.FeatureInstallState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Local AI entry screen.
 * Handles feature module installation state.
 */
@HiltViewModel
class LocalAIEntryViewModel @Inject constructor(
    private val featureDeliveryManager: FeatureDeliveryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalAIEntryUiState())
    val uiState: StateFlow<LocalAIEntryUiState> = _uiState.asStateFlow()

    private var currentSessionId: Int? = null

    companion object {
        const val CONFIRMATION_REQUEST_CODE = 1001
    }

    init {
        checkFeatureStatus()
    }

    /**
     * Check if feature is already installed.
     */
    private fun checkFeatureStatus() {
        _uiState.update { it.copy(isChecking = true) }
        
        val isInstalled = featureDeliveryManager.isLocalInferenceInstalled()
        
        _uiState.update { 
            it.copy(
                isChecking = false,
                isFeatureInstalled = isInstalled
            )
        }
    }

    /**
     * Start feature installation.
     */
    fun installFeature() {
        viewModelScope.launch {
            featureDeliveryManager.installLocalInference()
                .collect { state ->
                    _uiState.update { it.copy(installState = state) }
                    
                    when (state) {
                        is FeatureInstallState.Started -> {
                            currentSessionId = state.sessionId
                        }
                        is FeatureInstallState.Installed -> {
                            _uiState.update { it.copy(isFeatureInstalled = true) }
                        }
                        else -> { /* Handle other states in UI */ }
                    }
                }
        }
    }

    /**
     * Cancel ongoing installation.
     */
    fun cancelInstall() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                featureDeliveryManager.cancelInstall(sessionId)
            }
            _uiState.update { it.copy(installState = null) }
        }
    }

    /**
     * Request user confirmation for large downloads.
     */
    fun requestUserConfirmation(activity: Activity, state: SplitInstallSessionState) {
        featureDeliveryManager.startConfirmationActivity(
            activity,
            state,
            CONFIRMATION_REQUEST_CODE
        )
    }

    /**
     * Handle confirmation result from activity.
     */
    fun onConfirmationResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            // User confirmed, installation will continue automatically
        } else {
            // User cancelled
            _uiState.update { 
                it.copy(installState = FeatureInstallState.Canceled) 
            }
        }
    }

    /**
     * Refresh feature status (e.g., after returning from another activity).
     */
    fun refreshStatus() {
        checkFeatureStatus()
    }
}

/**
 * UI State for Local AI entry screen.
 */
data class LocalAIEntryUiState(
    val isChecking: Boolean = true,
    val isFeatureInstalled: Boolean = false,
    val installState: FeatureInstallState? = null
)
