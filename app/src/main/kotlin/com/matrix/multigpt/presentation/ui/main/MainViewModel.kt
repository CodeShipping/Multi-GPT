package com.matrix.multigpt.presentation.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.matrix.multigpt.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: SettingRepository
) : ViewModel() {

    sealed class SplashEvent {
        data object OpenIntro : SplashEvent()
        data object OpenHome : SplashEvent()
    }

    private val _isReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _event: MutableSharedFlow<SplashEvent> = MutableSharedFlow()
    val event: SharedFlow<SplashEvent> = _event.asSharedFlow()

    init {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            
            // Also check if LOCAL is enabled via SharedPreferences
            val localPrefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
            val localEnabled = localPrefs.getBoolean("local_enabled", false)

            if (platforms.all { it.enabled.not() } && !localEnabled) {
                // No platforms enabled - show intro
                sendSplashEvent(SplashEvent.OpenIntro)
            } else {
                // At least one platform (or LOCAL) is enabled - go to home
                sendSplashEvent(SplashEvent.OpenHome)
            }

            setAsReady()
        }
    }

    private suspend fun sendSplashEvent(event: SplashEvent) {
        _event.emit(event)
    }

    private fun setAsReady() {
        _isReady.update { true }
    }
}
