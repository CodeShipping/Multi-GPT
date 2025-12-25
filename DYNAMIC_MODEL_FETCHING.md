# Dynamic Model Fetching Implementation

This document describes the dynamic model fetching feature implemented in MultiGPT.

## Overview

The application now supports dynamically fetching available models from AI provider APIs instead of relying solely on hardcoded model lists. This ensures users always have access to the latest models available from each provider.

## Architecture

### Core Components

1. **ModelResponse.kt** - Data Transfer Objects (DTOs)
   - `OpenAIModelsResponse` & `OpenAIModel` - For OpenAI and OpenAI-compatible APIs (OpenAI, Groq)
   - `GoogleModelsResponse` & `GoogleModel` - For Google AI Platform
   - `OllamaModelsResponse` & `OllamaModel` - For Ollama local models
   - `ModelInfo` - Unified model representation
   - `ModelFetchResult` - Sealed class for handling fetch states (Loading, Success, Error)

2. **ModelFetchService.kt** - Service Layer
   - `ModelFetchService` - Interface defining model fetching contract
   - `ModelFetchServiceImpl` - Implementation handling API calls to different providers
   - Supports: OpenAI, Anthropic, Google, Groq, and Ollama

3. **ModelFetchModule.kt** - Dependency Injection
   - Dagger Hilt module providing ModelFetchService as a singleton

4. **SetupViewModel.kt** - ViewModel Enhancement
   - Added `fetchModelsForPlatform()` - Triggers model fetching for a specific platform
   - Added `modelFetchState` - StateFlow tracking fetch operation status
   - Added `fetchedModels` - StateFlow storing retrieved models
   - Added `getFallbackModels()` - Returns hardcoded models as fallback

5. **SelectModelScreen.kt** - UI Enhancement
   - Automatically fetches models when screen is displayed
   - Shows loading indicator during fetch
   - Displays error message with retry option on failure
   - Falls back to hardcoded models if fetch fails
   - Uses fetched models when available

## How It Works

### Model Fetching Flow

1. User navigates to model selection screen
2. `LaunchedEffect` triggers `fetchModelsForPlatform()` for the current platform
3. ViewModel updates `modelFetchState` to `Loading`
4. Service makes API call to provider's models endpoint
5. On success:
   - Models are parsed and transformed to `ModelInfo`
   - `modelFetchState` updated to `Success`
   - `fetchedModels` updated with retrieved models
   - UI displays fetched models
6. On failure:
   - `modelFetchState` updated to `Error` with message
   - UI shows error with retry button
   - UI falls back to hardcoded models

### Provider-Specific Implementation

#### OpenAI
- Endpoint: `{apiUrl}/models`
- Authentication: Bearer token (API key)
- Filters: Only includes models containing "gpt" in the ID
- Sorting: By creation date (newest first)

#### Groq
- Endpoint: `{apiUrl}/models`
- Authentication: Bearer token (API key)
- Uses OpenAI-compatible format
- Sorting: By creation date (newest first)

#### Google AI
- Endpoint: `{apiUrl}/v1beta/models?key={apiKey}`
- Authentication: API key in query parameter
- Filters: Only includes models supporting "generateContent"
- Extracts model ID from resource name

#### Anthropic
- No public models endpoint available
- Returns updated hardcoded list of known models
- Includes Claude 3.5 Sonnet, Haiku, Opus models

#### Ollama
- Endpoint: `{apiUrl}/api/tags`
- Authentication: None required (local)
- Returns locally installed models
- Displays model size in description

## Benefits

1. **Always Up-to-Date**: Users automatically get access to new models as providers release them
2. **Better UX**: Real-time model availability based on user's API credentials
3. **Fallback Support**: Graceful degradation to hardcoded models if fetch fails
4. **Provider Flexibility**: Easy to add support for new providers
5. **Error Handling**: Clear feedback when fetching fails with retry option

## User Experience

### Loading State
- Shows "Fetching models…" message with spinner
- Non-blocking - user can still interact with the screen

### Success State
- Seamlessly displays fetched models
- Models show provider-specific information (owner, description, size)
- Maintains custom model option for flexibility

### Error State
- Displays error message explaining the issue
- Provides "Retry" button to attempt fetch again
- Shows "Using fallback models" message
- Continues showing hardcoded models for selection

## Testing

### Manual Testing Steps

1. **OpenAI Model Fetching**
   - Enter valid OpenAI API key
   - Navigate to OpenAI model selection
   - Verify models are fetched and displayed
   - Check that new models (if any) appear in the list

2. **Error Handling**
   - Enter invalid API key
   - Navigate to model selection
   - Verify error message appears
   - Click retry button
   - Verify fallback models are still selectable

3. **Ollama Model Fetching**
   - Set up local Ollama instance
   - Enter Ollama API URL
   - Navigate to Ollama model selection
   - Verify locally installed models appear

4. **Network Timeout**
   - Disable network connection
   - Navigate to model selection
   - Verify timeout is handled gracefully
   - Verify fallback models work

## Future Enhancements

1. **Caching**: Implement persistent caching to avoid repeated API calls
2. **Background Refresh**: Periodically refresh model lists in the background
3. **Model Metadata**: Display more detailed model information (context window, pricing, etc.)
4. **Model Search**: Add search/filter functionality for providers with many models
5. **Model Comparison**: Allow users to compare model capabilities side-by-side

## Configuration

### Timeout Settings
- Network timeout: 5 minutes (configured in NetworkClient.kt)
- Suitable for slower connections and larger model lists

### Error Messages
All error messages are localized:
- `fetching_models` - "Fetching models…"
- `using_fallback_models` - "Using fallback models"
- `retry` - "Retry"

## Technical Notes

### Kotlin Coroutines
- All network operations run on `Dispatchers.IO`
- StateFlow ensures thread-safe state management
- LaunchedEffect manages lifecycle-aware fetching

### Ktor Client
- Uses existing NetworkClient with JSON serialization
- Automatic error handling and retry logic
- Supports multiple transport mechanisms

### Dependency Injection
- ModelFetchService is provided as singleton via Hilt
- Automatically injected into SetupViewModel
- Easy to mock for testing

## Troubleshooting

### Models Not Fetching
1. Verify API key is valid and has proper permissions
2. Check network connectivity
3. Verify API URL is correct
4. Check Ktor client logs for detailed error messages

### Wrong Models Displayed
1. Ensure fetched models have higher priority than fallback
2. Check provider-specific filtering logic
3. Verify model response parsing

### Performance Issues
1. Consider implementing caching
2. Reduce network timeout if needed
3. Implement pagination for providers with many models

## API Endpoints Reference

| Provider | Endpoint | Authentication |
|----------|----------|----------------|
| OpenAI | `{apiUrl}/models` | Bearer {api_key} |
| Groq | `{apiUrl}/models` | Bearer {api_key} |
| Google | `{apiUrl}/v1beta/models?key={api_key}` | Query parameter |
| Anthropic | N/A (hardcoded) | N/A |
| Ollama | `{apiUrl}/api/tags` | None |

## Version History

- **1.0.0** (2024-11-30): Initial implementation
  - Dynamic model fetching for all providers
  - Loading and error states
  - Fallback to hardcoded models
  - Retry functionality
