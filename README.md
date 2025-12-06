<div align="center">

# MultiGPT

**Chat with multiple AI models at once**

Connect to OpenAI, Claude, Gemini, AWS Bedrock, and more—all from one powerful Android app.

<p>
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Language-Kotlin-purple.svg"/>
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg"/>
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue.svg"/>
</p>

<p>
  <a href="https://play.google.com/store/apps/details?id=com.matrix.multigpt">
    <img alt="Get it on Google Play" src="https://img.shields.io/badge/Get%20it%20on-Google%20Play-brightgreen.svg?logo=googleplay"/>
  </a>
</p>

</div>

## 🚀 What is MultiGPT?

MultiGPT is a native Android app that lets you chat with multiple AI models simultaneously. Instead of switching between different apps and websites, get responses from OpenAI's GPT models, Anthropic's Claude, Google's Gemini, AWS Bedrock's foundation models, and more—all in one conversation.

Perfect for comparing AI responses, getting diverse perspectives, or simply enjoying the convenience of having all your favorite AI models in one place.

## 📱 Screenshots

<div align="center">

|                            Getting Started                            |                         Multi-Model Chat                          |                          Settings                          |
|:---------------------------------------------------------------------:|:----------------------------------------------------------------:|:----------------------------------------------------------:|
| <img src="metadata/en-US/images/phoneScreenshots/1.png" width="200"/> | <img src="metadata/en-US/images/phoneScreenshots/6.png" width="200"/> | <img src="metadata/en-US/images/phoneScreenshots/3.png" width="200"/> |

</div>

## ✨ Why Choose MultiGPT?

### 🎯 **All-in-One AI Experience**
Stop juggling multiple apps and websites. MultiGPT brings together the best AI models in one beautiful, native Android interface.

### 🔒 **Privacy First**  
Your conversations stay on your device. No accounts, no cloud storage, no data collection. You control your API keys and chat history.

### ⚡ **Built for Android**
Native Android app with Material You design, dark mode support, and smooth animations. No web wrappers or compromises.

### 🌍 **Global Ready**
Available in 10+ languages including English, Arabic, Chinese, Korean, Russian, and more. Truly accessible worldwide.

## 🤖 Supported AI Platforms

### **OpenAI** 
*The creators of ChatGPT*
- **Models**: GPT-4o, GPT-4o mini, GPT-4 Turbo, GPT-4
- **Best for**: General tasks, coding, creative writing
- **Dynamic fetching**: Latest models automatically discovered

### **Groq**
*Lightning-fast AI inference*  
- **Models**: Llama 3.1, Llama 3.2, Gemma 2
- **Best for**: Quick responses, real-time applications
- **Speed**: Ultra-fast inference with open-source models

### **AWS Bedrock**
*Enterprise foundation models*
- **Providers**: Anthropic, Amazon, AI21 Labs, Cohere, Meta
- **Models**: 12+ foundation models including Claude, Titan, Jurassic-2, Command, Llama 2
- **Best for**: Enterprise applications, diverse model access
- **Setup**: Simple 4-field credential form with region selection

### **Anthropic**
*Home of Claude*
- **Models**: Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Sonnet, Claude 3 Haiku  
- **Best for**: Long-form writing, analysis, thoughtful responses
- **Strength**: Large context windows, nuanced understanding

### **Google**
*Gemini AI models*
- **Models**: Gemini 1.5 Pro, Gemini 1.5 Flash, Gemini 1.0 Pro
- **Best for**: Fast responses, coding, multimodal tasks
- **Integration**: Direct Google AI integration

### **Ollama**
*Run AI locally*
- **Models**: Any model you can run locally (Llama, Mistral, CodeLlama, etc.)
- **Best for**: Privacy, custom models, offline usage
- **Setup**: Connect to your local Ollama instance

## 📖 Getting Started

### 📋 **Prerequisites**
Before you begin, you'll need:
- Android device with **Android 8.0+** (API level 26+)
- API keys from the platforms you want to use

### 🔑 **Getting API Keys**

