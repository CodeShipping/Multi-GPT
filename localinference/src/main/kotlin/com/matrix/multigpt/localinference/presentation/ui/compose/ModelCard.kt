package com.matrix.multigpt.localinference.presentation.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.multigpt.localinference.R
import com.matrix.multigpt.localinference.data.model.*

/**
 * Compose card component for displaying a local model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCard(
    model: LocalModel,
    downloadState: LocalModelState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    formatSize: (Long) -> String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (model.isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.model_recommended)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Performance Badge
                PerformanceBadge(rating = model.performance.rating)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModelSpec(
                    icon = Icons.Outlined.Memory,
                    label = model.parameters
                )
                ModelSpec(
                    icon = Icons.Outlined.Storage,
                    label = formatSize(model.size)
                )
                ModelSpec(
                    icon = Icons.Outlined.Speed,
                    label = "${model.performance.tokensPerSecond.toInt()} tok/s"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Use Cases
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                model.useCases.take(4).forEach { useCase ->
                    UseCaseChip(useCase = useCase)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Download State / Actions
            ModelDownloadSection(
                downloadState = downloadState,
                modelSize = model.size,
                onDownload = onDownload,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onDelete = onDelete,
                onClick = onClick,
                formatSize = formatSize
            )
        }
    }
}

@Composable
private fun PerformanceBadge(rating: PerformanceRating) {
    val (color, text) = when (rating) {
        PerformanceRating.FAST -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.performance_fast)
        PerformanceRating.BALANCED -> MaterialTheme.colorScheme.primary to stringResource(R.string.performance_balanced)
        PerformanceRating.QUALITY -> MaterialTheme.colorScheme.secondary to stringResource(R.string.performance_quality)
        PerformanceRating.DEMANDING -> MaterialTheme.colorScheme.error to stringResource(R.string.performance_demanding)
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ModelSpec(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UseCaseChip(useCase: UseCase) {
    val label = when (useCase) {
        UseCase.CHAT -> stringResource(R.string.use_case_chat)
        UseCase.CODING -> stringResource(R.string.use_case_coding)
        UseCase.CREATIVE -> stringResource(R.string.use_case_creative)
        UseCase.SUMMARIZATION -> stringResource(R.string.use_case_summarization)
        UseCase.TRANSLATION -> stringResource(R.string.use_case_translation)
        UseCase.QUESTION_ANSWERING -> stringResource(R.string.use_case_question_answering)
        UseCase.MATH -> stringResource(R.string.use_case_math)
        UseCase.GENERAL -> stringResource(R.string.use_case_general)
    }
    
    SuggestionChip(
        onClick = { },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.height(24.dp)
    )
}

@Composable
private fun ModelDownloadSection(
    downloadState: LocalModelState?,
    modelSize: Long,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    formatSize: (Long) -> String
) {
    val status = downloadState?.status ?: ModelStatus.NOT_DOWNLOADED
    
    when (status) {
        ModelStatus.NOT_DOWNLOADED -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_download))
            }
        }
        
        ModelStatus.DOWNLOADING -> {
            Column {
                LinearProgressIndicator(
                    progress = { downloadState?.downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatSize(downloadState?.downloadedBytes ?: 0)} / ${formatSize(downloadState?.totalBytes ?: modelSize)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.action_pause))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
        
        ModelStatus.PAUSED -> {
            Column {
                LinearProgressIndicator(
                    progress = { downloadState?.downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.download_paused),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
        
        ModelStatus.DOWNLOADED, ModelStatus.LOADED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_start_chat))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        ModelStatus.LOADING -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.download_loading))
            }
        }
        
        ModelStatus.ERROR -> {
            Column {
                Text(
                    text = downloadState?.errorMessage ?: stringResource(R.string.download_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.model_list_retry))
                }
            }
        }
    }
}
