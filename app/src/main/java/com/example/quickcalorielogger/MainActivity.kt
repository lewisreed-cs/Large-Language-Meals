package com.example.quickcalorielogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LargeLanguageMealsScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LargeLanguageMealsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val healthConnect = remember { HealthConnectManager(context) }
    val gemini = remember { GeminiRepository() }
    val foodStore = remember { FoodPresetStore(context) }
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var mealDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedModel by remember { mutableStateOf(GeminiModel.FLASH_3_6) }
    var lastMeals by remember { mutableStateOf<List<ParsedMeal>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var addFoodDialogOpen by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(foodStore.load()) }

    val activeToken = description.text.substringAfterLast(" ").let { token ->
        if (token.startsWith("@")) token.lowercase() else ""
    }
    val suggestions = if (activeToken.length > 1) {
        presets.filter { it.token.startsWith(activeToken) }
    } else {
        emptyList()
    }

    fun selectPreset(preset: FoodPreset) {
        val prefix = description.text.substringBeforeLast(" ", missingDelimiterValue = "")
        val replacement = if (prefix.isBlank()) "${preset.token} " else "$prefix ${preset.token} "
        description = TextFieldValue(
            text = replacement,
            selection = TextRange(replacement.length)
        )
    }

    fun processMeal(text: String, date: LocalDate) {
        scope.launch {
            isLoading = true
            status = null
            try {
                val expanded = presets.fold(text) { value, preset ->
                    value.replace(preset.token, preset.expansion(), ignoreCase = true)
                }
                val meals = gemini.parseMeals(expanded, date, selectedModel)
                healthConnect.logNutritionToHealthConnect(meals)
                lastMeals = meals
                status = "${meals.size} meal${if (meals.size == 1) "" else "s"} logged successfully to Health Connect!"
                description = TextFieldValue("")
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

    Scaffold(topBar = { TopAppBar(title = { Text("Large Language Meals") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { datePickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Meal date: ${mealDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}")
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Meal description") },
                    placeholder = { Text("Lunch: @proteinwraps and fruit") },
                    minLines = 4,
                    enabled = !isLoading
                )
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 72.dp)
                    ) {
                        Column {
                        suggestions.forEach { preset ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectPreset(preset) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                        Text(preset.name)
                                        Text(
                                            "${preset.calories} kcal | P ${preset.proteinGrams}g | " +
                                                "F ${preset.fatGrams}g | C ${preset.carbGrams}g",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                            }
                        }
                        }
                    }
                }
            }
            SavedFoodsTable(
                presets = presets,
                onAdd = { addFoodDialogOpen = true }
            )
            Spacer(modifier = Modifier.weight(1f))
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (isLoading) CircularProgressIndicator()
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
            Button(
                onClick = {
                    scope.launch {
                        if (healthConnect.hasNutritionPermission()) {
                            processMeal(description.text, mealDate)
                        } else {
                            pendingRequest = description.text to mealDate
                            permissionLauncher.launch(healthConnect.requiredPermissions)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.text.isNotBlank() && !isLoading
            ) {
                Text("Log Meal")
            }
            if (lastMeals.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(lastMeals) { meal ->
                            Text(meal.mealName, style = MaterialTheme.typography.titleLarge)
                            Text("Date: ${meal.mealDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}")
                            Text("Calories: ${meal.calories} | P ${meal.proteinGrams}g | F ${meal.fatGrams}g | C ${meal.carbGrams}g")
                        }
                    }
                }
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = mealDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        mealDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    datePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (addFoodDialogOpen) {
        AddFoodDialog(
            presets = presets,
            onDismiss = { addFoodDialogOpen = false },
            onSave = {
                presets = it
                foodStore.save(it)
            }
        )
    }
}

@Composable
private fun SavedFoodsTable(
    presets: List<FoodPreset>,
    onAdd: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Saved foods", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onAdd) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Name", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium)
                Text("Calories", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Fat", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Carbs", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Protein", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(presets) { preset ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(preset.name, modifier = Modifier.weight(2f))
                        Text(preset.calories.formatNumber(), modifier = Modifier.weight(1f))
                        Text(preset.fatGrams.formatNumber(), modifier = Modifier.weight(1f))
                        Text(preset.carbGrams.formatNumber(), modifier = Modifier.weight(1f))
                        Text(preset.proteinGrams.formatNumber(), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFoodDialog(
    presets: List<FoodPreset>,
    onDismiss: () -> Unit,
    onSave: (List<FoodPreset>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved foods") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { Text("${it.token} - ${it.name}") }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(calories, { calories = it }, label = { Text("Calories") }, singleLine = true)
                OutlinedTextField(protein, { protein = it }, label = { Text("Protein grams") }, singleLine = true)
                OutlinedTextField(fat, { fat = it }, label = { Text("Fat grams") }, singleLine = true)
                OutlinedTextField(carbs, { carbs = it }, label = { Text("Carbs grams") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val item = FoodPreset(
                    name = name.trim(),
                    calories = calories.toDoubleOrNull() ?: 0.0,
                    proteinGrams = protein.toDoubleOrNull() ?: 0.0,
                    fatGrams = fat.toDoubleOrNull() ?: 0.0,
                    carbGrams = carbs.toDoubleOrNull() ?: 0.0
                )
                if (item.name.isNotBlank()) onSave((presets + item).distinctBy { it.token })
                onDismiss()
            }) { Text("Save food") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
