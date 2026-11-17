package com.example.fitnessapp

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val BgDark = Color(0xFF0F1115)
val CardDark = Color(0xFF1A1D24)
val CardBorder = Color(0xFF2A2E39)
val NeonGreen = Color(0xFF30C76B)
val NeonGreenDim = Color(0x2630C76B)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF8E95A5)
val AmberPause = Color(0xFFFFB020)
val CoralStop = Color(0xFFFF4D4F)

data class Exercise(val name: String, val met: Double)
data class RepExercise(val name: String, val kcalPerRepAt70kg: Double)

data class WorkoutRecord(
    val id: Long,
    val exerciseName: String,
    val durationFormatted: String,
    val calories: Double,
    val date: String
)

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun saveHistory(context: Context, list: List<WorkoutRecord>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id)
            put("name", it.exerciseName)
            put("dur", it.durationFormatted)
            put("cal", it.calories)
            put("date", it.date)
        })
    }
    context.getSharedPreferences("fit_prefs", Context.MODE_PRIVATE)
        .edit().putString("history", arr.toString()).apply()
}

fun loadHistory(context: Context): List<WorkoutRecord> {
    val str = context.getSharedPreferences("fit_prefs", Context.MODE_PRIVATE)
        .getString("history", null) ?: return emptyList()
    val res = mutableListOf<WorkoutRecord>()
    runCatching {
        val arr = JSONArray(str)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            res.add(WorkoutRecord(o.getLong("id"), o.getString("name"), o.getString("dur"), o.getDouble("cal"), o.getString("date")))
        }
    }
    return res
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface = CardDark,
                    primary = NeonGreen,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                FitnessMainApp()
            }
        }
    }
}

