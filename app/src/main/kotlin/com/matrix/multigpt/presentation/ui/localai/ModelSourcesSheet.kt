package com.matrix.multigpt.presentation.ui.localai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A website/repository where GGUF models can be downloaded. */
data class ModelSource(val name: String, val description: String, val url: String)

private val MODEL_SOURCES = listOf(
    ModelSource("Hugging Face (GGUF)", "Largest hub of GGUF models, filtered to GGUF files and sorted by trending.", "https://huggingface.co/models?library=gguf&sort=trending"),
    ModelSource("ModelScope", "Alibaba's model hub — usually much faster downloads across Asia.", "https://modelscope.cn/models?libraries=GGUF"),
    ModelSource("bartowski", "High-quality, up-to-date GGUF quantizations of popular models.", "https://huggingface.co/bartowski"),
    ModelSource("mradermacher", "Massive catalog of GGUF quants, including rarer models.", "https://huggingface.co/mradermacher"),
    ModelSource("unsloth", "Optimized GGUF quants, often first to release brand-new models.", "https://huggingface.co/unsloth"),
    ModelSource("TheBloke", "Classic GGUF library — legacy but still widely referenced.", "https://huggingface.co/TheBloke"),
    ModelSource("Ollama Library", "Reference catalog of open models and their variants.", "https://ollama.com/library"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSourcesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Where to find GGUF models",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            "Download a .gguf file from any source below, then use Import to add it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
            items(MODEL_SOURCES) { source ->
                ListItem(
                    headlineContent = { Text(source.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(source.description) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser")
                    },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                        }
                    }
                )
            }
        }
    }
}
