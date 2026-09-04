package io.kuthorx.github.home_sport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {
    @Test
    fun tickPauseResumeAndSkipAdvanceStages() {
        val plan = WorkoutPlan(
            listOf(
                step(WorkoutStep.Phase.EXERCISE, 2, 1, 2, 0, 1),
                step(WorkoutStep.Phase.REST, 1, 0, 0, 1, 1),
                step(WorkoutStep.Phase.EXERCISE, 2, 2, 2, 1, 1),
            ),
        )
        val engine = WorkoutEngine(plan)

        engine.tick()
        assertEquals(1, engine.secondsRemaining)
        engine.pause()
        engine.tick()
        assertEquals(1, engine.secondsRemaining)
        engine.resume()
        engine.tick()
        assertEquals(WorkoutStep.Phase.REST, engine.phase)
        assertEquals(1, engine.currentRest)

        engine.skip()
        assertEquals(WorkoutStep.Phase.EXERCISE, engine.phase)
        assertEquals(2, engine.currentSet)
        assertEquals(1, engine.skippedSteps)
    }

    @Test
    fun finalTickCompletesWorkout() {
        val engine = WorkoutEngine(
            WorkoutPlan(listOf(step(WorkoutStep.Phase.EXERCISE, 1, 1, 1, 0, 0))),
        )

        engine.tick()

        assertTrue(engine.isComplete)
        assertFalse(engine.isPaused)
        assertEquals(WorkoutStep.Phase.COMPLETED, engine.phase)
        assertEquals(0, engine.secondsRemaining)
        assertEquals(0, engine.skippedSteps)
    }

    @Test
    fun restoresPausedCompletedAndSkippedStates() {
        val plan = WorkoutPlan(
            listOf(
                step(WorkoutStep.Phase.EXERCISE, 2, 1, 1, 0, 1),
                step(WorkoutStep.Phase.REST, 3, 0, 0, 1, 1),
            ),
        )
        val paused = WorkoutEngine.restore(plan, 1, 2, true, false, 1)

        paused.tick()
        assertEquals(1, paused.stepIndex)
        assertEquals(2, paused.secondsRemaining)
        assertTrue(paused.isPaused)
        assertEquals(1, paused.skippedSteps)

        val complete = WorkoutEngine.restore(plan, 2, 0, false, true)
        assertTrue(complete.isComplete)
        assertEquals(WorkoutStep.Phase.COMPLETED, complete.phase)
        assertEquals(0, complete.skippedSteps)
    }

    @Test
    fun rejectsInvalidRestoredStates() {
        val plan = WorkoutPlan(listOf(step(WorkoutStep.Phase.EXERCISE, 2, 1, 1, 0, 0)))

        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEngine.restore(plan, 1, 1, false, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEngine.restore(plan, 0, 3, false, false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEngine.restore(plan, 1, 0, true, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutEngine.restore(plan, 0, 1, false, false, -1)
        }
    }

    private fun step(
        phase: WorkoutStep.Phase,
        seconds: Int,
        set: Int,
        totalSets: Int,
        rest: Int,
        totalRests: Int,
    ) = WorkoutStep(phase, "动作", "介绍", seconds, 0, set, totalSets, rest, totalRests)
}
