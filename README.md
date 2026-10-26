# Large-Language-Meals

## Quick Calorie Logger

This repository contains a single-screen Jetpack Compose Android app that sends a
plaintext meal description to Gemini (`gemini-2.5-flash`) and writes the parsed
nutrition values to Android Health Connect.

### Setup

1. Open the project in Android Studio with an Android SDK that includes API 35.
2. Set `GEMINI_API_KEY` in `app/build.gradle.kts` (or replace the placeholder
   through your build configuration). Do not commit a real key.
3. Run the app on an Android 14+ device or emulator with Health Connect
   available, then grant the nutrition write permission when prompted.

The implementation is split between:

- `HealthConnectManager.kt`: permission checks and `NutritionRecord` insertion.
- `GeminiRepository.kt`: Gemini REST request and response parsing.
- `MainActivity.kt`: Compose UI and permission/request state flow.