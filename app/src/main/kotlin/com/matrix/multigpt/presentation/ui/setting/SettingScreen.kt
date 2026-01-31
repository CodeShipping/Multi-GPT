package com.matrix.multigpt.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.R
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.model.DynamicTheme
import com.matrix.multigpt.data.model.ThemeMode
import com.matrix.multigpt.presentation.common.LocalDynamicTheme
import com.matrix.multigpt.presentation.common.LocalThemeMode
import com.matrix.multigpt.presentation.common.LocalThemeViewModel
import com.matrix.multigpt.presentation.common.RadioItem
import com.matrix.multigpt.presentation.common.SettingItem
import com.matrix.multigpt.util.getDynamicThemeTitle
import com.matrix.multigpt.util.getPlatformSettingDescription
import com.matrix.multigpt.util.getPlatformSettingTitle
import com.matrix.multigpt.util.getThemeModeTitle
import com.matrix.multigpt.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToPlatformSetting: (ApiType) -> Unit,
    onNavigateToAboutPage: () -> Unit,
    onNavigateToLocalAI: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingTopBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            ThemeSetting { settingViewModel.openThemeDialog() }
            // Cloud-based API platforms (exclude LOCAL since it has dedicated settings)
            ApiType.entries.filter { it != ApiType.LOCAL }.forEach { apiType ->
                SettingItem(
                    title = getPlatformSettingTitle(apiType),
                    description = getPlatformSettingDescription(apiType),
                    onItemClick = { onNavigateToPlatformSetting(apiType) },
                    showTrailingIcon = true,
                    showLeadingIcon = false
                )
            }
            
            // Local AI Models - On-device inference (separate dedicated settings)
            LocalAISettingItem(onItemClick = onNavigateToLocalAI)
            
            AboutPageItem(onItemClick = onNavigateToAboutPage)

            if (dialogState.isThemeDialogOpen) {
                ThemeSettingDialog(settingViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.settings),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = navigationOnClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ThemeSetting(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.theme_settings),
        description = stringResource(R.string.theme_description),
        onItemClick = onItemClick,
        showTrailingIcon = false,
        showLeadingIcon = false
    )
}

@Composable
fun LocalAISettingItem(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.local_ai_models),
        description = stringResource(R.string.local_ai_description),
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun AboutPageItem(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.about),
        description = stringResource(R.string.about_description),
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun ThemeSettingDialog(
    settingViewModel: SettingViewModel = hiltViewModel()
) {
    val themeViewModel = LocalThemeViewModel.current
    AlertDialog(
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(R.string.dynamic_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                DynamicTheme.entries.forEach { theme ->
                    RadioItem(
                        title = getDynamicThemeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalDynamicTheme.current == theme
                    ) {
                        themeViewModel.updateDynamicTheme(theme)
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
                Text(text = stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                ThemeMode.entries.forEach { theme ->
                    RadioItem(
                        title = getThemeModeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalThemeMode.current == theme
                    ) {
                        themeViewModel.updateThemeMode(theme)
                    }
                }
            }
        },
        onDismissRequest = settingViewModel::closeThemeDialog,
        confirmButton = {
            TextButton(
                onClick = settingViewModel::closeThemeDialog
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
