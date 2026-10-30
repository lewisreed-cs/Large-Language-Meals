package com.example.quickcalorielogger

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FoodPreset(
    val name: String,
    val calories: Double,
    val proteinGrams: Double,
    val fatGrams: Double,
    val carbGrams: Double
) {
    val token: String
        get() = "@${name.lowercase().replace(" ", "")}"

    fun expansion(): String =
        "$name (${calories.formatNumber()}kcal, ${proteinGrams.formatNumber()}g protein, " +
            "${fatGrams.formatNumber()}g fat, ${carbGrams.formatNumber()}g carbs)"
}

class FoodPresetStore(context: Context) {
    private val preferences = context.getSharedPreferences("food_presets", Context.MODE_PRIVATE)

    fun load(): List<FoodPreset> {
        val saved = preferences.getString("items", null) ?: return emptyList()
        val json = JSONArray(saved)
        return List(json.length()) { index ->
            val item = json.getJSONObject(index)
            FoodPreset(
                name = item.getString("name"),
                calories = item.getDouble("calories"),
                proteinGrams = item.getDouble("proteinGrams"),
                fatGrams = item.getDouble("fatGrams"),
                carbGrams = item.getDouble("carbGrams")
            )
        }
    }

    fun save(items: List<FoodPreset>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(
                JSONObject()
                    .put("name", item.name)
                    .put("calories", item.calories)
                    .put("proteinGrams", item.proteinGrams)
                    .put("fatGrams", item.fatGrams)
                    .put("carbGrams", item.carbGrams)
            )
        }
        preferences.edit().putString("items", json.toString()).apply()
    }
}

fun Double.formatNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
