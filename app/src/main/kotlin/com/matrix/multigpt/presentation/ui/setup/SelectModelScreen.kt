package com.matrix.multigpt.presentation.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.R
import com.matrix.multigpt.data.ModelConstants.anthropicModels
import com.matrix.multigpt.data.ModelConstants.bedrockModels
import com.matrix.multigpt.data.ModelConstants.googleModels
import com.matrix.multigpt.data.ModelConstants.groqModels
import com.matrix.multigpt.data.ModelConstants.openaiModels
import com.matrix.multigpt.data.dto.APIModel
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.presentation.common.PrimaryLongButton
import com.matrix.multigpt.presentation.common.RadioItem
import com.matrix.multigpt.util.generateAnthropicModelList
import com.matrix.multigpt.util.generateBedrockModelList
import com.matrix.multigpt.util.generateGoogleModelList
import com.matrix.multigpt.util.generateGroqModelList
import com.matrix.multigpt.util.generateOpenAIModelList
import com.matrix.multigpt.util.getAPIModelSelectDescription
import com.matrix.multigpt.util.getAPIModelSelectTitle

@Composable
fun SelectModelScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModel = hiltViewModel(),
    currentRoute: String,
    platformType: ApiType,
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    val title = getAPIModelSelectTitle(platformType)
    val description = getAPIModelSelectDescription(platformType)
    
    val modelFetchState by setupViewModel.modelFetchState.collectAsStateWithLifecycle()
    val fetchedModels by setupViewModel.fetchedModels.collectAsStateWithLifecycle()
    
    // Trigger model fetching when screen is displayed
    LaunchedEffect(platformType) {
        setupViewModel.fetchModelsForPlatform(platformType)
    }
    
    // Determine which models to display
    val displayModels = remember(fetchedModels, platformType) {
        derivedStateOf {
            val fetched = fetchedModels[platformType]
            if (fetched != null && fetched.isNotEmpty()) {
                // Use dynamically fetched models directly
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
    val fallbackModels = when (platformType) {
        ApiType.OPENAI -> generateOpenAIModelList(models = openaiModels)
        ApiType.ANTHROPIC -> generateAnthropicModelList(models = anthropicModels)
        ApiType.GOOGLE -> generateGoogleModelList(models = googleModels)
        ApiType.GROQ -> generateGroqModelList(models = groqModels)
        ApiType.OLLAMA -> listOf()
        ApiType.BEDROCK -> generateBedrockModelList(models = bedrockModels)
        ApiType.LOCAL -> listOf() // Local models are managed via Local AI settings
    }
    
    val availableModels = displayModels ?: fallbackModels
    val defaultModel = remember {
        derivedStateOf {
            setupViewModel.setDefaultModel(
                platformType,
                when (platformType) {
                    ApiType.OPENAI -> 0
                    ApiType.ANTHROPIC -> 0
                    ApiType.GOOGLE -> 1
                    ApiType.GROQ -> 0
                    ApiType.OLLAMA -> 0
                    ApiType.BEDROCK -> 0
                    ApiType.LOCAL -> 0
                }
            )
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val platformState by setupViewModel.platformState.collectAsStateWithLifecycle()
    val model = platformState.firstOrNull { it.name == platformType }?.model ?: defaultModel.value

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
        ) {
            SelectModelText(title = title, description = description)
            
            // Show loading or error state
            val fetchState = modelFetchState[platformType]
            when (fetchState) {
                is ModelFetchResult.Loading -> {
                    LoadingModelsIndicator()
                }
                is ModelFetchResult.Error -> {
                    ModelFetchError(
                        message = fetchState.message,
                        onRetry = { setupViewModel.fetchModelsForPlatform(platformType) }
                    )
                }
                else -> Unit
            }
            
            ModelRadioGroup(
                availableModels = availableModels,
                initModel = model,
                onChangeEvent = { model -> setupViewModel.updateModel(platformType, model) }
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                enabled = availableModels.any { it.aliasValue == model } || model.isNotBlank(),
                onClick = {
                    val nextStep = setupViewModel.getNextSetupRoute(currentRoute)
                    onNavigate(nextStep)
                },
                text = stringResource(R.string.next)
            )
        }
    }
}

@Composable
fun SelectModelText(
    modifier: Modifier = Modifier,
    title: String,
    description: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = description,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ModelRadioGroup(
    modifier: Modifier = Modifier,
    availableModels: List<APIModel>,
    initModel: String,
    onChangeEvent: (String) -> Unit
) {
    var model by remember { mutableStateOf(initModel) }
    var customSelected by remember { mutableStateOf(model !in availableModels.map { it.aliasValue }.toSet()) }
    var customModel by remember { mutableStateOf(if (customSelected) model else "") }

    Column(modifier = modifier) {
        availableModels.forEach { m ->
            RadioItem(
                value = m.aliasValue,
                selected = model == m.aliasValue && !customSelected,
                title = m.name,
                description = m.description,
                onSelected = {
                    model = it
                    customSelected = false
                    onChangeEvent(it)
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
                onChangeEvent(it)
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
            onValueChange = { s ->
                customModel = s
                onChangeEvent(s)
            },
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
}

@Composable
fun LoadingModelsIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = stringResource(R.string.fetching_models),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun ModelFetchError(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
        Text(
            text = stringResource(R.string.using_fallback_models),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
