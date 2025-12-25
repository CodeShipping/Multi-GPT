# Support

Welcome to MultiGPT support! We're here to help you get the most out of your multi-AI chat experience.

## 📚 Documentation

### Quick Start
1. **Download** the app from [Google Play Store](https://play.google.com/store/apps/details?id=com.matrix.multigpt)
2. **Set up AI providers** - Add API keys for the providers you want to use
3. **Create a conversation** - Start chatting with multiple AI models at once
4. **Customize settings** - Adjust temperature, prompts, and preferences

### User Guides

#### Basic Setup
- [Installation Guide](README.md#-getting-started)
- [API Key Setup](README.md#-getting-api-keys)
- [First Conversation](README.md#-installation--setup)

#### AI Provider Configuration
- **OpenAI**: Get API key from [OpenAI Platform](https://platform.openai.com/account/api-keys)
- **Anthropic**: Get API key from [Anthropic Console](https://console.anthropic.com/settings/keys)
- **Google**: Get API key from [AI Studio](https://aistudio.google.com/app/apikey)
- **Groq**: Get API key from [Groq Console](https://console.groq.com/keys)
- **AWS Bedrock**: Configure Access Key, Secret Key, and Region
- **Ollama**: Connect to your local Ollama instance

#### Advanced Features
- **Temperature Control**: Adjust AI creativity (0.0-2.0)
- **System Prompts**: Customize AI behavior and personality
- **Model Selection**: Choose specific models for each conversation
- **Multi-Language**: Switch between 10+ supported languages

## 🆘 Getting Help

### Before Asking for Help

Please check these common solutions first:

#### App Not Working
1. ✅ **Check API keys** - Ensure they are valid and properly entered
2. ✅ **Update app** - Make sure you have the latest version
3. ✅ **Check internet** - Verify your connection is stable
4. ✅ **Restart app** - Force close and reopen MultiGPT
5. ✅ **Restart device** - Sometimes a device restart helps

#### AI Responses Not Working
1. ✅ **Valid API keys** - Check if your API keys are correct and active
2. ✅ **Provider status** - Verify AI provider services are online
3. ✅ **Account credits** - Ensure you have sufficient credits (OpenAI, Anthropic)
4. ✅ **Model access** - Some models require special access (AWS Bedrock)
5. ✅ **Rate limits** - You might be hitting API rate limits

#### Conversations Not Loading
1. ✅ **Storage space** - Ensure device has sufficient storage
2. ✅ **App permissions** - Check if storage permissions are granted
3. ✅ **Database corruption** - Try clearing app cache
4. ✅ **Large conversations** - Very long chats might load slowly

## 💬 Support Channels

### Primary Support
📧 **Email**: [prasoonkumar008@gmail.com](mailto:prasoonkumar008@gmail.com)
- **Response time**: 24-48 hours
- **Best for**: Bug reports, feature requests, account issues

### Community Support
🐛 **GitHub Issues**: [Report Issues](https://github.com/it5prasoon/MultiGPT/issues)
- **Best for**: Bug reports, feature discussions
- **Public**: Help others with similar issues

💬 **GitHub Discussions**: [Community Forum](https://github.com/it5prasoon/MultiGPT/discussions)
- **Best for**: Questions, tips, general discussion
- **Community-driven**: Get help from other users

### Social Media
📱 **Google Play**: [Leave a Review](https://play.google.com/store/apps/details?id=com.matrix.multigpt)

## 🐛 Reporting Bugs

When reporting a bug, please include:

### Essential Information
- **App version**: Found in Settings → About
- **Android version**: e.g., Android 14
- **Device model**: e.g., Samsung Galaxy S23
- **AI providers affected**: Which providers have issues
- **Steps to reproduce**: Detailed step-by-step instructions
- **Expected vs actual behavior**: What should happen vs what happens
- **Screenshots**: If applicable

### Bug Report Template
```markdown
**Bug Description**
Brief description of the issue

**Steps to Reproduce**
1. Open MultiGPT
2. Go to...
3. Tap on...
4. See error

**Expected Behavior**
What should happen

**Actual Behavior**
What actually happens

**Device Info**
- Device: [e.g., Pixel 7]
- Android: [e.g., 14]
- App Version: [e.g., 1.0.0]
- AI Provider: [e.g., OpenAI, Claude]

**Additional Context**
Any other relevant information, logs, or screenshots
```

## 🚀 Feature Requests

We welcome feature suggestions! When requesting a feature:

1. **Check existing requests** - Search GitHub issues first
2. **Describe the use case** - What problem does this solve?
3. **Propose a solution** - How should it work?
4. **Consider impact** - How would this benefit other users?

### Popular Feature Requests
- Voice input and text-to-speech
- Conversation folders and organization
- Export conversations to PDF/Markdown
- Custom AI model fine-tuning
- Collaborative conversations
- Advanced prompt templates

## 🔧 Troubleshooting

### Common Issues & Solutions

#### ❌ "Invalid API Key" Error
**Solutions**:
- Verify API key is copied correctly (no extra spaces)
- Check if API key has proper permissions
- Ensure billing is set up (OpenAI, Anthropic)
- Regenerate API key if needed

#### ❌ "Model not found" Error
**Solutions**:
- Check if you have access to the specific model
- Try using dynamic model fetching in settings
- Use a different model that you have access to
- Contact AI provider support for model access

#### ❌ App crashes when starting conversation
**Solutions**:
- Clear app cache: Settings → Apps → MultiGPT → Storage → Clear Cache
- Ensure sufficient device storage
- Update to latest app version
- Restart device and try again

#### ❌ Slow or no responses from AI
**Solutions**:
- Check internet connection stability
- Verify AI provider service status
- Try reducing conversation length
- Switch to a different AI model

#### ❌ "Rate limit exceeded" Error
**Solutions**:
- Wait a few minutes before trying again
- Upgrade your API plan with the provider
- Use different AI providers to spread requests
- Reduce conversation frequency

### Advanced Troubleshooting

#### API Provider Status Pages
Check if services are operational:
- **OpenAI**: [status.openai.com](https://status.openai.com)
- **Anthropic**: [status.anthropic.com](https://status.anthropic.com)
- **Google**: [status.cloud.google.com](https://status.cloud.google.com)

#### Debug Information
To get debug information:
1. Enable developer options on your device
2. Use `adb logcat` to capture logs
3. Filter for MultiGPT: `adb logcat | grep com.matrix.multigpt`
4. Include relevant logs in bug reports

#### Network Diagnostics
If experiencing connection issues:
1. Test with different networks (WiFi vs mobile data)
2. Check if firewall/proxy is blocking requests
3. Try disabling VPN temporarily
4. Verify DNS resolution is working

## 📖 FAQ

### General Questions

**Q: Is MultiGPT really free?**
A: Yes! The app is completely free and open source. You only pay for the AI provider APIs you choose to use.

**Q: Do I need all AI providers?**
A: No! You can use any combination of providers. Start with one and add others as needed.

**Q: Does MultiGPT collect my data?**
A: No. All conversations are stored locally on your device. We don't collect or transmit your personal data.

**Q: Can I use MultiGPT offline?**
A: No, AI providers require internet connection. However, your conversation history is stored locally.

### Technical Questions

**Q: Which Android versions are supported?**
A: Android 8.0 (API 26) and above.

**Q: Does MultiGPT work on tablets?**
A: Yes! The app is optimized for both phones and tablets.

**Q: Can I backup my conversations?**
A: Currently, conversations are stored locally. Export features are planned for future releases.

**Q: How secure are my API keys?**
A: API keys are encrypted using Android Keystore, the most secure storage available on Android.

### AI Provider Questions

**Q: Why do I get different responses from different AI models?**
A: Each AI model has different training data, capabilities, and personalities, leading to varied responses.

**Q: Can I use custom models with MultiGPT?**
A: Currently, you can use Ollama for local models. Custom cloud model support is planned.

**Q: Which AI provider is best for coding?**
A: OpenAI's GPT-4 and Anthropic's Claude are excellent for coding. Groq offers fast responses for quick queries.

## 🤝 Community Guidelines

When seeking or providing support:
- ✅ Be respectful and patient
- ✅ Search before asking
- ✅ Provide detailed information
- ✅ Help others when possible
- ✅ Follow our [Code of Conduct](CODE_OF_CONDUCT.md)

## 📱 System Requirements

- **Android**: 8.0+ (API level 26)
- **RAM**: 4GB+ recommended
- **Storage**: 100MB free space
- **Internet**: Required for AI provider communication
- **Permissions**: Network access, storage (for local conversations)

## 🎯 Feature Roadmap

See our [CHANGELOG.md](CHANGELOG.md) for:
- Recent updates and new features
- Planned improvements
- Version history
- Development milestones

## 💝 Support the Project

If MultiGPT has helped you, consider:
- ⭐ **Star the repository** on GitHub
- 📝 **Write a review** on Google Play Store
- 🤝 **Contribute**: See [CONTRIBUTING.md](CONTRIBUTING.md)
- 💬 **Join discussions** and help other users
- 🐛 **Report bugs** to improve the app

## 📞 Contact Information

- **Developer**: it5prasoon
- **Email**: [prasoonkumar008@gmail.com](mailto:prasoonkumar008@gmail.com)
- **GitHub**: [@it5prasoon](https://github.com/it5prasoon)
- **Project**: [MultiGPT](https://github.com/it5prasoon/MultiGPT)

## 🔗 Useful Links

- **Download**: [Google Play Store](https://play.google.com/store/apps/details?id=com.matrix.multigpt)
- **Source Code**: [GitHub Repository](https://github.com/it5prasoon/MultiGPT)
- **Issues**: [Bug Reports & Feature Requests](https://github.com/it5prasoon/MultiGPT/issues)
- **Discussions**: [Community Forum](https://github.com/it5prasoon/MultiGPT/discussions)

---

**Last Updated**: December 2024

Thank you for using MultiGPT! We're committed to making your multi-AI experience exceptional. 🤖✨
