package io.kuthorx.github.home_sport

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WorkoutStoreTest {
    private lateinit var context: Context
    private lateinit var store: WorkoutStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("workout_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = WorkoutStore(context)
    }

    @Test
    fun savesProgressAndDailyHistory() {
        val step = WorkoutStep(
            WorkoutStep.Phase.EXERCISE, "深蹲", "介绍", 45, 12, 1, 3, 0, 0,
        )
        val plan = WorkoutPlan(listOf(step))
        val engine = WorkoutEngine(plan)
        engine.tick()

        store.saveProgress(engine, true)
        val restored = store.restoreProgress(plan)!!
        assertEquals(44, restored.secondsRemaining)
        assertEquals(0, restored.skippedSteps)

        val date = LocalDate.of(2026, 9, 5)
        store.recordCompletedStep(step, date)
        store.recordCompletedWorkout(date)
        val day = store.getDay(date)

        assertEquals(1, day.completedWorkouts)
        assertEquals(45, day.totalSeconds)
        assertEquals(12, day.exercises.getValue("深蹲").repetitions)
        assertTrue(store.getActiveDays(YearMonth.of(2026, 9)).contains(date))
    }

    @Test
    fun restoresLegacyProgressWithoutSkippedSteps() {
        context.getSharedPreferences("workout_store", Context.MODE_PRIVATE).edit()
            .putString(
                "progress",
                "{\"stepIndex\":0,\"secondsRemaining\":1,\"paused\":true,\"complete\":false}",
            )
            .commit()
        val plan = WorkoutPlan(
            listOf(
                WorkoutStep(
                    WorkoutStep.Phase.EXERCISE, "动作", "介绍", 1, 0, 1, 1, 0, 0,
                ),
            ),
        )

        val restored = store.restoreProgress(plan)!!

        assertEquals(0, restored.skippedSteps)
        assertTrue(restored.isPaused)
    }

    @Test
    fun corruptProgressFallsBackAndClearsIt() {
        context.getSharedPreferences("workout_store", Context.MODE_PRIVATE)
            .edit().putString("progress", "broken").commit()
        val plan = WorkoutPlan(
            listOf(
                WorkoutStep(
                    WorkoutStep.Phase.EXERCISE, "动作", "介绍", 1, 0, 1, 1, 0, 0,
                ),
            ),
        )

        assertNull(store.restoreProgress(plan))
        assertNull(
            context.getSharedPreferences("workout_store", Context.MODE_PRIVATE)
                .getString("progress", null),
        )
    }

    @Test
    fun completedStepAtomicallySavesProgressAndHistory() {
        val step = WorkoutStep(
            WorkoutStep.Phase.EXERCISE, "平板支撑", "介绍", 1, 0, 1, 1, 0, 0,
        )
        val plan = WorkoutPlan(listOf(step))
        val engine = WorkoutEngine(plan)
        engine.tick()
        val date = LocalDate.of(2026, 9, 6)

        store.completeStepAndSave(engine, step, date)

        assertTrue(store.restoreProgress(plan)!!.isComplete)
        val day = store.getDay(date)
        assertEquals(1, day.completedWorkouts)
        assertEquals(1, day.exercises.getValue("平板支撑").sets)
        assertEquals(1, day.totalSeconds)
    }

    @Test
    fun skippedWorkoutDoesNotCountAsCompleted() {
        val skipped = WorkoutStep(
            WorkoutStep.Phase.EXERCISE, "深蹲", "介绍", 1, 12, 1, 2, 0, 0,
        )
        val completed = WorkoutStep(
            WorkoutStep.Phase.EXERCISE, "平板支撑", "介绍", 1, 0, 2, 2, 0, 0,
        )
        val plan = WorkoutPlan(listOf(skipped, completed))
        val engine = WorkoutEngine(plan)
        engine.skip()
        engine.tick()
        val date = LocalDate.of(2026, 9, 7)

        store.completeStepAndSave(engine, completed, date)

        val restored = store.restoreProgress(plan)!!
        assertEquals(1, restored.skippedSteps)
        val day = store.getDay(date)
        assertEquals(0, day.completedWorkouts)
        assertEquals(1, day.exercises.getValue("平板支撑").sets)
    }
}
