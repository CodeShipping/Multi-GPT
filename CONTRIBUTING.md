# Contributing to MultiGPT

We love your input! We want to make contributing to MultiGPT as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## Development Process

We use GitHub to host code, to track issues and feature requests, as well as accept pull requests.

## Pull Requests

Pull requests are the best way to propose changes to the codebase. We actively welcome your pull requests:

1. **Fork the repository** and create your branch from `main`.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/yourusername/MultiGPT.git
   cd MultiGPT
   ```
3. **Set up the development environment**:
   - Install Android Studio Hedgehog or newer
   - Open the project in Android Studio
   - Let Gradle sync complete
4. **Create a feature branch**:
   ```bash
   git checkout -b feature/amazing-feature
   ```
5. **Make your changes** and ensure they follow our coding standards
6. **Test thoroughly** on different Android versions and devices
7. **Commit your changes** with clear, descriptive messages:
   ```bash
   git commit -m 'Add amazing feature: brief description'
   ```
8. **Push to your fork**:
   ```bash
   git push origin feature/amazing-feature
   ```
9. **Create a Pull Request** from your branch to our `main` branch

## Code Style

### Kotlin Guidelines
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions small and focused
- Use proper indentation (4 spaces)
- Use trailing commas in multi-line structures

### Android & Compose Guidelines
- Follow [Android Architecture Guidelines](https://developer.android.com/guide/architecture)
- Use [Jetpack Compose best practices](https://developer.android.com/jetpack/compose/performance)
- Follow Material Design 3 principles
- Optimize for different screen sizes and densities
- Use proper resource naming conventions

### Example Code Style
```kotlin
/**
 * Repository for managing AI provider configurations
 */
class AiProviderRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val providerDao: AiProviderDao,
) {
    
    companion object {
        private const val MAX_RETRY_COUNT = 3
    }
    
    /**
     * Fetches all enabled AI providers
     * @return Flow of enabled providers
     */
    fun getEnabledProviders(): Flow<List<AiProvider>> {
        return providerDao.getEnabledProviders()
            .catch { exception ->
                Logger.e("Failed to fetch providers", exception)
                emit(emptyList())
            }
    }
}

@Composable
fun ChatMessage(
    message: Message,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

## Architecture Guidelines

### Project Structure
```
app/src/main/kotlin/com/matrix/multigpt/
├── data/                 # Data layer
│   ├── local/           # Room database, DataStore
│   ├── remote/          # API services, DTOs
│   └── repository/      # Repository implementations
├── domain/              # Business logic
│   ├── model/           # Domain models
│   ├── repository/      # Repository interfaces  
│   └── usecase/         # Use cases
├── presentation/        # UI layer
│   ├── ui/              # Composable screens
│   ├── viewmodel/       # ViewModels
│   └── navigation/      # Navigation logic
├── di/                  # Dependency injection
└── utils/               # Utility classes
```

### Architecture Patterns
- **MVVM**: Use ViewModel for UI state management
- **Repository Pattern**: Centralize data access logic
- **Use Cases**: Encapsulate business logic
- **Dependency Injection**: Use Hilt for DI
- **Single Activity**: Navigate with Jetpack Navigation

## Testing

### Testing Requirements
- Write unit tests for ViewModels and repositories
- Add integration tests for critical flows
- Test on different Android versions (API 26+)
- Test with different AI providers
- Verify UI responsiveness and accessibility

### Running Tests
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run all tests with coverage
./gradlew jacocoTestReport
```

### Testing Guidelines
```kotlin
@Test
fun `getAiProviders returns enabled providers only`() = runTest {
    // Given
    val providers = listOf(
        AiProvider(id = "openai", isEnabled = true),
        AiProvider(id = "claude", isEnabled = false),
    )
    coEvery { providerDao.getAllProviders() } returns flowOf(providers)
    
    // When
    val result = repository.getEnabledProviders().first()
    
    // Then
    assertEquals(1, result.size)
    assertEquals("openai", result.first().id)
}
```

## Reporting Bugs

We use GitHub Issues to track bugs. Report a bug by [opening a new issue](https://github.com/it5prasoon/MultiGPT/issues/new?template=bug_report.md).

**Great Bug Reports** tend to have:

- A quick summary and/or background
- Steps to reproduce (be specific!)
- Sample code if applicable
- What you expected would happen
- What actually happens
- Device information (Android version, device model)
- App version
- Screenshots or screen recordings

### Bug Report Template
```markdown
**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Tap on '....'
3. See error

**Expected behavior**
What you expected to happen.

**Device Information:**
 - Device: [e.g. Samsung Galaxy S23]
 - OS: [e.g. Android 14]
 - App Version: [e.g. v1.2.0]
 - AI Provider: [e.g. OpenAI, Claude]

**Additional context**
Screenshots, logs, or other relevant information.
```

## Feature Requests

We also use GitHub Issues for feature requests. You can [request a feature](https://github.com/it5prasoon/MultiGPT/issues/new?template=feature_request.md).

**Great Feature Requests** include:

- Clear problem description
- Proposed solution
- Alternative solutions considered
- Use cases and benefits
- Mockups or examples (if applicable)

## AI Provider Integration

### Adding New AI Providers

When adding support for a new AI provider:

1. **Create the API client** in `data/remote/`
2. **Add data models** for requests/responses
3. **Implement repository** with proper error handling
4. **Add UI configuration** for API keys/settings
5. **Write comprehensive tests**
6. **Update documentation**

### API Integration Guidelines
```kotlin
@Serializable
data class NewProviderRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 1.0,
)

