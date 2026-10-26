# Large-Language-Meals

## Quick Calorie Logger

This repository contains a single-screen Jetpack Compose Android app that sends a
plaintext meal description to a selectable Gemini model and writes the parsed
nutrition values to Android Health Connect. A single request can contain multiple
meals, such as lunch and dinner, and each is logged as a separate record.

### Setup

1. Open the project in Android Studio with an Android SDK that includes API 35.
2. Open the gitignored `secrets.properties` file at the project root and paste
   your key:
   ```properties
   GEMINI_API_KEY=your-key-here
   ```
   Do not commit a real key. If the file is absent, the app builds with a
   placeholder and reports a configuration error when a meal is submitted.
3. Run the app on an Android 14+ device or emulator with Health Connect
   available, then grant the nutrition write permission when prompted.

### Meal dates and multiple meals

Choose a model from the model dropdown, then optionally provide a natural-language
date/time hint such as `Today at 12:30 for lunch and 19:00 for dinner`. Describe
both meals in the meal field. Gemini resolves each meal to an ISO-8601 timestamp,
and the app writes each meal separately using that timestamp. If no hint is
provided, Gemini uses the current date and inferred times.

The implementation is split between:

- `HealthConnectManager.kt`: permission checks and `NutritionRecord` insertion.
- `GeminiRepository.kt`: Gemini REST request and response parsing.
- `MainActivity.kt`: Compose UI and permission/request state flow.