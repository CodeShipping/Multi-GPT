package com.matrix.multigpt.localinference.presentation.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.localinference.R
import com.matrix.multigpt.localinference.data.model.ModelFamily
import com.matrix.multigpt.localinference.presentation.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Main screen for displaying and managing local AI models.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (modelId: String, modelPath: String) -> Unit,
    viewModel: ModelListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }

    // File picker launcher for importing GGUF files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onEvent(ModelListEvent.ImportModels(uris))
        }
    }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ModelListEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ModelListEffect.NavigateToChat -> {
                    onNavigateToChat(effect.modelId, effect.modelPath)
                }
                is ModelListEffect.NavigateToModelDetail -> {
                    // Handle model detail navigation if needed
                }
                ModelListEffect.ShowDownloadStarted -> {
                    snackbarHostState.showSnackbar("Download started")
                }
                ModelListEffect.ShowDownloadComplete -> {
                    snackbarHostState.showSnackbar("Download complete")
                }
                is ModelListEffect.ShowDeleteConfirmation -> {
                    showDeleteDialog = effect.modelId
                }
                is ModelListEffect.ShowImportSuccess -> {
                    importProgress = null
                    snackbarHostState.showSnackbar(
                        "${effect.count} model${if (effect.count > 1) "s" else ""} imported successfully"
                    )
                }
                is ModelListEffect.ShowImportProgress -> {
                    importProgress = Triple(effect.current, effect.total, effect.fileName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_local_models)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                },
                actions = {
                    // Import GGUF button
                    IconButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cd_import_button)
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.cd_filter_button)
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(ModelListEvent.Refresh) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh_button)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.onEvent(ModelListEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.models.isEmpty() -> {
                    LoadingContent()
                }
                uiState.error != null && uiState.models.isEmpty() -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.onEvent(ModelListEvent.LoadModels) }
                    )
                }
                uiState.filteredModels.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    ModelListContent(
                        uiState = uiState,
                        onEvent = viewModel::onEvent,
                        formatSize = viewModel::formatSize
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { modelId ->
        val model = uiState.models.find { it.id == modelId }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = {
                Text(stringResource(R.string.dialog_delete_message, model?.name ?: modelId))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDeleteModel(modelId)
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.dialog_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            families = uiState.families,
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = { filter ->
                viewModel.onEvent(ModelListEvent.SetFilter(filter))
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Import Progress Dialog
    importProgress?.let { (current, total, fileName) ->
        AlertDialog(
            onDismissRequest = { /* Non-dismissible */ },
            title = { Text("Importing Models") },
            text = {
                Column {
                    Text("Importing $current of $total...")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { current.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.model_list_loading))
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.model_list_retry))
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.model_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModelListContent(
    uiState: ModelListUiState,
    onEvent: (ModelListEvent) -> Unit,
    formatSize: (Long) -> String
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Info card at the top
        item(key = "info_card") {
            LocalAIInfoCard()
        }
        
        // Group by family
        uiState.groupedByFamily.forEach { (family, models) ->
            if (family != null) {
                item(key = "header_${family.id}") {
                    FamilyHeader(family = family)
                }
            }
            
            items(
                items = models,
                key = { it.id }
            ) { model ->
                ModelCard(
                    model = model,
                    downloadState = uiState.modelStates[model.id],
                    onDownload = { onEvent(ModelListEvent.DownloadModel(model.id)) },
                    onPause = { onEvent(ModelListEvent.PauseDownload(model.id)) },
                    onResume = { onEvent(ModelListEvent.ResumeDownload(model.id)) },
                    onCancel = { onEvent(ModelListEvent.CancelDownload(model.id)) },
                    onDelete = { onEvent(ModelListEvent.DeleteModel(model.id)) },
                    onClick = { onEvent(ModelListEvent.SelectModel(model.id)) },
                    formatSize = formatSize
                )
            }
        }
    }
}

@Composable
private fun LocalAIInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.local_ai_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.local_ai_info_ram),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.local_ai_info_context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.local_ai_info_accuracy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.local_ai_info_tip),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun FamilyHeader(family: ModelFamily) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = family.name,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = family.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    families: List<ModelFamily>,
    selectedFilter: ModelFilter,
    onFilterSelected: (ModelFilter) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Filter Models",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Basic Filters
            FilterChip(
                selected = selectedFilter == ModelFilter.All,
                onClick = { onFilterSelected(ModelFilter.All) },
                label = { Text(stringResource(R.string.filter_all)) },
                modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
                selected = selectedFilter == ModelFilter.Recommended,
                onClick = { onFilterSelected(ModelFilter.Recommended) },
                label = { Text(stringResource(R.string.filter_recommended)) },
                leadingIcon = {
                    if (selectedFilter == ModelFilter.Recommended) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            FilterChip(
                selected = selectedFilter == ModelFilter.Downloaded,
                onClick = { onFilterSelected(ModelFilter.Downloaded) },
                label = { Text(stringResource(R.string.filter_downloaded)) },
                leadingIcon = {
                    if (selectedFilter == ModelFilter.Downloaded) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (families.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.filter_by_family),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                families.forEach { family ->
                    FilterChip(
                        selected = selectedFilter is ModelFilter.ByFamily && 
                                   selectedFilter.familyId == family.id,
                        onClick = { onFilterSelected(ModelFilter.ByFamily(family.id)) },
                        label = { Text(family.name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
