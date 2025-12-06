package com.matrix.multigpt.presentation.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matrix.multigpt.R
import com.matrix.multigpt.presentation.common.PrimaryLongButton
import com.matrix.multigpt.presentation.common.Route
import com.matrix.multigpt.presentation.icons.Done
import com.matrix.multigpt.util.AdMobManager

@Composable
fun SetupCompleteScreen(
    modifier: Modifier = Modifier,
    currentRoute: String = Route.SETUP_COMPLETE,
    setupViewModel: SetupViewModel = hiltViewModel(),
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SetupCompleteText()
            SetupCompleteLogo(
                Modifier
                    .widthIn(min = screenWidth)
                    .heightIn(min = screenWidth)
                    .padding(screenWidth * 0.1f)
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                onClick = {
                    setupViewModel.savePlatformState()
                    val nextStep = setupViewModel.getNextSetupRoute(currentRoute)
                    
                    // Show interstitial ad after setup completion
                    activity?.let { act ->
                        AdMobManager.showInterstitialAd(act, R.string.setup_complete_interstitial) {
                            onNavigate(nextStep)
                        }
                    } ?: run {
                        onNavigate(nextStep)
                    }
                },
                text = stringResource(R.string.done)
            )
        }
    }
}

@Preview
@Composable
private fun SetupCompleteText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.setup_complete),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.setup_complete_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview
@Composable
private fun SetupCompleteLogo(modifier: Modifier = Modifier) {
    Image(
        imageVector = Done,
        contentDescription = stringResource(R.string.setup_complete_logo),
        modifier = modifier
            .padding(64.dp)
    )
}