interface NewProviderApi {
    @POST("chat/completions")
    suspend fun createCompletion(
        @Body request: NewProviderRequest,
    ): NewProviderResponse
}
```

## Security Considerations

- **Never commit API keys** or sensitive data
- **Validate all user inputs** before API calls
- **Use secure storage** for API keys (EncryptedSharedPreferences)
- **Handle network errors** gracefully
- **Implement rate limiting** to prevent abuse
- **Follow OWASP Mobile Security** guidelines

## Documentation

### Documentation Requirements
- Update README.md for new features
- Add KDoc comments for public APIs
- Include code examples in complex implementations
- Document API integration steps
- Update CHANGELOG.md

### Writing Good Documentation
```kotlin
/**
 * Manages chat conversations with multiple AI providers
 * 
 * @param providers List of enabled AI providers
 * @param chatRepository Repository for chat persistence
 * 
 * Example usage:
 * ```
 * val chatManager = ChatManager(providers, repository)
 * val response = chatManager.sendMessage("Hello", listOf("openai", "claude"))
 * ```
 */
class ChatManager(
    private val providers: List<AiProvider>,
    private val chatRepository: ChatRepository,
) {
    // Implementation
}
```

## Code Review Process

All submissions require review before merging:

1. **Automated checks** must pass (build, tests, linting)
2. **Manual review** by maintainers focuses on:
   - Code quality and architecture
   - Security considerations
   - Performance impact
   - User experience
   - Documentation completeness

## Community Guidelines

- Be respectful and inclusive
- Help others learn and grow
- Follow our [Code of Conduct](CODE_OF_CONDUCT.md)
- Join discussions in issues and pull requests
- Share knowledge and best practices

## Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34+ (API level 34)
- Kotlin 1.9+
- JDK 17+
- Git

### Local Development
1. Clone the repository
2. Open in Android Studio
3. Let Gradle sync complete
4. Build and run on device/emulator
5. Start contributing!

### Environment Variables
For testing, you may want to add API keys to `local.properties`:
```properties
OPENAI_API_KEY=your_key_here
ANTHROPIC_API_KEY=your_key_here
# These are for testing only - never commit real keys!
```

## Release Process

### Version Numbering
- **Major.Minor.Patch** (e.g., 1.2.3)
- **Major**: Breaking changes or major features
- **Minor**: New features, backward compatible
- **Patch**: Bug fixes and small improvements

### Release Checklist
- [ ] All tests pass
- [ ] Version number updated
- [ ] CHANGELOG.md updated
- [ ] Play Store metadata updated
- [ ] Screenshots updated if needed
- [ ] Release notes written

## Recognition

Contributors will be recognized in:
- README.md contributors section
- Release notes for significant contributions
- GitHub contributors page
- Special mentions in app credits

## Questions?

Feel free to:
- Open a discussion issue
- Join GitHub Discussions
- Contact the maintainers
- Review existing documentation

Thank you for contributing to MultiGPT! 🤖✨
