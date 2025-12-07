package com.matrix.multigpt.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.matrix.multigpt.R
import com.matrix.multigpt.data.dto.bedrock.BedrockAuthMethod
import com.matrix.multigpt.data.dto.bedrock.BedrockCredentials
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun BedrockCredentialsField(
    value: String,
    onValueChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Parse existing JSON or use defaults
    val credentials = remember(value) {
        try {
            if (value.isNotBlank()) {
                Json.decodeFromString<BedrockCredentials>(value)
            } else {
                BedrockCredentials()
            }
        } catch (e: Exception) {
            BedrockCredentials()
        }
    }

    var authMethod by remember(value) { mutableStateOf(credentials.authMethod) }
    var accessKeyId by remember(value) { mutableStateOf(credentials.accessKeyId) }
    var secretAccessKey by remember(value) { mutableStateOf(credentials.secretAccessKey) }
    var region by remember(value) { mutableStateOf(credentials.region) }
    var sessionToken by remember(value) { mutableStateOf(credentials.sessionToken ?: "") }
    var apiKey by remember(value) { mutableStateOf(credentials.apiKey) }

    // Update parent when any field changes
    fun updateCredentials() {
        val newCredentials = BedrockCredentials(
            authMethod = authMethod,
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
            region = region,
            sessionToken = sessionToken.ifBlank { null },
            apiKey = apiKey
        )
        val json = Json.encodeToString(newCredentials)
        onValueChange(json)
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.bedrock_credentials_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Authentication Method Selection
        Text(
            text = stringResource(R.string.bedrock_auth_method),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // API Key Method
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = authMethod == BedrockAuthMethod.API_KEY,
                    onClick = {
                        authMethod = BedrockAuthMethod.API_KEY
                        updateCredentials()
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (authMethod == BedrockAuthMethod.API_KEY) 
                    MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = authMethod == BedrockAuthMethod.API_KEY,
                    onClick = {
                        authMethod = BedrockAuthMethod.API_KEY
                        updateCredentials()
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.bedrock_api_key_method),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.bedrock_api_key_method_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Signature V4 Method
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = authMethod == BedrockAuthMethod.SIGNATURE_V4,
                    onClick = {
                        authMethod = BedrockAuthMethod.SIGNATURE_V4
                        updateCredentials()
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (authMethod == BedrockAuthMethod.SIGNATURE_V4) 
                    MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = authMethod == BedrockAuthMethod.SIGNATURE_V4,
                    onClick = {
                        authMethod = BedrockAuthMethod.SIGNATURE_V4
                        updateCredentials()
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.bedrock_signature_v4_method),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.bedrock_signature_v4_method_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show appropriate credential fields based on selected method
        when (authMethod) {
            BedrockAuthMethod.API_KEY -> {
                ApiKeyCredentialsSection(
                    apiKey = apiKey,
                    onApiKeyChange = { 
                        apiKey = it
                        updateCredentials()
                    }
                )
            }
            BedrockAuthMethod.SIGNATURE_V4 -> {
                SignatureV4CredentialsSection(
                    accessKeyId = accessKeyId,
                    secretAccessKey = secretAccessKey,
                    region = region,
                    sessionToken = sessionToken,
                    onAccessKeyIdChange = { 
                        accessKeyId = it
                        updateCredentials()
                    },
                    onSecretAccessKeyChange = { 
                        secretAccessKey = it
                        updateCredentials()
                    },
                    onRegionChange = { 
                        region = it
                        updateCredentials()
                    },
                    onSessionTokenChange = { 
                        sessionToken = it
                        updateCredentials()
                    }
                )
            }
        }

        // Clear button
        if (shouldShowClearButton(authMethod, accessKeyId, secretAccessKey, region, sessionToken, apiKey)) {
            TextButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    authMethod = BedrockAuthMethod.SIGNATURE_V4
                    accessKeyId = ""
                    secretAccessKey = ""
                    region = "us-east-1"
                    sessionToken = ""
                    apiKey = ""
                    onClearClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(stringResource(R.string.clear_token))
            }
        }
    }
}

@Composable
private fun ApiKeyCredentialsSection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.bedrock_api_key_section_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.bedrock_api_key)) },
        placeholder = { Text("abcr_1234567890abcdef...") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        supportingText = {
            Text(
                text = stringResource(R.string.bedrock_api_key_help),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

@Composable
private fun SignatureV4CredentialsSection(
    accessKeyId: String,
    secretAccessKey: String,
    region: String,
    sessionToken: String,
    onAccessKeyIdChange: (String) -> Unit,
    onSecretAccessKeyChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onSessionTokenChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.bedrock_signature_v4_section_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = accessKeyId,
        onValueChange = onAccessKeyIdChange,
        label = { Text(stringResource(R.string.aws_access_key_id)) },
        placeholder = { Text("AKIAIOSFODNN7EXAMPLE") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = secretAccessKey,
        onValueChange = onSecretAccessKeyChange,
        label = { Text(stringResource(R.string.aws_secret_access_key)) },
        placeholder = { Text("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = region,
        onValueChange = onRegionChange,
        label = { Text(stringResource(R.string.aws_region)) },
        placeholder = { Text("us-east-1") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true,
        supportingText = {
            Text(
                text = stringResource(R.string.aws_region_help),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = sessionToken,
        onValueChange = onSessionTokenChange,
        label = { Text(stringResource(R.string.aws_session_token)) },
        placeholder = { Text(stringResource(R.string.aws_session_token_optional)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        singleLine = true,
        supportingText = { 
            Text(
                text = stringResource(R.string.aws_session_token_help),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

private fun shouldShowClearButton(
    authMethod: BedrockAuthMethod,
    accessKeyId: String,
    secretAccessKey: String,
    region: String,
    sessionToken: String,
    apiKey: String
): Boolean {
    return when (authMethod) {
        BedrockAuthMethod.API_KEY -> apiKey.isNotBlank()
        BedrockAuthMethod.SIGNATURE_V4 -> accessKeyId.isNotBlank() || 
            secretAccessKey.isNotBlank() || 
            region != "us-east-1" || 
            sessionToken.isNotBlank()
    }
}
