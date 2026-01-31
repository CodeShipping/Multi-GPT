package com.matrix.multigpt.presentation.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.matrix.multigpt.data.database.entity.ChatRoom
import com.matrix.multigpt.data.dto.Platform
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.repository.ChatRepository
import com.matrix.multigpt.data.repository.SettingRepository
import com.matrix.multigpt.util.FirebaseEvents
import com.matrix.multigpt.util.FirebaseManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val firebaseManager: FirebaseManager
) : ViewModel() {

    data class ChatListState(
        val chats: List<ChatRoom> = listOf(),
        val isSelectionMode: Boolean = false,
        val selected: List<Boolean> = listOf(),
        val usedPlatformsMap: Map<Int, List<ApiType>> = emptyMap() // Actually used platforms per chat
    )

    private val _chatListState = MutableStateFlow(ChatListState())
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<Platform>())
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()
    
    // Track if platforms have been loaded
    private val _isPlatformsLoaded = MutableStateFlow(false)
    val isPlatformsLoaded: StateFlow<Boolean> = _isPlatformsLoaded.asStateFlow()

    private val _showSelectModelDialog = MutableStateFlow(false)
    val showSelectModelDialog: StateFlow<Boolean> = _showSelectModelDialog.asStateFlow()

    private val _showDeleteWarningDialog = MutableStateFlow(false)
    val showDeleteWarningDialog: StateFlow<Boolean> = _showDeleteWarningDialog.asStateFlow()

    init {
        // Load platforms immediately when ViewModel is created
        fetchPlatformStatus()
    }

    fun updateCheckedState(platform: Platform) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(selected = p.selected.not())
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun openDeleteWarningDialog() {
        closeSelectModelDialog()
        _showDeleteWarningDialog.update { true }
    }

    fun closeDeleteWarningDialog() {
        _showDeleteWarningDialog.update { false }
    }

    fun openSelectModelDialog() {
        // Log platform selection dialog opening
        firebaseManager.logUserAction(FirebaseEvents.ACTION_NEW_CHAT)
        
        _showSelectModelDialog.update { true }
        disableSelectionMode()
    }

    fun closeSelectModelDialog() {
        _showSelectModelDialog.update { false }
    }

    fun deleteSelectedChats() {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selected[index]
            }

            // Log delete action to Firebase
            firebaseManager.logUserAction(FirebaseEvents.ACTION_DELETE_CHAT, selectedChats.size.toString())

            chatRepository.deleteChats(selectedChats)
            _chatListState.update { it.copy(chats = chatRepository.fetchChatList()) }
            disableSelectionMode()
        }
    }

    fun disableSelectionMode() {
        _chatListState.update {
            it.copy(
                selected = List(it.chats.size) { false },
                isSelectionMode = false
            )
        }
    }

    fun enableSelectionMode() {
        _chatListState.update { it.copy(isSelectionMode = true) }
    }

    fun fetchChats() {
        viewModelScope.launch {
            val chats = chatRepository.fetchChatList()
            
            // Fetch actually used platforms for all chats
            val usedPlatformsMap = chatRepository.getAllChatsUsedPlatforms()

            _chatListState.update {
                it.copy(
                    chats = chats,
                    selected = List(chats.size) { false },
                    isSelectionMode = false,
                    usedPlatformsMap = usedPlatformsMap
                )
            }

            Log.d("chats", "${_chatListState.value.chats}")
            Log.d("HomeViewModel", "Used platforms per chat: $usedPlatformsMap")
        }
    }

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            _isPlatformsLoaded.update { false }
            val platforms = settingRepository.fetchPlatforms().toMutableList()
            
            // Check if LOCAL is enabled via SharedPreferences (model selected)
            val localPrefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
            val localEnabled = localPrefs.getBoolean("local_enabled", false)
            val selectedModelName = localPrefs.getString("selected_model_name", null)
            
            // Find LOCAL platform and update its enabled status
            val localIndex = platforms.indexOfFirst { it.name == ApiType.LOCAL }
            if (localIndex >= 0) {
                platforms[localIndex] = platforms[localIndex].copy(enabled = localEnabled)
            } else if (localEnabled) {
                // Add LOCAL platform if not present but enabled
                platforms.add(Platform(ApiType.LOCAL, enabled = true, selected = false))
            }
            
            _platformState.update { platforms }
            _isPlatformsLoaded.update { true }
            Log.d("HomeViewModel", "Platforms loaded: ${platforms.filter { it.enabled }.map { it.name }}, localEnabled=$localEnabled, model=$selectedModelName")
        }
    }

    fun selectChat(chatRoomIdx: Int) {
        if (chatRoomIdx < 0 || chatRoomIdx > _chatListState.value.chats.size) return

        _chatListState.update {
            it.copy(
                selected = it.selected.mapIndexed { index, b ->
                    if (index == chatRoomIdx) {
                        !b
                    } else {
                        b
                    }
                }
            )
        }

        if (_chatListState.value.selected.count { it } == 0) {
            disableSelectionMode()
        }
    }
}
