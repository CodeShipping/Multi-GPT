# Security Policy

## Supported Versions

We release patches for security vulnerabilities. Which versions are eligible for receiving such patches depends on the CVSS v3.0 Rating:

| Version | Supported          |
| ------- | ------------------ |
| Latest  | ✅ Yes             |
| Previous major | ✅ Yes       |
| < Previous major | ❌ No      |

## Reporting a Vulnerability

The MultiGPT team takes security bugs seriously. We appreciate your efforts to responsibly disclose your findings, and will make every effort to acknowledge your contributions.

### How to Report

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:

📧 **Email**: [prasoonkumar008@gmail.com](mailto:prasoonkumar008@gmail.com)

**Subject Line**: `[SECURITY] Brief description of vulnerability`

### What to Include

Please include the following information along with your report:

- Type of issue (e.g., data exposure, credential theft, code injection, etc.)
- Full paths of source file(s) related to the manifestation of the issue
- The location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit the issue

This information will help us triage your report more quickly.

### Response Timeline

- **Initial Response**: We aim to respond to security reports within 48 hours
- **Updates**: We will send you regular updates about our progress at least every 7 days
- **Resolution**: We target resolving critical vulnerabilities within 30 days
- **Disclosure**: Once fixed, we will work with you on coordinated disclosure

### Safe Harbor

We support safe harbor for security researchers who:

- Make a good faith effort to avoid privacy violations, destruction of data, and interruption or degradation of our services
- Only interact with accounts you own or with explicit permission of the account holder
- Do not access a system beyond what is necessary to demonstrate a vulnerability
- Report vulnerabilities as soon as you discover them
- Do not violate any other applicable laws or regulations

## Security Considerations

### For Users

- **Keep the app updated**: Always use the latest version from Google Play Store
- **Protect your API keys**: Never share your AI provider API keys with others
- **Review permissions**: The app only requests necessary permissions
- **Use secure networks**: Avoid using public WiFi for sensitive conversations
- **Local data only**: Your conversations are stored locally and never transmitted to our servers

### For Developers

- **API Key Security**: Use EncryptedSharedPreferences for storing API keys
- **Input Validation**: Always validate and sanitize user inputs
- **Network Security**: Use HTTPS for all AI provider communications
- **Secure Coding**: Follow OWASP Mobile Security guidelines
- **Code Review**: All code changes require security review before merging

## Known Security Features

### Privacy Protection
- **Local Storage**: All conversations are stored locally on your device
- **No Data Collection**: We don't collect, store, or transmit your personal data
- **API Key Encryption**: API keys are encrypted using Android Keystore
- **Direct API Communication**: Your data goes directly to AI providers, not through our servers
- **Minimal Permissions**: Only requests permissions necessary for functionality

### AI Provider Security
- **Secure API Calls**: All API communications use HTTPS/TLS encryption
- **Rate Limiting**: Built-in rate limiting to prevent API abuse
- **Error Handling**: Secure error handling that doesn't expose sensitive information
- **Token Management**: Secure handling of authentication tokens and API keys

### Application Security
- **Code Obfuscation**: Release builds use ProGuard/R8 obfuscation
- **Certificate Pinning**: SSL certificate pinning for critical API endpoints
- **Runtime Checks**: Application integrity verification
- **Secure Storage**: Sensitive data stored using Android security best practices

## Security Updates

Security updates will be released as patch versions and announced through:

- GitHub Releases with security advisories
- Google Play Store app updates
- Project README updates
- Security mailing list (for critical issues)

## Vulnerability Disclosure Process

1. **Report received**: We acknowledge receipt within 48 hours
2. **Initial assessment**: We perform initial validation (1-7 days)
3. **Investigation**: We investigate and develop a fix (7-30 days)
4. **Fix deployment**: We deploy the fix and notify you
5. **Public disclosure**: We coordinate public disclosure with you

## AI Provider Security Guidelines

### API Key Management
- Store API keys securely using EncryptedSharedPreferences
- Never log API keys in debug output
- Allow users to easily revoke and rotate keys
- Validate API key format before making requests

### Network Security
```kotlin
// Example secure API client configuration
val httpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
    }
    
    engine {
        config {
            sslSocketFactory(createSecureSSLSocketFactory())
            certificatePinner(createCertificatePinner())
        }
    }
}
```

### Data Handling
- Minimize data retention (automatic conversation cleanup)
- Encrypt sensitive data at rest
- Use secure random number generation for IDs/tokens
- Validate all responses from AI providers

## Security Best Practices for Contributors

### Code Security
- Never commit API keys, passwords, or sensitive data
- Use parameterized queries for database operations
- Validate all user inputs before processing
- Implement proper error handling without information disclosure
- Use secure random number generation

### Review Checklist
Before submitting code, ensure:
- [ ] No hardcoded secrets or API keys
- [ ] Input validation implemented
- [ ] Error handling doesn't expose sensitive information
- [ ] Network calls use HTTPS
- [ ] Sensitive data uses secure storage
- [ ] No debug information in release builds

## Security Hall of Fame

We recognize security researchers who help improve MultiGPT's security:

<!-- Future contributors will be listed here -->

Thank you for helping keep MultiGPT and our users safe! 🔒

## Contact

For security-related questions or concerns:

- **Email**: [prasoonkumar008@gmail.com](mailto:prasoonkumar008@gmail.com)
- **Subject**: `[SECURITY] Your question here`

For general questions, please use GitHub Issues or regular support channels.

---

**Last Updated**: December 2024
