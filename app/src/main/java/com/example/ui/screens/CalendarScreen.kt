package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.TaskItem
import com.example.model.UiState
import com.example.ui.components.DateFormatters
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.TaskCardItem
import com.example.ui.components.priorityColor
import com.example.ui.i18n.copyFor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarViewMode { MONTH, WEEK, DAY }

@Composable
fun CalendarScreen(
    tasksState: UiState<List<TaskItem>>,
    projects: List<ProjectItem>,
    language: AppLanguage,
    onEditTask: (TaskItem) -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onRescheduleTask: (TaskItem, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val copy = remember(language) { copyFor(language) }
    val projectById = remember(projects) { projects.associateBy { it.id } }

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDateMillis by remember { mutableLongStateOf(DateFormatters.startOfTodayMillis()) }
    var anchorMonthMillis by remember { mutableLongStateOf(DateFormatters.startOfTodayMillis()) }

    val tasksWithDeadlines = remember(tasksState) {
        (tasksState as? UiState.Success)?.data
            .orEmpty()
            .filter { !it.archived && it.dueAtMillis != null }
    }

    val selectedDayTasks = remember(tasksWithDeadlines, selectedDateMillis) {
        tasksWithDeadlines.filter { task ->
            val due = task.dueAtMillis ?: return@filter false
            DateFormatters.isSameDay(due, selectedDateMillis)
        }.sortedBy { it.dueAtMillis }
    }

    fun shiftPeriod(delta: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMonthMillis }
        when (viewMode) {
            CalendarViewMode.MONTH -> cal.add(Calendar.MONTH, delta)
            CalendarViewMode.WEEK -> {
                cal.timeInMillis = selectedDateMillis
                cal.add(Calendar.WEEK_OF_YEAR, delta)
                selectedDateMillis = cal.timeInMillis
            }
            CalendarViewMode.DAY -> {
                cal.timeInMillis = selectedDateMillis
                cal.add(Calendar.DAY_OF_YEAR, delta)
                selectedDateMillis = cal.timeInMillis
            }
        }
        anchorMonthMillis = cal.timeInMillis
    }

    fun openReschedulePicker(task: TaskItem) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = task.dueAtMillis ?: System.currentTimeMillis()
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = task.dueAtMillis ?: System.currentTimeMillis()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                onRescheduleTask(task, updated.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = copy.navCalendar,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = {
                        val today = DateFormatters.startOfTodayMillis()
                        selectedDateMillis = today
                        anchorMonthMillis = today
                    }) {
                        Text(if (language == AppLanguage.ID) "Hari Ini" else "Today")
                    }
                }
            }

            // Mode switcher (Month / Week / Day)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewMode == CalendarViewMode.MONTH,
                        onClick = { viewMode = CalendarViewMode.MONTH },
                        label = { Text(if (language == AppLanguage.ID) "Bulanan" else "Month") }
                    )
                    FilterChip(
                        selected = viewMode == CalendarViewMode.WEEK,
                        onClick = { viewMode = CalendarViewMode.WEEK },
                        label = { Text(if (language == AppLanguage.ID) "Mingguan" else "Week") }
                    )
                    FilterChip(
                        selected = viewMode == CalendarViewMode.DAY,
                        onClick = { viewMode = CalendarViewMode.DAY },
                        label = { Text(if (language == AppLanguage.ID) "Harian" else "Day") }
                    )
                }
            }

            // Period Header & Calendar Grid
            item {
                val monthTitle = remember(anchorMonthMillis) {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(anchorMonthMillis))
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { shiftPeriod(-1) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Sebelumnya")
                            }
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { shiftPeriod(1) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Berikutnya")
                            }
                        }

                        if (viewMode != CalendarViewMode.DAY) {
                            Spacer(Modifier.height(6.dp))
                            val dayLabels = if (language == AppLanguage.ID) {
                                listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
                            } else {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                dayLabels.forEach { d ->
                                    Text(
                                        text = d,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            val daysToRender: List<Long?> = remember(anchorMonthMillis, selectedDateMillis, viewMode) {
                                if (viewMode == CalendarViewMode.MONTH) {
                                    buildMonthDays(anchorMonthMillis)
                                } else {
                                    buildWeekDays(selectedDateMillis)
                                }
                            }
                            val rows = daysToRender.chunked(7)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                rows.forEach { week ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        week.forEach { dayMillis ->
                                            if (dayMillis == null) {
                                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                                            } else {
                                                val dayTasks = tasksWithDeadlines.filter {
                                                    it.dueAtMillis != null && DateFormatters.isSameDay(it.dueAtMillis, dayMillis)
                                                }
                                                val isSelected = DateFormatters.isSameDay(dayMillis, selectedDateMillis)
                                                val isToday = DateFormatters.isSameDay(dayMillis, System.currentTimeMillis())
                                                val dayNum = Calendar.getInstance().apply { timeInMillis = dayMillis }
                                                    .get(Calendar.DAY_OF_MONTH)

                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            when {
                                                                isSelected -> MaterialTheme.colorScheme.primary
                                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                                else -> MaterialTheme.colorScheme.surface
                                                            }
                                                        )
                                                        .clickable {
                                                            selectedDateMillis = dayMillis
                                                            anchorMonthMillis = dayMillis
                                                        }
                                                        .padding(2.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = "$dayNum",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                        ),
                                                        color = when {
                                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                    if (dayTasks.isNotEmpty()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            dayTasks.take(3).forEach { dt ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(5.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                            else priorityColor(dt.taskPriority)
                                                                        )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Agenda
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateFormatters.formatFullDate(selectedDateMillis),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${selectedDayTasks.size} ${if (language == AppLanguage.ID) "agenda" else "items"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (selectedDayTasks.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Rounded.CalendarMonth,
                        title = if (language == AppLanguage.ID) "Tidak ada deadline pada tanggal ini" else "No deadlines on this date",
                        subtitle = if (language == AppLanguage.ID) "Tugas tanpa deadline tidak dipaksakan masuk ke kalender." else "Tasks without deadlines stay out of the calendar."
                    )
                }
            } else {
                items(selectedDayTasks, key = { "cal_${it.id}" }) { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TaskCardItem(
                            task = task,
                            project = projectById[task.projectId],
                            language = language,
                            onToggleComplete = { onToggleComplete(task) },
                            onClick = { onEditTask(task) }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { openReschedulePicker(task) }) {
                                Icon(Icons.Rounded.EditCalendar, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (language == AppLanguage.ID) "Ubah Tanggal" else "Reschedule",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildMonthDays(anchorMillis: Long): List<Long?> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anchorMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2..Sat=7
    val leadingBlanks = (dayOfWeek + 5) % 7 // Mon=0..Sun=6
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Long?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..maxDay) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        cells.add(cal.timeInMillis)
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun buildWeekDays(selectedMillis: Long): List<Long?> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = selectedMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val offsetFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    cal.add(Calendar.DAY_OF_YEAR, -offsetFromMonday)
    return (0..6).map { idx ->
        val dayCal = cal.clone() as Calendar
        dayCal.add(Calendar.DAY_OF_YEAR, idx)
        dayCal.timeInMillis
    }
}
