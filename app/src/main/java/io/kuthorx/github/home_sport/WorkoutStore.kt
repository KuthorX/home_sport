package io.kuthorx.github.home_sport

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.Collections
import java.util.LinkedHashMap
import java.util.TreeSet

@SuppressLint("ApplySharedPref")
class WorkoutStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveProgress(engine: WorkoutEngine, synchronous: Boolean) {
        val editor = preferences.edit()
            .putString(KEY_PROGRESS, progressJson(engine).toString())
        if (synchronous) editor.commit() else editor.apply()
    }

    @Synchronized
    fun completeStepAndSave(
        engineAfter: WorkoutEngine,
        completedStep: WorkoutStep,
        date: LocalDate,
    ) {
        val history = readHistory()
        val day = getOrCreateObject(history, date.toString())
        if (completedStep.phase == WorkoutStep.Phase.EXERCISE) {
            addCompletedExercise(day, completedStep)
        }
        if (engineAfter.isComplete && engineAfter.skippedSteps == 0) {
            put(day, "completedWorkouts", day.optInt("completedWorkouts") + 1)
        }
        put(history, date.toString(), day)
        preferences.edit()
            .putString(KEY_PROGRESS, progressJson(engineAfter).toString())
            .putString(KEY_HISTORY, history.toString())
            .commit()
    }

    fun restoreProgress(plan: WorkoutPlan): WorkoutEngine? = try {
        val saved = preferences.getString(KEY_PROGRESS, null) ?: return null
        val value = JSONObject(saved)
        WorkoutEngine.restore(
            plan,
            value.getInt("stepIndex"),
            value.getInt("secondsRemaining"),
            value.getBoolean("paused"),
            value.getBoolean("complete"),
            value.optInt("skippedSteps", 0),
        )
    } catch (error: JSONException) {
        clearProgress()
        null
    } catch (error: IllegalArgumentException) {
        clearProgress()
        null
    } catch (error: ClassCastException) {
        clearProgress()
        null
    }

    fun clearProgress() {
        preferences.edit().remove(KEY_PROGRESS).commit()
    }

    @Synchronized
    fun recordCompletedStep(step: WorkoutStep, date: LocalDate) {
        if (step.phase != WorkoutStep.Phase.EXERCISE) return
        val history = readHistory()
        val day = getOrCreateObject(history, date.toString())
        addCompletedExercise(day, step)
        put(history, date.toString(), day)
        writeHistory(history)
    }

    @Synchronized
    fun recordCompletedWorkout(date: LocalDate) {
        val history = readHistory()
        val day = getOrCreateObject(history, date.toString())
        put(day, "completedWorkouts", day.optInt("completedWorkouts") + 1)
        put(history, date.toString(), day)
        writeHistory(history)
    }

    @Synchronized
    fun getDay(date: LocalDate): DayRecord {
        val day = readHistory().optJSONObject(date.toString()) ?: return DayRecord.empty(date)
        val storedExercises = day.optJSONObject("exercises")
        val exercises = LinkedHashMap<String, ExerciseSummary>()
        if (storedExercises != null) {
            val names = storedExercises.keys()
            while (names.hasNext()) {
                val name = names.next()
                val exercise = storedExercises.optJSONObject(name)
                if (exercise != null) {
                    exercises[name] = ExerciseSummary.create(
                        exercise.optInt("sets"),
                        exercise.optInt("repetitions"),
                        exercise.optInt("seconds"),
                    )
                }
            }
        }
        return DayRecord.create(
            date,
            day.optInt("completedWorkouts"),
            day.optInt("totalSeconds"),
            exercises,
        )
    }

    @Synchronized
    fun getActiveDays(month: YearMonth): Set<LocalDate> {
        val history = readHistory()
        val dates = TreeSet<LocalDate>()
        val keys = history.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val date = LocalDate.parse(key)
                if (YearMonth.from(date) == month) {
                    val record = getDay(date)
                    if (record.completedWorkouts > 0 || record.exercises.isNotEmpty()) {
                        dates += date
                    }
                }
            } catch (_: RuntimeException) {
                // Ignore an invalid entry without hiding valid dates.
            }
        }
        return Collections.unmodifiableSet(dates)
    }

    private fun readHistory(): JSONObject = try {
        val saved = preferences.getString(KEY_HISTORY, null)
        if (saved == null) JSONObject() else JSONObject(saved)
    } catch (error: JSONException) {
        preferences.edit().remove(KEY_HISTORY).commit()
        JSONObject()
    } catch (error: ClassCastException) {
        preferences.edit().remove(KEY_HISTORY).commit()
        JSONObject()
    }

    private fun writeHistory(history: JSONObject) {
        preferences.edit().putString(KEY_HISTORY, history.toString()).commit()
    }

    class DayRecord private constructor(
        val date: LocalDate,
        val completedWorkouts: Int,
        val totalSeconds: Int,
        exercises: Map<String, ExerciseSummary>,
    ) {
        val exercises: Map<String, ExerciseSummary> =
            Collections.unmodifiableMap(LinkedHashMap(exercises))

        companion object {
            internal fun create(
                date: LocalDate,
                completedWorkouts: Int,
                totalSeconds: Int,
                exercises: Map<String, ExerciseSummary>,
            ): DayRecord = DayRecord(date, completedWorkouts, totalSeconds, exercises)

            internal fun empty(date: LocalDate): DayRecord = DayRecord(date, 0, 0, emptyMap())
        }
    }

    class ExerciseSummary private constructor(
        val sets: Int,
        val repetitions: Int,
        val seconds: Int,
    ) {
        companion object {
            internal fun create(sets: Int, repetitions: Int, seconds: Int): ExerciseSummary =
                ExerciseSummary(sets, repetitions, seconds)
        }
    }

    companion object {
        private const val PREFS_NAME = "workout_store"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_HISTORY = "history"

        private fun progressJson(engine: WorkoutEngine): JSONObject = JSONObject().also {
            put(it, "stepIndex", engine.stepIndex)
            put(it, "secondsRemaining", engine.secondsRemaining)
            put(it, "paused", engine.isPaused)
            put(it, "complete", engine.isComplete)
            put(it, "skippedSteps", engine.skippedSteps)
        }

        private fun addCompletedExercise(day: JSONObject, step: WorkoutStep) {
            val exercises = getOrCreateObject(day, "exercises")
            val exercise = getOrCreateObject(exercises, step.name)
            put(exercise, "sets", exercise.optInt("sets") + 1)
            put(exercise, "repetitions", exercise.optInt("repetitions") + step.repetitions)
            put(exercise, "seconds", exercise.optInt("seconds") + step.durationSeconds)
            put(exercises, step.name, exercise)
            put(day, "exercises", exercises)
            put(day, "totalSeconds", day.optInt("totalSeconds") + step.durationSeconds)
        }

        private fun getOrCreateObject(parent: JSONObject, key: String): JSONObject =
            parent.optJSONObject(key) ?: JSONObject()

        private fun put(target: JSONObject, key: String, value: Any) {
            try {
                target.put(key, value)
            } catch (impossible: JSONException) {
                throw AssertionError(impossible)
            }
        }
    }
}
