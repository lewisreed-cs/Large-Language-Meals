package com.example.quickcalorielogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QuickCalorieLoggerScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun QuickCalorieLoggerScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val healthConnect = remember { HealthConnectManager(context) }
    val gemini = remember { GeminiRepository() }
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var lastMeal by remember { mutableStateOf<ParsedMeal?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingDescription by remember { mutableStateOf<String?>(null) }

    fun processMeal(text: String) {
        scope.launch {
            isLoading = true
            status = null
            try {
                val meal = gemini.parseMeal(text)
                healthConnect.logNutritionToHealthConnect(meal)
                lastMeal = meal
                status = "Logged successfully to Health Connect!"
                description = ""
            } catch (error: Exception) {
                status = error.message ?: "Unable to log meal."
            } finally {
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectClient.createRequestPermissionResultContract()
    ) {
        pendingDescription?.let { processMeal(it) }
        pendingDescription = null
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Quick Calorie Logger") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meal description") },
                placeholder = { Text("3 scrambled eggs, 2 slices of sourdough with butter") },
                minLines = 3,
                enabled = !isLoading
            )
            Button(
                onClick = {
                    scope.launch {
                        if (healthConnect.hasNutritionPermission()) {
                            processMeal(description)
                        } else {
                            pendingDescription = description
                            permissionLauncher.launch(healthConnect.requiredPermissions)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.isNotBlank() && !isLoading
            ) {
                Text("Log Meal")
            }
            if (isLoading) CircularProgressIndicator()
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            lastMeal?.let { meal ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(meal.mealName, style = MaterialTheme.typography.titleLarge)
                        Text("Calories: ${meal.calories}")
                        Text("Protein: ${meal.proteinGrams} g")
                        Text("Carbs: ${meal.carbGrams} g")
                        Text("Fat: ${meal.fatGrams} g")
                    }
                }
            }
        }
    }
}
