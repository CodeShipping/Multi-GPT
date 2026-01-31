package com.matrix.multigpt.presentation.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.matrix.multigpt.data.database.entity.ChatRoom
import com.matrix.multigpt.data.database.entity.Message
import com.matrix.multigpt.data.dto.ApiState
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.dto.ModelInfo
import com.matrix.multigpt.data.ModelConstants
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.network.ModelFetchService
import com.matrix.multigpt.data.repository.ChatRepository
import com.matrix.multigpt.data.repository.SettingRepository
import com.matrix.multigpt.util.handleStates
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val modelFetchService: ModelFetchService
) : ViewModel() {
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    val enabledPlatformsInChat = enabledPlatformString.split(',').map { s -> ApiType.valueOf(s) }
    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

    private val _chatRoom = MutableStateFlow<ChatRoom>(ChatRoom(id = -1, title = "", enabledPlatform = enabledPlatformsInChat))
    val chatRoom = _chatRoom.asStateFlow()

    private val _isChatTitleDialogOpen = MutableStateFlow(false)
    val isChatTitleDialogOpen = _isChatTitleDialogOpen.asStateFlow()

    private val _isEditQuestionDialogOpen = MutableStateFlow(false)
    val isEditQuestionDialogOpen = _isEditQuestionDialogOpen.asStateFlow()

    // Enabled platforms list
    private val _enabledPlatformsInApp = MutableStateFlow(listOf<ApiType>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()
    
    // Model selection state
    private val _currentModels = MutableStateFlow<Map<ApiType, String>>(emptyMap())
    val currentModels = _currentModels.asStateFlow()
    
    private val _fetchedModels = MutableStateFlow<Map<ApiType, List<ModelInfo>>>(emptyMap())
    val fetchedModels = _fetchedModels.asStateFlow()
    
    private val _modelFetchState = MutableStateFlow<Map<ApiType, ModelFetchResult>>(emptyMap())
    val modelFetchState = _modelFetchState.asStateFlow()

    // List of question & answers (User, Assistant)
    private val _messages = MutableStateFlow(listOf<Message>())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // User input used for TextField
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    // Used for passing user question to Edit User Message Dialog
    private val _editedQuestion = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val editedQuestion = _editedQuestion.asStateFlow()

    // Loading state for each platforms
    private val _openaiLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val openaiLoadingState = _openaiLoadingState.asStateFlow()

    private val _anthropicLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val anthropicLoadingState = _anthropicLoadingState.asStateFlow()

    private val _googleLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val googleLoadingState = _googleLoadingState.asStateFlow()

    private val _groqLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val groqLoadingState = _groqLoadingState.asStateFlow()

    private val _ollamaLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val ollamaLoadingState = _ollamaLoadingState.asStateFlow()

    private val _bedrockLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val bedrockLoadingState = _bedrockLoadingState.asStateFlow()

    private val _geminiNanoLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val geminiNanoLoadingState = _geminiNanoLoadingState.asStateFlow()

    // Total loading state. It should be updated if one of the loading state has changed.
    // If all loading states are idle, this value should have `true`.
    private val _isIdle = MutableStateFlow(true)
    val isIdle = _isIdle.asStateFlow()

    // State for the message loading state (From the database)
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    // Currently active(chat completion) user input. This is used when user input is sent.
    private val _userMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null, modelName = null))
    val userMessage = _userMessage.asStateFlow()

    // Currently active(chat completion) assistant output. This is used when data is received from the API.
    private val _openAIMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OPENAI, modelName = null))
    val openAIMessage = _openAIMessage.asStateFlow()

    private val _anthropicMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.ANTHROPIC, modelName = null))
    val anthropicMessage = _anthropicMessage.asStateFlow()

    private val _googleMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GOOGLE, modelName = null))
    val googleMessage = _googleMessage.asStateFlow()

    private val _groqMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GROQ, modelName = null))
    val groqMessage = _groqMessage.asStateFlow()

    private val _ollamaMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OLLAMA, modelName = null))
    val ollamaMessage = _ollamaMessage.asStateFlow()

    private val _bedrockMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.BEDROCK, modelName = null))
    val bedrockMessage = _bedrockMessage.asStateFlow()

    private val _geminiNanoMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null, modelName = null))
    val geminiNanoMessage = _geminiNanoMessage.asStateFlow()

    // Flows for assistant message streams
    private val openAIFlow = MutableSharedFlow<ApiState>()
    private val anthropicFlow = MutableSharedFlow<ApiState>()
    private val googleFlow = MutableSharedFlow<ApiState>()
    private val groqFlow = MutableSharedFlow<ApiState>()
    private val ollamaFlow = MutableSharedFlow<ApiState>()
    private val bedrockFlow = MutableSharedFlow<ApiState>()
    private val geminiNanoFlow = MutableSharedFlow<ApiState>()

    init {
        if (com.matrix.multigpt.BuildConfig.DEBUG) {
            android.util.Log.d("ViewModel", "$chatRoomId")
            android.util.Log.d("ViewModel", "$enabledPlatformsInChat")
        }
        fetchChatRoom()
        viewModelScope.launch { fetchMessages() }
        fetchEnabledPlatformsInApp()
        observeFlow()
    }

    fun askQuestion() {
        if (com.matrix.multigpt.BuildConfig.DEBUG) {
            android.util.Log.d("Question: ", _question.value)
        }
        _userMessage.update { it.copy(content = _question.value, createdAt = currentTimeStamp) }
        _question.update { "" }
        completeChat()
    }

    fun closeChatTitleDialog() = _isChatTitleDialogOpen.update { false }

    fun closeEditQuestionDialog() {
        _editedQuestion.update { Message(chatId = chatRoomId, content = "", platformType = null) }
        _isEditQuestionDialogOpen.update { false }
    }

    fun editQuestion(q: Message) {
        _messages.update { it.filter { message -> message.id < q.id && message.createdAt < q.createdAt } }
        _userMessage.update { it.copy(content = q.content, createdAt = currentTimeStamp) }
        completeChat()
    }

    fun openChatTitleDialog() = _isChatTitleDialogOpen.update { true }

    fun openEditQuestionDialog(question: Message) {
        _editedQuestion.update { question }
        _isEditQuestionDialogOpen.update { true }
    }

    fun generateDefaultChatTitle(): String? = chatRepository.generateDefaultChatTitle(_messages.value)

    fun generateAIChatTitle() {
        viewModelScope.launch {
            _geminiNanoLoadingState.update { LoadingState.Loading }
            _geminiNanoMessage.update { it.copy(content = "") }
        }
    }

    fun retryQuestion(message: Message) {
        val latestQuestionIndex = _messages.value.indexOfLast { it.platformType == null }

        if (latestQuestionIndex != -1 && _isIdle.value) {
            // Update user input to latest question
            _userMessage.update { _messages.value[latestQuestionIndex] }

            // Get previous answers from the assistant
            val previousAnswers = enabledPlatformsInChat.mapNotNull { apiType -> _messages.value.lastOrNull { it.platformType == apiType } }

            // Remove latest question & answers
            _messages.update { it - setOf(_messages.value[latestQuestionIndex]) - previousAnswers.toSet() }

            // Restore messages that are not retrying
            enabledPlatformsInChat.forEach { apiType ->
                when (apiType) {
                    message.platformType -> {}
                    else -> restoreMessageState(apiType, previousAnswers)
                }
            }
        }
        message.platformType?.let { updateLoadingState(it, LoadingState.Loading) }

        when (message.platformType) {
            ApiType.OPENAI -> {
                _openAIMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeOpenAIChat()
            }

            ApiType.ANTHROPIC -> {
                _anthropicMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeAnthropicChat()
            }

            ApiType.GOOGLE -> {
                _googleMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeGoogleChat()
            }

            ApiType.GROQ -> {
                _groqMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeGroqChat()
            }

            ApiType.OLLAMA -> {
                _ollamaMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeOllamaChat()
            }

            ApiType.BEDROCK -> {
                _bedrockMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp, modelName = message.modelName) }
                completeBedrockChat()
            }

            else -> {}
        }
    }

    fun updateChatTitle(title: String) {
        // Should be only used for changing chat title after the chatroom is created.
        if (_chatRoom.value.id > 0) {
            _chatRoom.update { it.copy(title = title) }
            viewModelScope.launch {
                chatRepository.updateChatTitle(_chatRoom.value, title)
            }
        }
    }

    fun exportChat(): Pair<String, String> {
        // Build the chat history in Markdown format
        val chatHistoryMarkdown = buildString {
            appendLine("# Chat Export: \"${chatRoom.value.title}\"")
            appendLine()
            appendLine("**Exported on:** ${formatCurrentDateTime()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Chat History")
            appendLine()
            messages.value.forEach { message ->
                val sender = if (message.platformType == null) "User" else "Assistant"
                appendLine("**$sender:**")
                appendLine(message.content)
                appendLine()
            }
        }

        // Save the Markdown file
        val fileName = "export_${chatRoom.value.title}_${System.currentTimeMillis()}.md"
        return Pair(fileName, chatHistoryMarkdown)
    }

    private fun formatCurrentDateTime(): String {
        val currentDate = java.util.Date()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault())
        return format.format(currentDate)
    }

    fun updateQuestion(q: String) = _question.update { q }

    private fun addMessage(message: Message) = _messages.update { it + listOf(message) }

    private fun clearQuestionAndAnswers() {
        _userMessage.update { it.copy(id = 0, content = "") }
        _openAIMessage.update { it.copy(id = 0, content = "") }
        _anthropicMessage.update { it.copy(id = 0, content = "") }
        _googleMessage.update { it.copy(id = 0, content = "") }
        _groqMessage.update { it.copy(id = 0, content = "") }
        _ollamaMessage.update { it.copy(id = 0, content = "") }
        _bedrockMessage.update { it.copy(id = 0, content = "") }
    }

    private fun completeChat() {
        enabledPlatformsInChat.forEach { apiType -> updateLoadingState(apiType, LoadingState.Loading) }
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            _openAIMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.OPENAI]) }
            completeOpenAIChat()
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            _anthropicMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.ANTHROPIC]) }
            completeAnthropicChat()
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            _googleMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.GOOGLE]) }
            completeGoogleChat()
        }

        if (ApiType.GROQ in enabledPlatforms) {
            _groqMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.GROQ]) }
            completeGroqChat()
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            _ollamaMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.OLLAMA]) }
            completeOllamaChat()
        }

        if (ApiType.BEDROCK in enabledPlatforms) {
            _bedrockMessage.update { it.copy(content = "", createdAt = currentTimeStamp, modelName = currentModels.value[ApiType.BEDROCK]) }
            completeBedrockChat()
        }
    }

    private fun completeAnthropicChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeAnthropicChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> anthropicFlow.emit(chunk) }
        }
    }

    private fun completeGoogleChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGoogleChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> googleFlow.emit(chunk) }
        }
    }

    private fun completeGroqChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGroqChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> groqFlow.emit(chunk) }
        }
    }

    private fun completeOllamaChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOllamaChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> ollamaFlow.emit(chunk) }
        }
    }

    private fun completeOpenAIChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOpenAIChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> openAIFlow.emit(chunk) }
        }
    }

    private fun completeBedrockChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeBedrockChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> bedrockFlow.emit(chunk) }
        }
    }

    private suspend fun fetchMessages() {
        // If the room isn't new
        if (chatRoomId != 0) {
            _messages.update { chatRepository.fetchMessages(chatRoomId) }
            _isLoaded.update { true } // Finish fetching
            return
        }

        // When message id should sync after saving chats
        if (_chatRoom.value.id != 0) {
            _messages.update { chatRepository.fetchMessages(_chatRoom.value.id) }
            return
        }
    }

    private fun fetchChatRoom() {
        viewModelScope.launch {
            _chatRoom.update {
                if (chatRoomId == 0) {
                    ChatRoom(id = 0, title = "Untitled Chat", enabledPlatform = enabledPlatformsInChat)
                } else {
                    chatRepository.fetchChatList().first { it.id == chatRoomId }
                }
            }
            if (com.matrix.multigpt.BuildConfig.DEBUG) {
                android.util.Log.d("ViewModel", "chatroom: $chatRoom")
            }
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            val enabled = platforms.filter { it.enabled }.map { it.name }
            _enabledPlatformsInApp.update { enabled }
            
            // Fetch current models for all enabled platforms
            platforms.filter { it.enabled }.forEach { platform ->
                platform.model?.let { model ->
                    _currentModels.update { it + (platform.name to model) }
                }
            }
        }
    }
    
    fun fetchModelsForProvider(apiType: ApiType) {
        viewModelScope.launch {
            _modelFetchState.update { it + (apiType to ModelFetchResult.Loading) }
            
            val platforms = settingRepository.fetchPlatforms()
            val platform = platforms.find { it.name == apiType }
            
            val apiUrl = platform?.apiUrl ?: ModelConstants.getDefaultAPIUrl(apiType)
            val apiKey = platform?.token ?: ""
            
            val result = modelFetchService.fetchModels(apiType, apiUrl, apiKey)
            _modelFetchState.update { it + (apiType to result) }
            
            if (result is ModelFetchResult.Success) {
                _fetchedModels.update { it + (apiType to result.models) }
            }
        }
    }
    
    fun updateSelectedModel(apiType: ApiType, model: String) {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            val updatedPlatforms = platforms.map { platform ->
                if (platform.name == apiType) {
                    platform.copy(model = model)
                } else {
                    platform
                }
            }
            settingRepository.updatePlatforms(updatedPlatforms)
            _currentModels.update { it + (apiType to model) }
        }
    }

    private fun observeFlow() {
        viewModelScope.launch {
            openAIFlow.handleStates(
                messageFlow = _openAIMessage,
                onLoadingComplete = { updateLoadingState(ApiType.OPENAI, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            anthropicFlow.handleStates(
                messageFlow = _anthropicMessage,
                onLoadingComplete = { updateLoadingState(ApiType.ANTHROPIC, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            googleFlow.handleStates(
                messageFlow = _googleMessage,
                onLoadingComplete = { updateLoadingState(ApiType.GOOGLE, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            groqFlow.handleStates(
                messageFlow = _groqMessage,
                onLoadingComplete = { updateLoadingState(ApiType.GROQ, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            ollamaFlow.handleStates(
                messageFlow = _ollamaMessage,
                onLoadingComplete = { updateLoadingState(ApiType.OLLAMA, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            bedrockFlow.handleStates(
                messageFlow = _bedrockMessage,
                onLoadingComplete = { updateLoadingState(ApiType.BEDROCK, LoadingState.Idle) }
            )
        }

        viewModelScope.launch {
            geminiNanoFlow.handleStates(
                messageFlow = _geminiNanoMessage,
                onLoadingComplete = { _geminiNanoLoadingState.update { LoadingState.Idle } }
            )
        }

        viewModelScope.launch {
            _isIdle.collect { status ->
                if (status) {
                    if (com.matrix.multigpt.BuildConfig.DEBUG) {
                        android.util.Log.d("status", "val: ${_userMessage.value}")
                    }
                    if (_chatRoom.value.id != -1 && _userMessage.value.content.isNotBlank()) {
                        syncQuestionAndAnswers()
                        if (com.matrix.multigpt.BuildConfig.DEBUG) {
                            android.util.Log.d("message", "${_messages.value}")
                        }
                        _chatRoom.update { chatRepository.saveChat(_chatRoom.value, _messages.value) }
                        fetchMessages() // For syncing message ids
                    }
                    clearQuestionAndAnswers()
                }
            }
        }
    }

    private fun restoreMessageState(apiType: ApiType, previousAnswers: List<Message>) {
        val message = previousAnswers.firstOrNull { it.platformType == apiType }
        val retryingState = when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState
            ApiType.ANTHROPIC -> _anthropicLoadingState
            ApiType.GOOGLE -> _googleLoadingState
            ApiType.GROQ -> _groqLoadingState
            ApiType.OLLAMA -> _ollamaLoadingState
            ApiType.BEDROCK -> _bedrockLoadingState
            ApiType.LOCAL -> _ollamaLoadingState // TODO: Add _localLoadingState
        }

        if (retryingState == LoadingState.Loading) return
        if (message == null) return

        when (apiType) {
            ApiType.OPENAI -> _openAIMessage.update { message }
            ApiType.ANTHROPIC -> _anthropicMessage.update { message }
            ApiType.GOOGLE -> _googleMessage.update { message }
            ApiType.GROQ -> _groqMessage.update { message }
            ApiType.OLLAMA -> _ollamaMessage.update { message }
            ApiType.BEDROCK -> _bedrockMessage.update { message }
            ApiType.LOCAL -> _ollamaMessage.update { message } // TODO: Add _localMessage
        }
    }

    private fun syncQuestionAndAnswers() {
        addMessage(_userMessage.value)
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            addMessage(_openAIMessage.value)
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            addMessage(_anthropicMessage.value)
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            addMessage(_googleMessage.value)
        }

        if (ApiType.GROQ in enabledPlatforms) {
            addMessage(_groqMessage.value)
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            addMessage(_ollamaMessage.value)
        }

        if (ApiType.BEDROCK in enabledPlatforms) {
            addMessage(_bedrockMessage.value)
        }
    }

    private fun updateLoadingState(apiType: ApiType, loadingState: LoadingState) {
        when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState.update { loadingState }
            ApiType.ANTHROPIC -> _anthropicLoadingState.update { loadingState }
            ApiType.GOOGLE -> _googleLoadingState.update { loadingState }
            ApiType.GROQ -> _groqLoadingState.update { loadingState }
            ApiType.OLLAMA -> _ollamaLoadingState.update { loadingState }
            ApiType.BEDROCK -> _bedrockLoadingState.update { loadingState }
            ApiType.LOCAL -> _ollamaLoadingState.update { loadingState } // TODO: Add _localLoadingState
        }

        var result = true
        enabledPlatformsInChat.forEach {
            val state = when (it) {
                ApiType.OPENAI -> _openaiLoadingState
                ApiType.ANTHROPIC -> _anthropicLoadingState
                ApiType.GOOGLE -> _googleLoadingState
                ApiType.GROQ -> _groqLoadingState
                ApiType.OLLAMA -> _ollamaLoadingState
                ApiType.BEDROCK -> _bedrockLoadingState
                ApiType.LOCAL -> _ollamaLoadingState // TODO: Add _localLoadingState
            }

            result = result && (state.value is LoadingState.Idle)
        }

        _isIdle.update { result }
    }
}