@Composable
fun FitnessMainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fit_prefs", Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<WorkoutRecord>().apply { addAll(loadHistory(context)) } }
    var dailyGoal by remember { mutableStateOf(prefs.getString("daily_goal", "500") ?: "500") }

    var allTimeKcal by remember {
        val saved = prefs.getFloat("all_time_kcal", -1f)
        mutableDoubleStateOf(if (saved < 0f) history.sumOf { it.calories } else saved.toDouble())
    }
    var activeDaysSet by remember {
        val savedSet = prefs.getStringSet("active_days_set", null)
        val initialSet = savedSet?.toSet() ?: history.map { it.date.take(10) }.toSet()
        mutableStateOf(initialSet)
    }

    val todayPrefix = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()) }
    val todayCalories = history.filter { it.date.startsWith(todayPrefix) }.sumOf { it.calories }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(containerColor = CardDark, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("⚡", fontSize = 18.sp) },
                    label = { Text("Тренировка", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonGreen,
                        selectedTextColor = NeonGreen,
                        indicatorColor = NeonGreenDim,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📊", fontSize = 18.sp) },
                    label = { Text("История (${history.size})", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonGreen,
                        selectedTextColor = NeonGreen,
                        indicatorColor = NeonGreenDim,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { pad ->
        Box(modifier = Modifier.padding(pad)) {
            if (tab == 0) {
                WorkoutScreen(
                    allTimeKcal = allTimeKcal,
                    totalActiveDays = activeDaysSet.size,
                    todayCalories = todayCalories,
                    dailyGoalStr = dailyGoal,
                    onGoalChange = { newGoal ->
                        dailyGoal = newGoal
                        prefs.edit().putString("daily_goal", newGoal).apply()
                    },
                    onSave = { record ->
                        history.add(0, record)
                        saveHistory(context, history)

                        allTimeKcal += record.calories
                        val updatedDays = activeDaysSet + record.date.take(10)
                        activeDaysSet = updatedDays
                        prefs.edit()
                            .putFloat("all_time_kcal", allTimeKcal.toFloat())
                            .putStringSet("active_days_set", updatedDays)
                            .apply()
                    }
                )
            } else {
                HistoryScreen(
                    history = history,
                    onDeleteOne = { record ->
                        history.remove(record)
                        saveHistory(context, history)
                    },
                    onClearAll = {
                        history.clear()
                        saveHistory(context, history)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    allTimeKcal: Double,
    totalActiveDays: Int,
    todayCalories: Double,
    dailyGoalStr: String,
    onGoalChange: (String) -> Unit,
    onSave: (WorkoutRecord) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fit_prefs", Context.MODE_PRIVATE) }

    var workoutMode by remember { mutableIntStateOf(0) }

    val timeExercises = remember {
        listOf(
            Exercise("Ходьба", 3.5), Exercise("Бег (8 км/ч)", 8.0),
            Exercise("Бег (быстрый)", 11.5), Exercise("Велосипед", 7.5),
            Exercise("Силовая", 6.0), Exercise("Плавание", 7.0),
            Exercise("Скакалка", 11.0), Exercise("Йога", 2.5)
        )
    }

    val repExercises = remember {
        listOf(
            RepExercise("Отжимания", 0.35),
            RepExercise("Приседания", 0.40),
            RepExercise("Скручивания (пресс)", 0.25),
            RepExercise("Подтягивания", 0.80),
            RepExercise("Бёрпи", 1.10),
            RepExercise("Выпады", 0.45)
        )
    }

    var selectedTimeEx by remember {
        val savedName = prefs.getString("saved_exercise", null)
        mutableStateOf(timeExercises.find { it.name == savedName } ?: timeExercises.first())
    }
    var selectedRepEx by remember {
        val savedRepName = prefs.getString("saved_rep_exercise", null)
        mutableStateOf(repExercises.find { it.name == savedRepName } ?: repExercises.first())
    }

    var expanded by remember { mutableStateOf(false) }
    var weightStr by remember { mutableStateOf(prefs.getString("saved_weight", "70") ?: "70") }

    var running by remember { mutableStateOf(false) }
    var accMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var dispMs by remember { mutableLongStateOf(0L) }
    var repsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(running) {
        if (running) {
            startMs = SystemClock.elapsedRealtime()
            while (running) {
                dispMs = accMs + (SystemClock.elapsedRealtime() - startMs)
                delay(100L)
            }
        }
    }

    val weight = weightStr.toDoubleOrNull() ?: 70.0
    val currentKcal = if (workoutMode == 0) {
        selectedTimeEx.met * weight * (dispMs / 3600000.0)
    } else {
        selectedRepEx.kcalPerRepAt70kg * (weight / 70.0) * repsCount
    }

    val goalVal = (dailyGoalStr.toDoubleOrNull() ?: 500.0).coerceAtLeast(1.0)
    val totalTodayWithActive = todayCalories + currentKcal
    val progress = (totalTodayWithActive / goalVal).toFloat().coerceIn(0f, 1f)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NeonGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = NeonGreen,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        disabledTextColor = TextSecondary,
        disabledBorderColor = CardBorder
    )

    val isLocked = (workoutMode == 0 && (running || dispMs > 0L))

    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FITNESS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 1.5.sp)
            Text(".APP", fontSize = 20.sp, fontWeight = FontWeight.Black, color = NeonGreen, letterSpacing = 1.5.sp)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CardDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥 ккал: ", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = String.format(Locale.US, "%.1f", allTimeKcal + currentKcal),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGreen
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(20.dp).background(CardBorder))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 дней: ", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$totalActiveDays",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CardDark,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ЦЕЛЬ НА СЕГОДНЯ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary, letterSpacing = 1.sp)
                    Text(
                        "${String.format(Locale.US, "%.1f", totalTodayWithActive)} / ${goalVal.toInt()} ккал",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonGreen
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = NeonGreen,
                    trackColor = BgDark
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (!isLocked) { workoutMode = 0; expanded = false } },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (workoutMode == 0) NeonGreen else CardDark,
                    contentColor = if (workoutMode == 0) Color.Black else TextSecondary
                ),
                border = if (workoutMode == 0) null else BorderStroke(1.dp, CardBorder),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("⏱ По времени", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = { if (!isLocked) { workoutMode = 1; expanded = false } },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (workoutMode == 1) NeonGreen else CardDark,
                    contentColor = if (workoutMode == 1) Color.Black else TextSecondary
                ),
                border = if (workoutMode == 1) null else BorderStroke(1.dp, CardBorder),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("💪 Повторения", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = weightStr,
                onValueChange = {
                    if (!isLocked) {
                        weightStr = it
                        prefs.edit().putString("saved_weight", it).apply()
                    }
                },
                label = { Text("Вес (кг)") },
                enabled = !isLocked,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = dailyGoalStr,
                onValueChange = onGoalChange,
                label = { Text("Цель (ккал)") },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (!isLocked) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            val displayText = if (workoutMode == 0) {
                "${selectedTimeEx.name}  •  MET ${selectedTimeEx.met}"
            } else {
                "${selectedRepEx.name}  •  ~${selectedRepEx.kcalPerRepAt70kg} ккал/раз"
            }

            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                enabled = !isLocked,
                label = { Text(if (workoutMode == 0) "Кардио / Время" else "Силовое упражнение") },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(CardDark)
            ) {
                if (workoutMode == 0) {
                    timeExercises.forEach { ex ->
                        DropdownMenuItem(
                            text = { Text("${ex.name} (MET: ${ex.met})", color = TextPrimary) },
                            onClick = {
                                selectedTimeEx = ex
                                prefs.edit().putString("saved_exercise", ex.name).apply()
                                expanded = false
                            }
                        )
                    }
                } else {
                    repExercises.forEach { repEx ->
                        DropdownMenuItem(
                            text = { Text("${repEx.name} (${repEx.kcalPerRepAt70kg} ккал/раз)", color = TextPrimary) },
                            onClick = {
                                selectedRepEx = repEx
                                prefs.edit().putString("saved_rep_exercise", repEx.name).apply()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (running || repsCount > 0) NeonGreen else CardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1F242D), CardDark)))
                    .padding(vertical = 16.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (workoutMode == 0) {
                        Text(
                            text = if (running) "ТРЕНИРОВКА ИДЁТ" else "ВРЕМЯ СЕССИИ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (running) NeonGreen else TextSecondary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = formatTime(dispMs), fontSize = 44.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    } else {
                        Text(
                            text = "СДЕЛАНО ПОВТОРЕНИЙ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (repsCount > 0) NeonGreen else TextSecondary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { if (repsCount > 0) repsCount-- },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("-1", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Text(text = "$repsCount", fontSize = 44.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            Button(
                                onClick = { repsCount++ },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("+1", fontSize = 17.sp, fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = { repsCount += 5 },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreenDim, contentColor = NeonGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+5", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = NeonGreenDim) {
                        Text(
                            text = "🔥 ${String.format(Locale.US, "%.2f", currentKcal)} ккал",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                    if (workoutMode == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Мин. время для сохранения: 00:00:20",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (workoutMode == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (running) { accMs += SystemClock.elapsedRealtime() - startMs; running = false }
                        else running = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (running) AmberPause else NeonGreen, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text(
                        text = if (running) "ПАУЗА" else if (dispMs > 0L) "ДАЛЬШЕ" else "СТАРТ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                }

                if (dispMs > 0L) {
                    Button(
                        onClick = {
                            val finalMs = if (running) accMs + (SystemClock.elapsedRealtime() - startMs) else accMs
                            if (finalMs >= 20000L) {
                                val finalKcal = selectedTimeEx.met * weight * (finalMs / 3600000.0)
                                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                                onSave(WorkoutRecord(System.currentTimeMillis(), selectedTimeEx.name, formatTime(finalMs), finalKcal, date))
                            } else {
                                Toast.makeText(context, "Менее 20 сек — не сохранено", Toast.LENGTH_SHORT).show()
                            }
                            running = false; accMs = 0L; dispMs = 0L
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralStop, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("СТОП", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (repsCount > 0) {
                    OutlinedButton(
                        onClick = { repsCount = 0 },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CoralStop),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("СБРОС", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CoralStop)
                    }
                }
                Button(
                    onClick = {
                        if (repsCount > 0) {
                            val finalKcal = selectedRepEx.kcalPerRepAt70kg * (weight / 70.0) * repsCount
                            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                            onSave(WorkoutRecord(System.currentTimeMillis(), selectedRepEx.name, "$repsCount повт.", finalKcal, date))
                            repsCount = 0
                        }
                    },
                    enabled = repsCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = CardDark,
                        disabledContentColor = TextSecondary
                    ),
                    modifier = Modifier.weight(2f).height(52.dp)
                ) {
                    Text("ЗАПИСАТЬ ПОДХОД", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<WorkoutRecord>,
    onDeleteOne: (WorkoutRecord) -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            containerColor = CardDark,
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Очистить историю?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Все сохранённые тренировки будут безвозвратно удалены.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onClearAll(); showConfirmDialog = false }) {
                    Text("Удалить", color = CoralStop, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Отмена", color = TextPrimary)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ИСТОРИЯ", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 1.sp)
            if (history.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoralStop)
                ) {
                    Text("Очистить", color = CoralStop, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Пока нет завершённых тренировок", color = TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { rec ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = CardDark,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(rec.exerciseName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                                Spacer(Modifier.height(4.dp))
                                Text("⚡ ${rec.durationFormatted}   •   ${rec.date}", fontSize = 12.sp, color = TextSecondary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "🔥 ${String.format(Locale.US, "%.2f", rec.calories)} ккал",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = NeonGreen
                                )
                            }
                            IconButton(onClick = { onDeleteOne(rec) }) {
                                Text("🗑️", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
