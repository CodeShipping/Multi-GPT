package com.matrix.multigpt.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.R
import com.matrix.multigpt.data.ModelConstants.anthropicModels
import com.matrix.multigpt.data.ModelConstants.bedrockModels
import com.matrix.multigpt.data.ModelConstants.getDefaultAPIUrl
import com.matrix.multigpt.data.ModelConstants.googleModels
import com.matrix.multigpt.data.ModelConstants.groqModels
import com.matrix.multigpt.data.ModelConstants.ollamaModels
import com.matrix.multigpt.data.ModelConstants.openaiModels
import com.matrix.multigpt.data.dto.APIModel
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.presentation.common.BedrockCredentialsField
import com.matrix.multigpt.presentation.common.RadioItem
import com.matrix.multigpt.presentation.common.TokenInputField
import com.matrix.multigpt.util.generateAnthropicModelList
import com.matrix.multigpt.util.generateBedrockModelList
import com.matrix.multigpt.util.generateGoogleModelList
import com.matrix.multigpt.util.generateGroqModelList
import com.matrix.multigpt.util.generateOpenAIModelList
import com.matrix.multigpt.util.getPlatformAPILabelResources
import com.matrix.multigpt.util.getPlatformHelpLinkResources
import com.matrix.multigpt.util.isValidUrl
import kotlin.math.roundToInt

@Composable
fun APIUrlDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    initialValue: String,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiUrlDialogOpen) {
        APIUrlDialog(
            apiType = apiType,
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closeApiUrlDialog,
            onResetRequest = {
                settingViewModel.updateURL(apiType, getDefaultAPIUrl(apiType))
                settingViewModel.savePlatformSettings()
                settingViewModel.closeApiUrlDialog()
            },
            onConfirmRequest = { apiUrl ->
                settingViewModel.updateURL(apiType, apiUrl)
                settingViewModel.savePlatformSettings()
                settingViewModel.closeApiUrlDialog()
            }
        )
    }
}

@Composable
fun APIKeyDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiTokenDialogOpen) {
        APIKeyDialog(
            apiType = apiType,
            onDismissRequest = settingViewModel::closeApiTokenDialog
        ) { apiToken ->
            settingViewModel.updateToken(apiType, apiToken)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeApiTokenDialog()
        }
    }
}

@Composable
fun ModelDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    model: String?,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isApiModelDialogOpen) {
        val modelFetchState by settingViewModel.modelFetchState.collectAsStateWithLifecycle()
        val fetchedModels by settingViewModel.fetchedModels.collectAsStateWithLifecycle()
        
        ModelDialog(
            apiType = apiType,
            initModel = model ?: "",
            modelFetchState = modelFetchState,
            fetchedModels = fetchedModels,
            onFetchModels = { settingViewModel.fetchModelsForPlatform(apiType) },
            onDismissRequest = settingViewModel::closeApiModelDialog
        ) { m ->
            settingViewModel.updateModel(apiType, m)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeApiModelDialog()
        }
    }
}

@Composable
fun TemperatureDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    temperature: Float,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isTemperatureDialogOpen) {
        TemperatureDialog(
            apiType = apiType,
            temperature = temperature,
            onDismissRequest = settingViewModel::closeTemperatureDialog
        ) { temp ->
            settingViewModel.updateTemperature(apiType, temp)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeTemperatureDialog()
        }
    }
}

@Composable
fun TopPDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    topP: Float?,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isTopPDialogOpen) {
        TopPDialog(
            topP = topP,
            onDismissRequest = settingViewModel::closeTopPDialog
        ) { p ->
            settingViewModel.updateTopP(apiType, p)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeTopPDialog()
        }
    }
}

@Composable
fun SystemPromptDialog(
    dialogState: SettingViewModel.DialogState,
    apiType: ApiType,
    systemPrompt: String,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isSystemPromptDialogOpen) {
        SystemPromptDialog(
            prompt = systemPrompt,
            onDismissRequest = settingViewModel::closeSystemPromptDialog
        ) {
            settingViewModel.updateSystemPrompt(apiType, it)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeSystemPromptDialog()
        }
    }
}

