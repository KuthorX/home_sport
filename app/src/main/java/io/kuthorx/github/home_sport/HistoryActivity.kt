package io.kuthorx.github.home_sport

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var store: WorkoutStore
    private lateinit var shownMonth: YearMonth
    private lateinit var selectedDate: LocalDate
    private lateinit var monthLabel: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var selectedDateLabel: TextView
    private lateinit var daySummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        SystemBars.useOpaqueStatusBar(this)

        store = WorkoutStore(this)
        monthLabel = findViewById(R.id.monthLabel)
        calendarGrid = findViewById(R.id.calendarGrid)
        selectedDateLabel = findViewById(R.id.selectedDateLabel)
        daySummary = findViewById(R.id.daySummary)
        selectedDate = LocalDate.now()
        shownMonth = YearMonth.from(selectedDate)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.previousMonthButton).setOnClickListener { changeMonth(-1) }
        findViewById<View>(R.id.nextMonthButton).setOnClickListener { changeMonth(1) }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) render()
    }

    private fun changeMonth(offset: Long) {
        shownMonth = shownMonth.plusMonths(offset)
        val today = LocalDate.now()
        selectedDate = if (YearMonth.from(today) == shownMonth) today else shownMonth.atDay(1)
        render()
    }

    private fun render() {
        monthLabel.text = MONTH_FORMAT.format(shownMonth)
        renderCalendar()
        renderSelectedDay()
    }

    private fun renderCalendar() {
        calendarGrid.removeAllViews()
        val activeDays = store.getActiveDays(shownMonth)
        val leadingBlanks = shownMonth.atDay(1).dayOfWeek.value % 7
        val days = shownMonth.lengthOfMonth()
        repeat(42) { position ->
            val dayNumber = position - leadingBlanks + 1
            val date = dayNumber.takeIf { it in 1..days }?.let(shownMonth::atDay)
            calendarGrid.addView(dayCell(date, position, date != null && date in activeDays))
        }
    }

    private fun dayCell(date: LocalDate?, position: Int, checked: Boolean): View {
        val cell = TextView(this).apply {
            layoutParams = dayLayoutParams(position)
            if (date == null) {
                visibility = View.INVISIBLE
                return@apply
            }
            gravity = Gravity.CENTER
            text = getString(R.string.calendar_day, date.dayOfMonth)
            textSize = 14f
            setTypeface(Typeface.DEFAULT, if (checked) Typeface.BOLD else Typeface.NORMAL)
            contentDescription = getString(
                if (checked) R.string.checked_day_description else R.string.unchecked_day_description,
                date.dayOfMonth,
            )

            when {
                date == selectedDate -> {
                    setBackgroundResource(R.drawable.day_selected)
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                checked -> {
                    setBackgroundResource(R.drawable.day_checked)
                    setTextColor(ContextCompat.getColor(context, R.color.primary_dark))
                }
                date == LocalDate.now() -> {
                    setBackgroundResource(R.drawable.day_today)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
                else -> setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            if (checked) {
                ContextCompat.getDrawable(context, R.drawable.ic_check_small)?.mutate()?.let { icon ->
                    val wrapped = DrawableCompat.wrap(icon)
                    DrawableCompat.setTint(
                        wrapped,
                        ContextCompat.getColor(
                            context,
                            if (date == selectedDate) R.color.white else R.color.primary_dark,
                        ),
                    )
                    setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, wrapped)
                    compoundDrawablePadding = dp(2)
                }
            }
            setOnClickListener {
                selectedDate = date
                render()
            }
        }
        return cell
    }

    private fun dayLayoutParams(position: Int) = GridLayout.LayoutParams(
        GridLayout.spec(position / 7, 1f),
        GridLayout.spec(position % 7, 1f),
    ).apply {
        width = 0
        height = dp(48)
        val margin = dp(4)
        setMargins(margin, margin, margin, margin)
    }

    private fun renderSelectedDay() {
        selectedDateLabel.text = DAY_FORMAT.format(selectedDate)
        val record = store.getDay(selectedDate)
        if (record.exercises.isEmpty() && record.completedWorkouts == 0) {
            daySummary.setText(R.string.no_activity)
            return
        }

        val summary = StringBuilder().apply {
            if (record.completedWorkouts > 0) {
                append(
                    getString(
                        R.string.activity_sessions,
                        record.completedWorkouts,
                        formatDuration(record.totalSeconds),
                    ),
                )
            } else {
                append(getString(R.string.activity_duration, formatDuration(record.totalSeconds)))
            }
            record.exercises.forEach { (name, exercise) ->
                append("\n\n")
                append(
                    if (exercise.repetitions > 0) {
                        getString(
                            R.string.exercise_sets_reps,
                            name,
                            exercise.sets,
                            exercise.repetitions,
                            formatDuration(exercise.seconds),
                        )
                    } else {
                        getString(
                            R.string.exercise_sets,
                            name,
                            exercise.sets,
                            formatDuration(exercise.seconds),
                        )
                    },
                )
            }
        }
        daySummary.text = summary
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val remainder = seconds % 60
        return if (minutes > 0) {
            getString(R.string.minutes_seconds, minutes, remainder)
        } else {
            getString(R.string.seconds_only, remainder)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    }
}
