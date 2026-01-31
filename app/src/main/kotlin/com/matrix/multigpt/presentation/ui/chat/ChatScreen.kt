package com.matrix.multigpt.presentation.ui.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.R
import com.matrix.multigpt.data.database.entity.Message
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.util.DefaultHashMap
import com.matrix.multigpt.util.multiScrollStateSaver
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onBackAction: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val packageManager = LocalContext.current.packageManager
    val systemChatMargin = 32.dp
    val maximumChatBubbleWidth = screenWidth - 48.dp - systemChatMargin
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val aiCorePackageInfo = try {
        packageManager.getPackageInfo("com.google.android.aicore", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    val privateComputePackageInfo = try {
        packageManager.getPackageInfo("com.google.android.as.oss", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val isChatTitleDialogOpen by chatViewModel.isChatTitleDialogOpen.collectAsStateWithLifecycle()
    val isEditQuestionDialogOpen by chatViewModel.isEditQuestionDialogOpen.collectAsStateWithLifecycle()
    val isIdle by chatViewModel.isIdle.collectAsStateWithLifecycle()
    val isLoaded by chatViewModel.isLoaded.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val question by chatViewModel.question.collectAsStateWithLifecycle()
    val appEnabledPlatforms by chatViewModel.enabledPlatformsInApp.collectAsStateWithLifecycle()
    val editedQuestion by chatViewModel.editedQuestion.collectAsStateWithLifecycle()
    val openaiLoadingState by chatViewModel.openaiLoadingState.collectAsStateWithLifecycle()
    val anthropicLoadingState by chatViewModel.anthropicLoadingState.collectAsStateWithLifecycle()
    val googleLoadingState by chatViewModel.googleLoadingState.collectAsStateWithLifecycle()
    val groqLoadingState by chatViewModel.groqLoadingState.collectAsStateWithLifecycle()
    val ollamaLoadingState by chatViewModel.ollamaLoadingState.collectAsStateWithLifecycle()
    val geminiNanoLoadingState by chatViewModel.geminiNanoLoadingState.collectAsStateWithLifecycle()
    val userMessage by chatViewModel.userMessage.collectAsStateWithLifecycle()
    val openAIMessage by chatViewModel.openAIMessage.collectAsStateWithLifecycle()
    val anthropicMessage by chatViewModel.anthropicMessage.collectAsStateWithLifecycle()
    val googleMessage by chatViewModel.googleMessage.collectAsStateWithLifecycle()
    val groqMessage by chatViewModel.groqMessage.collectAsStateWithLifecycle()
    val ollamaMessage by chatViewModel.ollamaMessage.collectAsStateWithLifecycle()
    val bedrockMessage by chatViewModel.bedrockMessage.collectAsStateWithLifecycle()
    val bedrockLoadingState by chatViewModel.bedrockLoadingState.collectAsStateWithLifecycle()
    val localMessage by chatViewModel.localMessage.collectAsStateWithLifecycle()
    val localLoadingState by chatViewModel.localLoadingState.collectAsStateWithLifecycle()
    val geminiNano by chatViewModel.geminiNanoMessage.collectAsStateWithLifecycle()
    val canUseChat = (chatViewModel.enabledPlatformsInChat.toSet() - appEnabledPlatforms.toSet()).isEmpty()
    val groupedMessages = remember(messages) { groupMessages(messages) }
    val latestMessageIndex = groupedMessages.keys.maxOrNull() ?: 0
    val chatBubbleScrollStates = rememberSaveable(saver = multiScrollStateSaver) { DefaultHashMap<Int, ScrollState> { ScrollState(0) } }
    val canEnableAICoreMode = rememberSaveable { checkAICoreAvailability(aiCorePackageInfo, privateComputePackageInfo) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // Model selection state
    val currentModels by chatViewModel.currentModels.collectAsStateWithLifecycle()
    val fetchedModels by chatViewModel.fetchedModels.collectAsStateWithLifecycle()
    val modelFetchState by chatViewModel.modelFetchState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    LaunchedEffect(isIdle) {
        listState.animateScrollToItem(groupedMessages.keys.size)
    }

    LaunchedEffect(isLoaded) {
        delay(300)
        listState.animateScrollToItem(groupedMessages.keys.size)
    }

    // Auto-scroll to bottom when keyboard opens
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            delay(100) // Small delay to let keyboard animation start
            listState.animateScrollToItem(groupedMessages.keys.size)
        }
    }

    if (com.matrix.multigpt.BuildConfig.DEBUG) {
        android.util.Log.d("AIPackage", "AICore: ${aiCorePackageInfo?.versionName ?: "Not installed"}, Private Compute Services: ${privateComputePackageInfo?.versionName ?: "Not installed"}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .imePadding()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() }
    ) {
        // Top Bar - always visible at top
        ChatTopBar(
            chatRoom.title,
            chatRoom.id > 0,
            onBackAction,
            scrollBehavior,
            chatViewModel::openChatTitleDialog,
            onExportChatItemClick = { exportChat(context, chatViewModel) },
            currentModels = currentModels,
            fetchedModels = fetchedModels,
            modelFetchState = modelFetchState,
            enabledProviders = appEnabledPlatforms,
            onFetchModels = chatViewModel::fetchModelsForProvider,
            onModelSelected = chatViewModel::updateSelectedModel
        )

        // Chat content - takes remaining space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (com.matrix.multigpt.BuildConfig.DEBUG) {
                groupedMessages.forEach { (i, k) -> android.util.Log.d("grouped", "idx: $i, data: $k") }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
            groupedMessages.keys.sorted().forEach { key ->
                if (key % 2 == 0) {
                    // User
                    item(key = key) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .animateItem()
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            UserChatBubble(
                                modifier = Modifier.widthIn(max = maximumChatBubbleWidth),
                                text = groupedMessages[key]!![0].content,
                                isLoading = !isIdle,
                                timestamp = groupedMessages[key]!![0].createdAt,
                                onCopyClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    clipboardManager.setText(AnnotatedString(groupedMessages[key]!![0].content.trim()))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onEditClick = { chatViewModel.openEditQuestionDialog(groupedMessages[key]!![0]) }
                            )
                        }
                    }
                } else {
                    // Assistant
                    item(key = key) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(chatBubbleScrollStates[(key - 1) / 2])
                                .animateItem()
                        ) {
                            Spacer(modifier = Modifier.width(8.dp))
                            groupedMessages[key]!!.sortedBy { it.platformType }.forEach { m ->
                                m.platformType?.let { apiType ->
                                    OpponentChatBubble(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 12.dp)
                                            .widthIn(max = maximumChatBubbleWidth),
                                        canRetry = canUseChat && isIdle && key >= latestMessageIndex,
                                        isLoading = false,
                                        apiType = apiType,
                                        text = m.content,
                                        timestamp = m.createdAt,
                                        modelName = m.modelName,
                                        onCopyClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setText(AnnotatedString(m.content.trim()))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onRetryClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            chatViewModel.retryQuestion(m)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(systemChatMargin))
                        }
                    }
                }
            }

            if (!isIdle) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        UserChatBubble(
                            modifier = Modifier.widthIn(max = maximumChatBubbleWidth),
                            text = userMessage.content,
                            isLoading = true,
                            onCopyClick = { clipboardManager.setText(AnnotatedString(userMessage.content.trim())) },
                            onEditClick = { chatViewModel.openEditQuestionDialog(userMessage) }
                        )
                    }
                }

                item(key = "loading_assistant") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chatBubbleScrollStates[(latestMessageIndex + 1) / 2])
                            .animateItem()
                    ) {
                            Spacer(modifier = Modifier.width(8.dp))
                            chatViewModel.enabledPlatformsInChat.sorted().forEach { apiType ->
                                val message = when (apiType) {
                                    ApiType.OPENAI -> openAIMessage
                                    ApiType.ANTHROPIC -> anthropicMessage
                                    ApiType.GOOGLE -> googleMessage
                                    ApiType.GROQ -> groqMessage
                                    ApiType.OLLAMA -> ollamaMessage
                                    ApiType.BEDROCK -> bedrockMessage
                                    ApiType.LOCAL -> localMessage
                                }

                                val loadingState = when (apiType) {
                                    ApiType.OPENAI -> openaiLoadingState
                                    ApiType.ANTHROPIC -> anthropicLoadingState
                                    ApiType.GOOGLE -> googleLoadingState
                                    ApiType.GROQ -> groqLoadingState
                                    ApiType.OLLAMA -> ollamaLoadingState
                                    ApiType.BEDROCK -> bedrockLoadingState
                                    ApiType.LOCAL -> localLoadingState
                                }

                                // Show typing indicator if loading and no content yet
                                if (loadingState == ChatViewModel.LoadingState.Loading && message.content.isEmpty()) {
                                    TypingIndicator(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                        apiType = apiType
                                    )
                                } else {
                                    OpponentChatBubble(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 12.dp)
                                            .widthIn(max = maximumChatBubbleWidth),
                                        canRetry = canUseChat,
                                        isLoading = loadingState == ChatViewModel.LoadingState.Loading,
                                        apiType = apiType,
                                        text = message.content,
                                        modelName = currentModels[apiType],
                                        onCopyClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setText(AnnotatedString(message.content.trim()))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onRetryClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            chatViewModel.retryQuestion(message)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(systemChatMargin))
                        }
                    }
                }
            }

            // Scroll to bottom FAB
            if (listState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ScrollToBottomButton {
                        scope.launch {
                            listState.animateScrollToItem(groupedMessages.keys.size)
                        }
                    }
                }
            }
        }

        // Bottom input box - always at bottom, above keyboard
        ChatInputBox(
            value = question,
            onValueChange = { s -> chatViewModel.updateQuestion(s) },
            chatEnabled = canUseChat,
            sendButtonEnabled = question.trim().isNotBlank() && isIdle
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            chatViewModel.askQuestion()
            focusManager.clearFocus()
        }
    }

    if (isChatTitleDialogOpen) {
        ChatTitleDialog(
            initialTitle = chatRoom.title,
            aiCoreModeEnabled = false,
            aiGeneratedResult = geminiNano.content,
            isAICoreLoading = geminiNanoLoadingState == ChatViewModel.LoadingState.Loading,
            onDefaultTitleMode = chatViewModel::generateDefaultChatTitle,
            onAICoreTitleMode = chatViewModel::generateAIChatTitle,
            onRetryRequest = chatViewModel::generateAIChatTitle,
            onConfirmRequest = { title -> chatViewModel.updateChatTitle(title) },
            onDismissRequest = chatViewModel::closeChatTitleDialog
        )
    }

    if (isEditQuestionDialogOpen) {
        ChatQuestionEditDialog(
            initialQuestion = editedQuestion,
            onDismissRequest = chatViewModel::closeEditQuestionDialog,
            onConfirmRequest = { question ->
                chatViewModel.editQuestion(question)
                chatViewModel.closeEditQuestionDialog()
            }
        )
    }
}

