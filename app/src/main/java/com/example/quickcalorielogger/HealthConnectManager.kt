package com.example.quickcalorielogger

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant

data class ParsedMeal(
    val mealName: String,
    val mealType: String,
    val calories: Double,
    val proteinGrams: Double,
    val carbGrams: Double,
    val fatGrams: Double
)

class HealthConnectManager(context: Context) {
    private val client = HealthConnectClient.getOrCreate(context)

    val requiredPermissions = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class)
    )

    suspend fun hasNutritionPermission(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)

    suspend fun logNutritionToHealthConnect(meal: ParsedMeal) {
        val now = Instant.now()
        val record = NutritionRecord(
            metadata = Metadata.manualEntry(),
            startTime = now,
            endTime = now,
            startZoneOffset = null,
            endZoneOffset = null,
            name = meal.mealName,
            mealType = meal.mealType.toHealthConnectMealType(),
            energy = Energy.kilocalories(meal.calories),
            protein = Mass.grams(meal.proteinGrams),
            totalCarbohydrate = Mass.grams(meal.carbGrams),
            totalFat = Mass.grams(meal.fatGrams)
        )
        client.insertRecords(listOf(record))
    }

    private fun String.toHealthConnectMealType(): Int = when (this) {
        "MEAL_TYPE_BREAKFAST" -> MealType.MEAL_TYPE_BREAKFAST
        "MEAL_TYPE_LUNCH" -> MealType.MEAL_TYPE_LUNCH
        "MEAL_TYPE_DINNER" -> MealType.MEAL_TYPE_DINNER
        else -> MealType.MEAL_TYPE_SNACK
    }
}
