# Agora

**Agora** is a sophisticated, modern Android LLM (Large Language Model) chat client built with **Jetpack Compose** and **Kotlin**. It is designed for advanced AI interactions, featuring a robust architecture that supports branching conversation paths and integrated agentic tools.

## Key Features

- **Branching Conversation History**: Unlike linear chat apps, Agora supports message branching (tree structure) via a parent-ID based Room database schema, allowing users to explore multiple conversation paths from a single prompt.
- **Precise UI Padding & Auto-Scroll**: Refined custom logic for stable message anchoring and smooth, 800ms animated auto-scroll that adapts to both user input and AI streaming, even with the IME (keyboard) raised.
- **Integrated AI Tools**: Built-in support for advanced agentic capabilities like **Code Execution** and **Google Search** integration.
- **Token & Context Management**: Real-time token counting and configurable context window limits for optimized model usage.
- **Modern Android Architecture**: 
  - **Jetpack Compose** for a fully reactive, single-activity UI.
  - **MVVM Pattern** for clean separation of concerns.
  - **Kotlin Coroutines & Flow** for efficient asynchronous operations.
  - **Room Database & DataStore** for persistent message history and application settings.

## Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- Android SDK 34+
- A valid Gemini API Key (or other supported LLM provider)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/newo-ether/Agora.git
   ```
2. Open the project in Android Studio.
3. Build and run on an emulator or physical device.
4. Enter your API key in the **Settings** menu.

## Technologies Used

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room
- **State Management**: ViewModel & Flow
- **Navigation**: Jetpack Navigation
- **Styling**: Material 3
- **Markdown**: Support for rich AI replies

## License

This project is licensed under the MIT License - see the LICENSE file for details.
