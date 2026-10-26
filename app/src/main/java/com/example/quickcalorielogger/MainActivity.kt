package com.example.quickcalorielogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var dateTimeHint by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(GeminiModel.FLASH_3_6) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var lastMeals by remember { mutableStateOf<List<ParsedMeal>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun processMeal(text: String, timeHint: String) {
        scope.launch {
            isLoading = true
            status = null
            try {
                val meals = gemini.parseMeals(text, timeHint, selectedModel)
                healthConnect.logNutritionToHealthConnect(meals)
                lastMeals = meals
                status = "${meals.size} meal${if (meals.size == 1) "" else "s"} logged successfully to Health Connect!"
                description = ""
                dateTimeHint = ""
            } catch (error: Exception) {
                status = error.message ?: "Unable to log meal."
            } finally {
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        pendingRequest?.let { processMeal(it.first, it.second) }
        pendingRequest = null
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
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { modelMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Model: ${selectedModel.label}")
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    GeminiModel.entries.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.label) },
                            onClick = {
                                selectedModel = model
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = dateTimeHint,
                onValueChange = { dateTimeHint = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meal date/time (optional)") },
                placeholder = { Text("Today at 12:30 for lunch and 19:00 for dinner") },
                enabled = !isLoading
            )
            Button(
                onClick = {
                    scope.launch {
                        if (healthConnect.hasNutritionPermission()) {
                            processMeal(description, dateTimeHint)
                        } else {
                            pendingRequest = description to dateTimeHint
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
            if (lastMeals.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        lastMeals.forEach { meal ->
                            Text(meal.mealName, style = MaterialTheme.typography.titleLarge)
                            Text("Time: ${meal.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}")
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
}
