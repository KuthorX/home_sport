package io.kuthorx.github.home_sport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class WorkoutEngineTest {
    @Test
    public void tickPauseResumeAndSkipAdvanceStages() {
        WorkoutPlan plan = new WorkoutPlan(Arrays.asList(
                step(WorkoutStep.Phase.EXERCISE, 2, 1, 2, 0, 1),
                step(WorkoutStep.Phase.REST, 1, 0, 0, 1, 1),
                step(WorkoutStep.Phase.EXERCISE, 2, 2, 2, 1, 1)
        ));
        WorkoutEngine engine = new WorkoutEngine(plan);

        engine.tick();
        assertEquals(1, engine.getSecondsRemaining());
        engine.pause();
        engine.tick();
        assertEquals(1, engine.getSecondsRemaining());
        engine.resume();
        engine.tick();
        assertEquals(WorkoutStep.Phase.REST, engine.getPhase());
        assertEquals(1, engine.getCurrentRest());

        engine.skip();
        assertEquals(WorkoutStep.Phase.EXERCISE, engine.getPhase());
        assertEquals(2, engine.getCurrentSet());
    }

    @Test
    public void finalTickCompletesWorkout() {
        WorkoutEngine engine = new WorkoutEngine(new WorkoutPlan(Arrays.asList(
                step(WorkoutStep.Phase.EXERCISE, 1, 1, 1, 0, 0)
        )));

        engine.tick();

        assertTrue(engine.isComplete());
        assertFalse(engine.isPaused());
        assertEquals(WorkoutStep.Phase.COMPLETED, engine.getPhase());
        assertEquals(0, engine.getSecondsRemaining());
    }

    @Test
    public void restoresPausedAndCompletedStates() {
        WorkoutPlan plan = new WorkoutPlan(Arrays.asList(
                step(WorkoutStep.Phase.EXERCISE, 2, 1, 1, 0, 1),
                step(WorkoutStep.Phase.REST, 3, 0, 0, 1, 1)
        ));
        WorkoutEngine paused = WorkoutEngine.restore(plan, 1, 2, true, false);

        paused.tick();
        assertEquals(1, paused.getStepIndex());
        assertEquals(2, paused.getSecondsRemaining());
        assertTrue(paused.isPaused());

        WorkoutEngine complete = WorkoutEngine.restore(plan, 2, 0, false, true);
        assertTrue(complete.isComplete());
        assertEquals(WorkoutStep.Phase.COMPLETED, complete.getPhase());
    }

    @Test
    public void rejectsInvalidRestoredStates() {
        WorkoutPlan plan = new WorkoutPlan(Arrays.asList(
                step(WorkoutStep.Phase.EXERCISE, 2, 1, 1, 0, 0)
        ));

        assertThrows(IllegalArgumentException.class,
                () -> WorkoutEngine.restore(plan, 1, 1, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> WorkoutEngine.restore(plan, 0, 3, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> WorkoutEngine.restore(plan, 1, 0, true, true));
    }

    private static WorkoutStep step(
            WorkoutStep.Phase phase,
            int seconds,
            int set,
            int totalSets,
            int rest,
            int totalRests
    ) {
        return new WorkoutStep(phase, "动作", "介绍", seconds, 0, set, totalSets, rest, totalRests);
    }
}