@Composable
private fun APIUrlDialog(
    apiType: ApiType,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onResetRequest: () -> Unit,
    onConfirmRequest: (url: String) -> Unit
) {
    var apiUrl by remember { mutableStateOf(initialValue) }
    val configuration = LocalConfiguration.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.api_url)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.api_url_cautions)
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    value = apiUrl,
                    singleLine = true,
                    isError = apiUrl.isValidUrl().not(),
                    onValueChange = { apiUrl = it },
                    label = {
                        Text(stringResource(R.string.api_url))
                    },
                    supportingText = {
                        if (apiUrl.isValidUrl().not()) {
                            Text(text = stringResource(R.string.invalid_api_url))
                        }
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = apiUrl.isNotBlank() && apiUrl.isValidUrl() && apiUrl.endsWith("/"),
                onClick = { onConfirmRequest(apiUrl) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = onResetRequest
                ) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun APIKeyDialog(
    apiType: ApiType,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (token: String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = getPlatformAPILabelResources()[apiType]!!) },
        text = {
            if (apiType == ApiType.BEDROCK) {
                BedrockCredentialsField(
                    value = token,
                    onValueChange = { token = it },
                    onClearClick = { token = "" }
                )
            } else {
                TokenInputField(
                    value = token,
                    onValueChange = { token = it },
                    onClearClick = { token = "" },
                    label = getPlatformAPILabelResources()[apiType]!!,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    helpLink = getPlatformHelpLinkResources()[apiType]!!
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = token.isNotBlank(),
                onClick = { onConfirmRequest(token) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ModelDialog(
    apiType: ApiType,
    initModel: String,
    modelFetchState: Map<ApiType, ModelFetchResult>,
    fetchedModels: Map<ApiType, List<com.matrix.multigpt.data.dto.ModelInfo>>,
    onFetchModels: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (model: String) -> Unit
) {
    // Trigger model fetching when dialog opens
    LaunchedEffect(apiType) {
        onFetchModels()
    }
    
    // Determine which models to display
    val displayModels = remember(fetchedModels, apiType) {
        derivedStateOf {
            val fetched = fetchedModels[apiType]
            if (fetched != null && fetched.isNotEmpty()) {
                // Use dynamically fetched models
                fetched.map { model ->
                    APIModel(
                        name = model.name,
                        description = model.description ?: "",
                        aliasValue = model.id
                    )
                }
            } else {
                null // Will use fallback
            }
        }
    }.value
    
    // Fallback to hardcoded models with localized strings
    val modelList = when (apiType) {
        ApiType.OPENAI -> openaiModels
        ApiType.ANTHROPIC -> anthropicModels
        ApiType.GOOGLE -> googleModels
        ApiType.GROQ -> groqModels
        ApiType.OLLAMA -> ollamaModels
        ApiType.BEDROCK -> bedrockModels
        ApiType.LOCAL -> linkedSetOf() // Local models are managed separately
    }
    val fallbackModels = when (apiType) {
        ApiType.OPENAI -> generateOpenAIModelList(models = modelList)
        ApiType.ANTHROPIC -> generateAnthropicModelList(models = modelList)
        ApiType.GOOGLE -> generateGoogleModelList(models = modelList)
        ApiType.GROQ -> generateGroqModelList(models = modelList)
        ApiType.OLLAMA -> listOf()
        ApiType.BEDROCK -> generateBedrockModelList(models = modelList)
        ApiType.LOCAL -> listOf() // Local models are selected via Local AI settings
    }
    
    val availableModels = displayModels ?: fallbackModels
    val configuration = LocalConfiguration.current
    var model by remember { mutableStateOf(initModel) }
    var customSelected by remember { mutableStateOf(model !in availableModels.map { it.aliasValue }.toSet()) }
    var customModel by remember { mutableStateOf(if (customSelected) model else "") }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.api_model)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Show loading or error state
                val fetchState = modelFetchState[apiType]
                when (fetchState) {
                    is ModelFetchResult.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.fetching_models),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    is ModelFetchResult.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = fetchState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = onFetchModels) {
                                Text(
                                    text = stringResource(R.string.retry),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = stringResource(R.string.using_fallback_models),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> Unit
                }
                
                availableModels.forEach { m ->
                    RadioItem(
                        value = m.aliasValue,
                        selected = model == m.aliasValue && !customSelected,
                        title = m.name,
                        description = m.description,
                        onSelected = {
                            model = it
                            customSelected = false
                        }
                    )
                }
                RadioItem(
                    value = customModel,
                    selected = customSelected,
                    title = stringResource(R.string.custom),
                    description = stringResource(R.string.custom_description),
                    onSelected = {
                        customSelected = true
                        customModel = it
                    }
                )
                OutlinedTextField(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    enabled = customSelected,
                    value = customModel,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onValueChange = { s -> customModel = s },
                    label = {
                        Text(stringResource(R.string.model_name))
                    },
                    placeholder = {
                        Text(stringResource(R.string.model_custom_example))
                    },
                    supportingText = {
                        Text(stringResource(R.string.custom_model_warning))
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = if (customSelected) customModel.isNotBlank() else model.isNotBlank(),
                onClick = {
                    if (customSelected) {
                        onConfirmRequest(customModel)
                    } else {
                        onConfirmRequest(model)
                    }
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TemperatureDialog(
    apiType: ApiType,
    temperature: Float,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (temp: Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldTemperature by remember { mutableStateOf(temperature.toString()) }
    var sliderTemperature by remember { mutableFloatStateOf(temperature) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.temperature_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.temperature_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTemperature,
                    onValueChange = { t ->
                        textFieldTemperature = t
                        val converted = t.toFloatOrNull()
                        converted?.let {
                            sliderTemperature = when (apiType) {
                                ApiType.ANTHROPIC -> it.coerceIn(0F, 1F)
                                ApiType.LOCAL -> it.coerceIn(0F, 2F)
                                else -> it.coerceIn(0F, 2F)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.temperature))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTemperature,
                    valueRange = when (apiType) {
                        ApiType.ANTHROPIC -> 0F..1F
                        ApiType.LOCAL -> 0F..2F
                        else -> 0F..2F
                    },
                    steps = when (apiType) {
                        ApiType.ANTHROPIC -> 10 - 1
                        ApiType.LOCAL -> 20 - 1
                        else -> 20 - 1
                    },
                    onValueChange = { t ->
                        sliderTemperature = t
                        textFieldTemperature = t.toString()
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(sliderTemperature) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun TopPDialog(
    topP: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (topP: Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldTopP by remember { mutableStateOf((topP ?: 1F).toString()) }
    var sliderTopP by remember { mutableFloatStateOf(topP ?: 1F) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.top_p_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.top_p_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTopP,
                    onValueChange = { p ->
                        textFieldTopP = p
                        p.toFloatOrNull()?.let {
                            val rounded = (it.coerceIn(0.1F, 1F) * 100).roundToInt() / 100F
                            sliderTopP = rounded
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.top_p))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTopP,
                    valueRange = 0.1F..1F,
                    steps = 8,
                    onValueChange = { t ->
                        val rounded = (t * 100).roundToInt() / 100F
                        sliderTopP = rounded
                        textFieldTopP = rounded.toString()
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(sliderTopP) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SystemPromptDialog(
    prompt: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (text: String) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldPrompt by remember { mutableStateOf(prompt) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.system_prompt_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.system_prompt_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldPrompt,
                    onValueChange = { textFieldPrompt = it },
                    label = {
                        Text(stringResource(R.string.system_prompt))
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = textFieldPrompt.isNotBlank(),
                onClick = { onConfirmRequest(textFieldPrompt) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ==================== Local AI Specific Dialogs ====================

@Composable
fun TopKDialog(
    dialogState: SettingViewModel.DialogState,
    topK: Int,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isTopKDialogOpen) {
        TopKDialogContent(
            topK = topK,
            onDismissRequest = settingViewModel::closeTopKDialog
        ) { value ->
            settingViewModel.updateTopK(value)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeTopKDialog()
        }
    }
}

@Composable
fun BatchSizeDialog(
    dialogState: SettingViewModel.DialogState,
    batchSize: Int,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isBatchSizeDialogOpen) {
        BatchSizeDialogContent(
            batchSize = batchSize,
            onDismissRequest = settingViewModel::closeBatchSizeDialog
        ) { value ->
            settingViewModel.updateBatchSize(value)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeBatchSizeDialog()
        }
    }
}

@Composable
fun ContextSizeDialog(
    dialogState: SettingViewModel.DialogState,
    contextSize: Int,
    settingViewModel: SettingViewModel
) {
    if (dialogState.isContextSizeDialogOpen) {
        ContextSizeDialogContent(
            contextSize = contextSize,
            onDismissRequest = settingViewModel::closeContextSizeDialog
        ) { value ->
            settingViewModel.updateContextSize(value)
            settingViewModel.savePlatformSettings()
            settingViewModel.closeContextSizeDialog()
        }
    }
}

@Composable
private fun TopKDialogContent(
    topK: Int,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    var textFieldTopK by remember { mutableStateOf(topK.toString()) }
    var sliderTopK by remember { mutableFloatStateOf(topK.toFloat()) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.top_k_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.top_k_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTopK,
                    onValueChange = { k ->
                        textFieldTopK = k
                        k.toIntOrNull()?.let {
                            sliderTopK = it.coerceIn(1, 200).toFloat()
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(stringResource(R.string.top_k))
                    }
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTopK,
                    valueRange = 1F..200F,
                    steps = 19, // 10, 20, 30, ..., 200
                    onValueChange = { k ->
                        sliderTopK = k
                        textFieldTopK = k.roundToInt().toString()
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(sliderTopK.roundToInt()) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun BatchSizeDialogContent(
    batchSize: Int,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    var selectedBatchSize by remember { mutableStateOf(batchSize) }
    
    val options = listOf(
        0 to stringResource(R.string.preset_auto),
        256 to "256 (${stringResource(R.string.preset_low_memory)})",
        512 to "512 (${stringResource(R.string.preset_balanced)})",
        1024 to "1024 (${stringResource(R.string.preset_max_performance)})"
    )

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.batch_size_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.batch_size_setting_description))
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    options.forEach { (value, label) ->
                        RadioItem(
                            value = value.toString(),
                            selected = selectedBatchSize == value,
                            title = label,
                            description = null,
                            onSelected = { selectedBatchSize = value }
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(selectedBatchSize) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ContextSizeDialogContent(
    contextSize: Int,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    var selectedContextSize by remember { mutableStateOf(contextSize) }
    
    val options = listOf(
        1024 to "1024 tokens (Fast, short conversations)",
        2048 to "2048 tokens (Balanced - recommended)",
        4096 to "4096 tokens (Long conversations)",
        8192 to "8192 tokens (Maximum, complex tasks)"
    )

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = configuration.screenWidthDp.dp - 40.dp)
            .heightIn(max = configuration.screenHeightDp.dp - 80.dp),
        title = { Text(text = stringResource(R.string.context_size_setting)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.context_size_setting_description))
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    options.forEach { (value, label) ->
                        RadioItem(
                            value = value.toString(),
                            selected = selectedContextSize == value,
                            title = label,
                            description = null,
                            onSelected = { selectedContextSize = value }
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(selectedContextSize) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
