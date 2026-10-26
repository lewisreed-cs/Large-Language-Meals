package com.example.quickcalorielogger

import com.example.quickcalorielogger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun parseMeal(description: String): ParsedMeal = withContext(Dispatchers.IO) {
        require(BuildConfig.GEMINI_API_KEY != "YOUR_GEMINI_API_KEY") {
            "Set GEMINI_API_KEY in app/build.gradle.kts before logging a meal."
        }

        val prompt = """
            Parse this plaintext meal description into nutritional estimates.
            Reply strictly with raw JSON only (no Markdown backticks or extra text), using exactly:
            {
              "mealName": "String",
              "mealType": "MEAL_TYPE_BREAKFAST | MEAL_TYPE_LUNCH | MEAL_TYPE_DINNER | MEAL_TYPE_SNACK",
              "calories": 0.0,
              "proteinGrams": 0.0,
              "carbGrams": 0.0,
              "fatGrams": 0.0
            }
            Meal description: $description
        """.trimIndent()

        val systemInstruction = """
            You are a nutrition parser. Reply strictly with raw JSON only, with no Markdown
            backticks or extra text. Use exactly these fields:
            mealName (string), mealType (one of MEAL_TYPE_BREAKFAST, MEAL_TYPE_LUNCH,
            MEAL_TYPE_DINNER, MEAL_TYPE_SNACK), calories (number), proteinGrams (number),
            carbGrams (number), and fatGrams (number).
        """.trimIndent()
        val requestJson = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Gemini request failed (${response.code}): ${response.body?.string().orEmpty()}")
            }
            val text = JSONObject(response.body?.string().orEmpty())
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(text)
            ParsedMeal(
                mealName = json.getString("mealName"),
                mealType = json.getString("mealType"),
                calories = json.getDouble("calories"),
                proteinGrams = json.getDouble("proteinGrams"),
                carbGrams = json.getDouble("carbGrams"),
                fatGrams = json.getDouble("fatGrams")
            )
        }
    }
}