**OpenAI** (Most Popular)
1. Visit [OpenAI Platform](https://platform.openai.com/account/api-keys)
2. Create account and add billing information  
3. Generate a new API key
4. Note: Requires prepaid credits

**AWS Bedrock** (Enterprise Models)
1. Create [AWS account](https://aws.amazon.com) and set up IAM user
2. Attach `AmazonBedrockFullAccess` policy
3. Generate Access Key ID and Secret Access Key
4. Choose AWS region (e.g., us-east-1, us-west-2)
5. Request model access in Bedrock console

**Other Providers**
- **Anthropic**: [Console](https://console.anthropic.com/settings/keys) (requires prepaid credits)
- **Google**: [AI Studio](https://aistudio.google.com/app/apikey) (free tier available)
- **Groq**: [Console](https://console.groq.com/keys) (generous free tier)

### 📲 **Installation & Setup**

1. **Install the App**
   - Download from [Google Play Store](https://play.google.com/store/apps/details?id=com.matrix.multigpt)
   - Or get the APK from [GitHub Releases](https://github.com/it5prasoon/MultiGPT/releases)

2. **First Launch Setup**  
   - Select which AI platforms you want to use
   - Enter your API keys securely (stored only on your device)
   - Choose your preferred models
   - Start chatting immediately!

3. **Create Your First Chat**
   - Tap the **+** button to create a new conversation
   - Select which AI models to include
   - Ask your question and get responses from multiple AIs simultaneously

## ⚙️ Advanced Features

### 🎛️ **Fine-Tuning AI Responses**

**Temperature Control**  
Adjust how creative or focused AI responses are:
- **0.0-0.3**: Very focused, consistent answers
- **0.7-1.0**: Balanced creativity (recommended)  
- **1.5-2.0**: Highly creative, diverse responses

**Top-p Sampling**  
Control response diversity alternative to temperature:
- **0.1-0.5**: More focused vocabulary
- **0.9-1.0**: Full vocabulary range

**System Prompts**  
Define AI behavior and personality:
- Set specific roles (e.g., "You are a helpful coding assistant")
- Provide context and guidelines
- Different prompts for different use cases

### 📊 **Model Management**  

**Dynamic Model Discovery**
The app automatically fetches the latest available models from each provider, ensuring you always have access to the newest AI capabilities without app updates.

**Smart Fallback System**  
If dynamic fetching fails, the app uses curated model lists to ensure uninterrupted service.

**Custom Models**  
For advanced users, enter custom model names that aren't in the standard lists.

### 🎨 **Personalization**

**Material You Theming**  
The app automatically adapts to your device's color scheme, creating a cohesive experience that feels native to your Android device.

**Multi-Language Support**  
Choose from 10+ languages including Arabic, Chinese, Korean, Russian, Portuguese, Hebrew, Tamil, Turkish, and more.

## 🛡️ Privacy & Security

### **Your Data, Your Control**

MultiGPT is built with privacy as a core principle. Here's what that means:

**Local Storage Only**
- All chat conversations are stored locally on your device
- No cloud synchronization or backup (by design)
- Your conversations never leave your device unless you explicitly export them

**No Data Collection**  
- No analytics or tracking
- No user accounts or profiles
- No telemetry or usage statistics
- Zero data sent to our servers

**Direct API Communication**
- Your API keys communicate directly with AI providers
- MultiGPT acts only as a client interface
- No proxy servers or middleware collecting your data

**Open Source Transparency**
- Full source code available on GitHub
- Audit the code yourself or have security experts review it
- Build the app yourself for complete control

## 🏗️ Technical Architecture

### **Modern Android Development**

MultiGPT is built using the latest Android development best practices:

**Architecture Components**
- **MVVM Pattern**: Clean separation between UI and business logic
- **Single Activity**: Modern navigation with Jetpack Navigation
- **Kotlin 100%**: Fully written in Kotlin for safety and conciseness
- **Jetpack Compose**: Declarative UI framework for smooth, responsive interfaces

**Networking & Data**
- **Ktor Client**: Robust HTTP client for API communications
- **Room Database**: Efficient local data persistence
- **DataStore**: Modern preferences management
- **Kotlin Coroutines**: Smooth async operations without blocking

**Quality Assurance**
- **Hilt Dependency Injection**: Testable, scalable architecture
- **Unit Tests**: Critical functionality covered by tests
- **Static Analysis**: Code quality maintained with linting tools
- **Material Design 3**: Follows Google's latest design guidelines

## 🤝 Contributing

We'd love your help in making MultiGPT even better!

### **Quick Ways to Help**
- ⭐ **Star the repository** to show support
- 🐛 **Report bugs** you encounter
- 💡 **Suggest features** you'd like to see
- 🌍 **Help translate** into your language
- 📖 **Improve documentation**

### **For Developers**

**Getting Started**
```bash
git clone https://github.com/it5prasoon/MultiGPT.git
cd MultiGPT
# Open in Android Studio and build
```

**Development Guidelines**
- Follow Kotlin coding conventions
- Write meaningful commit messages  
- Test your changes thoroughly
- Update documentation as needed

**Pull Request Process**
1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request with clear description

## 📞 Support & Community

### **Download MultiGPT**
- 📱 **Google Play Store**: [Download MultiGPT](https://play.google.com/store/apps/details?id=com.matrix.multigpt)
- 🔗 **GitHub Releases**: [Get APK directly](https://github.com/it5prasoon/MultiGPT/releases)

### **Need Help?**
- 💬 **GitHub Discussions**: [Ask questions and share ideas](https://github.com/it5prasoon/MultiGPT/discussions)
- 🐛 **Bug Reports**: [Report issues on GitHub](https://github.com/it5prasoon/MultiGPT/issues)
- ⭐ **GitHub**: [View source code and contribute](https://github.com/it5prasoon/MultiGPT)

### **Community Guidelines**
- Be respectful and constructive
- Help others learn and grow
- Share your use cases and tips
- Contribute positively to discussions

## 📄 License

This project is open source and available under the MIT License. See the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Built with ❤️ for the Android community**

*Connect to the future of AI, today.*

[⬆ Back to Top](#multigpt)

</div>
