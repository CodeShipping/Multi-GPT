package com.matrix.multigpt.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrix.multigpt.R
import com.matrix.multigpt.presentation.common.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onNavigationClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LicenseTopAppBar(onNavigationClick, scrollBehavior)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            // MultiGPT License
            SettingItem(
                modifier = Modifier.height(80.dp),
                title = "MultiGPT",
                description = "GNU General Public License v3.0",
                onItemClick = { 
                    uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html")
                },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_license),
                        contentDescription = "License Icon"
                    )
                }
            )
            
            // GPT Mobile Attribution
            SettingItem(
                modifier = Modifier.height(80.dp),
                title = "GPT Mobile",
                description = "Based on GPT Mobile by Taewan Park (GPL-3.0)",
                onItemClick = { 
                    uriHandler.openUri("https://github.com/Taewan-P/gpt_mobile")
                },
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub Icon"
                    )
                }
            )
            
            // License Info Text
            Text(
                text = "This application is free software licensed under the GNU General Public License v3.0. " +
                       "You can redistribute it and/or modify it under the terms of the GPL-3.0 license.\n\n" +
                       "This project is inspired by and builds upon concepts from GPT Mobile, " +
                       "including multi-model chat interface design, API integration patterns, " +
                       "and modern Android development practices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LicenseTopAppBar(
    onNavigationClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.license),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}
