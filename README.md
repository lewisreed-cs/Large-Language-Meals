# Large-Language-Meals

## Large Language Meals

This repository contains a single-screen Jetpack Compose Android app called
Large Language Meals. It sends plaintext meal descriptions to a selectable
Gemini model and writes the parsed nutrition values to Android Health Connect.
A single request can contain multiple meals, such as lunch and dinner, and each
is logged as a separate record.

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
date with the date picker. Describe the meal type in the text, such as
`Lunch: chicken salad. Dinner: salmon with rice.` The selected date is applied
to every meal returned by Gemini, and the app writes each meal separately using
that date. Health Connect receives a short midday interval because it requires
both a start and end instant while the app intentionally does not collect a time.

### Saved foods

Use the **Saved foods** table below the meal description and its `+` button to
save reusable foods with calories, protein, fat, and carbohydrate values. The
table scrolls independently when it contains many rows. While typing a meal,
enter `@` followed by a food name to see matching inline suggestions; the
keyboard stays open and the suggestions narrow as you type. Selecting
`@proteinwraps`, for example, inserts the token into the description; before
Gemini receives the request, it expands to
`protein wraps (100kcal, 10g protein, 2g fat, 20g carbs)`.

The implementation is split between:

- `HealthConnectManager.kt`: permission checks and `NutritionRecord` insertion.
- `GeminiRepository.kt`: Gemini REST request and response parsing.
- `MainActivity.kt`: Compose UI and permission/request state flow.