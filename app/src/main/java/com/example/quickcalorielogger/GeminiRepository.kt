package com.example.quickcalorielogger

import com.example.quickcalorielogger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class GeminiRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    suspend fun parseMeals(
        description: String,
        dateTimeHint: String,
        model: GeminiModel
    ): List<ParsedMeal> = withContext(Dispatchers.IO) {
        require(BuildConfig.GEMINI_API_KEY != "YOUR_GEMINI_API_KEY") {
            "Set GEMINI_API_KEY in app/build.gradle.kts before logging a meal."
        }

        val prompt = """
            Parse every meal in this plaintext description. The user may describe multiple
            meals, such as lunch and dinner, and each must be returned as a separate array item.
            Resolve rough or relative dates and times using the date/time hint and current time.
            Reply strictly with a raw JSON array only (no Markdown backticks or extra text), using:
            [
              {
                "mealName": "String",
                "mealType": "MEAL_TYPE_BREAKFAST | MEAL_TYPE_LUNCH | MEAL_TYPE_DINNER | MEAL_TYPE_SNACK",
                "mealDateTime": "ISO-8601 instant with timezone offset",
                "calories": 0.0,
                "proteinGrams": 0.0,
                "carbGrams": 0.0,
                "fatGrams": 0.0
              }
            ]
            Current date/time: ${OffsetDateTime.now()}
            User date/time hint: ${dateTimeHint.ifBlank { "Use the current date and infer times from the description." }}
            Meal description: $description
        """.trimIndent()

        val systemInstruction = """
            You are a nutrition parser. Reply strictly with raw JSON only, with no Markdown
            backticks or extra text. Use exactly these fields:
            mealName (string), mealType (one of MEAL_TYPE_BREAKFAST, MEAL_TYPE_LUNCH,
            MEAL_TYPE_DINNER, MEAL_TYPE_SNACK), calories (number), proteinGrams (number),
            carbGrams (number), fatGrams (number), and mealDateTime (ISO-8601 timestamp).
            Return one array item per meal, never combine lunch and dinner into one item.
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
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model.apiName}:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .header("Connection", "close")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var lastFailure: IOException? = null
        repeat(2) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = responseBody.take(300)
                        if (response.code == 503) {
                            error("Gemini is temporarily unavailable (503). Please try again shortly.")
                        }
                        error("Gemini request failed (${response.code}): $detail")
                    }
                    return@withContext parseResponse(responseBody)
                }
            } catch (error: IOException) {
                lastFailure = error
                if (attempt < 1) {
                    delay(1_000L)
                }
            }
        }
        error("Gemini request timed out or could not connect: ${lastFailure?.message ?: "service unavailable"}")
    }

    private fun parseResponse(responseBody: String): List<ParsedMeal> {
        val text = JSONObject(responseBody)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val meals = JSONArray(text)
        require(meals.length() > 0) { "Gemini returned no meals." }
        return List(meals.length()) { index ->
            val json = meals.getJSONObject(index)
            ParsedMeal(
                mealName = json.getString("mealName"),
                mealType = json.getString("mealType"),
                calories = json.getDouble("calories"),
                proteinGrams = json.getDouble("proteinGrams"),
                carbGrams = json.getDouble("carbGrams"),
                fatGrams = json.getDouble("fatGrams"),
                startTime = parseMealTime(json.getString("mealDateTime"))
            )
        }
    }

    private fun parseMealTime(value: String): Instant {
        return runCatching { Instant.parse(value) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse {
                LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant()
            }
        }
    }
}

enum class GeminiModel(val label: String, val apiName: String) {
    FLASH_3_8("Gemini 3.8 Flash", "gemini-3.8-flash"),
    FLASH_3_6("Gemini 3.6 Flash", "gemini-3.6-flash"),
    FLASH_3_5_LITE("Gemini 3.5 Flash Lite", "gemini-3.5-flash-lite")
}
