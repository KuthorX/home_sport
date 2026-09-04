package io.kuthorx.github.home_sport;

import java.util.List;

public final class WorkoutEngine {
    private final List<WorkoutStep> steps;
    private int stepIndex;
    private int secondsRemaining;
    private boolean paused;
    private boolean complete;

    public WorkoutEngine(WorkoutPlan plan) {
        steps = plan.getSteps();
        secondsRemaining = steps.get(0).getDurationSeconds();
    }

    private WorkoutEngine(
            WorkoutPlan plan,
            int stepIndex,
            int secondsRemaining,
            boolean paused,
            boolean complete
    ) {
        steps = plan.getSteps();
        if (complete) {
            if (stepIndex != steps.size() || secondsRemaining != 0 || paused) {
                throw new IllegalArgumentException("invalid completed workout state");
            }
        } else if (stepIndex < 0 || stepIndex >= steps.size()
                || secondsRemaining <= 0
                || secondsRemaining > steps.get(stepIndex).getDurationSeconds()) {
            throw new IllegalArgumentException("invalid active workout state");
        }
        this.stepIndex = stepIndex;
        this.secondsRemaining = secondsRemaining;
        this.paused = paused;
        this.complete = complete;
    }

    public static WorkoutEngine restore(
            WorkoutPlan plan,
            int stepIndex,
            int secondsRemaining,
            boolean paused,
            boolean complete
    ) {
        return new WorkoutEngine(plan, stepIndex, secondsRemaining, paused, complete);
    }

    public void tick() {
        if (paused || complete) {
            return;
        }
        secondsRemaining--;
        if (secondsRemaining == 0) {
            advance();
        }
    }

    public void pause() {
        if (!complete) {
            paused = true;
        }
    }

    public void resume() {
        paused = false;
    }

    public void skip() {
        if (!complete) {
            advance();
        }
    }

    private void advance() {
        stepIndex++;
        if (stepIndex == steps.size()) {
            complete = true;
            paused = false;
            secondsRemaining = 0;
            return;
        }
        secondsRemaining = currentStep().getDurationSeconds();
    }

    public WorkoutStep currentStep() {
        return complete ? null : steps.get(stepIndex);
    }

    public WorkoutStep.Phase getPhase() {
        return complete ? WorkoutStep.Phase.COMPLETED : currentStep().getPhase();
    }

    public int getSecondsRemaining() { return secondsRemaining; }
    public int getStepIndex() { return stepIndex; }
    public int getCurrentSet() { return complete ? 0 : currentStep().getSetNumber(); }
    public int getTotalSets() { return complete ? 0 : currentStep().getTotalSets(); }
    public int getCurrentRest() { return complete ? getTotalRests() : currentStep().getRestNumber(); }
    public int getTotalRests() { return steps.get(0).getTotalRests(); }
    public boolean isPaused() { return paused; }
    public boolean isComplete() { return complete; }

}