private fun checkAICoreAvailability(aiCore: PackageInfo?, privateComputeServices: PackageInfo?): Boolean {
    aiCore ?: return false
    privateComputeServices ?: return false
    val privateComputeMinVersion = "1.0.release.658389993"

    val aiCoreCondition = aiCore.versionName?.contains("thirdpartyeap") == true
    val privateComputeCondition = (privateComputeServices.versionName ?: "").padEnd(privateComputeMinVersion.length, '0') > privateComputeMinVersion

    return aiCoreCondition && privateComputeCondition
}

private fun groupMessages(messages: List<Message>): HashMap<Int, MutableList<Message>> {
    val classifiedMessages = hashMapOf<Int, MutableList<Message>>()
    var counter = 0

    messages.sortedBy { it.createdAt }.forEach { message ->
        if (message.platformType == null) {
            if (classifiedMessages.containsKey(counter) || counter % 2 == 1) {
                counter++
            }

            classifiedMessages[counter] = mutableListOf(message)
            counter++
        } else {
            if (counter % 2 == 0) {
                counter++
            }

            if (classifiedMessages.containsKey(counter)) {
                classifiedMessages[counter]?.add(message)
            } else {
                classifiedMessages[counter] = mutableListOf(message)
            }
        }
    }
    return classifiedMessages
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatTopBar(
    title: String,
    isMenuItemEnabled: Boolean,
    onBackAction: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    onChatTitleItemClick: () -> Unit,
    onExportChatItemClick: () -> Unit,
    currentModels: Map<ApiType, String>,
    fetchedModels: Map<ApiType, List<com.matrix.multigpt.data.dto.ModelInfo>>,
    modelFetchState: Map<ApiType, com.matrix.multigpt.data.dto.ModelFetchResult>,
    enabledProviders: List<ApiType>,
    onFetchModels: (ApiType) -> Unit,
    onModelSelected: (ApiType, String) -> Unit
) {
    var isDropDownMenuExpanded by remember { mutableStateOf(false) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<ApiType?>(null) }
    
    // Get current model and provider for display
    val firstProvider = enabledProviders.firstOrNull()
    val currentModel = firstProvider?.let { currentModels[it] } ?: "Select Model"
    val currentProviderName = firstProvider?.name ?: ""
    
    // Fetch models when provider is selected
    LaunchedEffect(selectedProvider) {
        selectedProvider?.let { provider ->
            if (fetchedModels[provider] == null) {
                onFetchModels(provider)
            }
        }
    }

    TopAppBar(
        title = { 
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Classy model selector dropdown
                Box {
                    Surface(
                        onClick = { 
                            isModelDropdownExpanded = true
                            selectedProvider = null // Reset to provider list
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = currentModel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentProviderName.isNotEmpty()) {
                                    Text(
                                        text = currentProviderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isModelDropdownExpanded) 
                                    Icons.Rounded.KeyboardArrowUp 
                                else 
                                    Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select Model",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Multi-level dropdown menu - centered
                    DropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { 
                            isModelDropdownExpanded = false
                            selectedProvider = null
                        },
                        offset = DpOffset(x = (-15).dp, y = 8.dp),
                        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                    ) {
                    if (selectedProvider == null) {
                        // Show providers
                        Text(
                            text = "Select Provider",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Divider()
                        
                        enabledProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(provider.name)
                                            currentModels[provider]?.let { model ->
                                                Text(
                                                    text = model,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .alpha(0.6f)
                                        )
                                    }
                                },
                                onClick = { selectedProvider = provider }
                            )
                        }
                    } else {
                        // Show models for selected provider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { selectedProvider = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${selectedProvider!!.name} Models",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Divider()
                        
                        val fetchState = modelFetchState[selectedProvider]
                        when (fetchState) {
                            is com.matrix.multigpt.data.dto.ModelFetchResult.Loading -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("Loading models...")
                                }
                            }
                            is com.matrix.multigpt.data.dto.ModelFetchResult.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Failed to load models",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    TextButton(onClick = { selectedProvider?.let { onFetchModels(it) } }) {
                                        Text("Retry")
                                    }
                                }
                            }
                            else -> {
                                val models = fetchedModels[selectedProvider] ?: emptyList()
                                if (models.isEmpty()) {
                                    Text(
                                        text = "No models available",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    models.forEach { modelInfo ->
                                        val isSelected = currentModels[selectedProvider] == modelInfo.id
                                        DropdownMenuItem(
                                            text = { 
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = modelInfo.name,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) 
                                                            MaterialTheme.colorScheme.primary 
                                                        else 
                                                            MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Done,
                                                            contentDescription = "Selected",
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = { 
                                                onModelSelected(selectedProvider!!, modelInfo.id)
                                                isModelDropdownExpanded = false
                                                selectedProvider = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBackAction
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(
                onClick = { isDropDownMenuExpanded = isDropDownMenuExpanded.not() }
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.options))
            }

            ChatDropdownMenu(
                isDropDownMenuExpanded = isDropDownMenuExpanded,
                isMenuItemEnabled = isMenuItemEnabled,
                onDismissRequest = { isDropDownMenuExpanded = false },
                onChatTitleItemClick = {
                    onChatTitleItemClick.invoke()
                    isDropDownMenuExpanded = false
                },
                onExportChatItemClick = onExportChatItemClick
            )
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ChatDropdownMenu(
    isDropDownMenuExpanded: Boolean,
    isMenuItemEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onChatTitleItemClick: () -> Unit,
    onExportChatItemClick: () -> Unit
) {
    DropdownMenu(
        modifier = Modifier.wrapContentSize(),
        expanded = isDropDownMenuExpanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            enabled = isMenuItemEnabled,
            text = { Text(text = stringResource(R.string.update_chat_title)) },
            onClick = onChatTitleItemClick
        )
        /* Export Chat */
        DropdownMenuItem(
            enabled = isMenuItemEnabled,
            text = { Text(text = stringResource(R.string.export_chat)) },
            onClick = {
                onExportChatItemClick()
                onDismissRequest()
            }
        )
    }
}

private fun exportChat(context: Context, chatViewModel: ChatViewModel) {
    try {
        val (fileName, fileContent) = chatViewModel.exportChat()
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(fileContent)
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Chat Export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfo = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        resInfo.forEach { res ->
            context.grantUriPermission(res.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        if (com.matrix.multigpt.BuildConfig.DEBUG) {
            android.util.Log.e("ChatExport", "Failed to export chat", e)
        }
        Toast.makeText(context, "Failed to export chat", Toast.LENGTH_SHORT).show()
    }
}

@Preview
@Composable
fun ChatInputBox(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    chatEnabled: Boolean = true,
    sendButtonEnabled: Boolean = true,
    onSendButtonClick: (String) -> Unit = {}
) {
    val localStyle = LocalTextStyle.current
    val mergedStyle = localStyle.merge(TextStyle(color = LocalContentColor.current))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface)
    ) {
        BasicTextField(
            modifier = Modifier
                .heightIn(max = 120.dp),
            value = value,
            enabled = chatEnabled,
            textStyle = mergedStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            onValueChange = { if (chatEnabled) onValueChange(it) },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(size = 24.dp))
                        .padding(all = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .padding(start = 16.dp)
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                modifier = Modifier.alpha(0.38f),
                                text = if (chatEnabled) stringResource(R.string.ask_a_question) else stringResource(R.string.some_platforms_disabled)
                            )
                        }
                        innerTextField()
                    }
                    IconButton(
                        enabled = chatEnabled && sendButtonEnabled,
                        onClick = { onSendButtonClick(value) }
                    ) {
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_send), contentDescription = stringResource(R.string.send))
                    }
                }
            }
        )
    }
}

@Composable
fun ScrollToBottomButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.scroll_to_bottom_icon))
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    apiType: ApiType
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 150)
                ),
                label = "dot$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}
