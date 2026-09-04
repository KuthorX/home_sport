package io.kuthorx.github.home_sport;

import java.util.Objects;

public final class WorkoutStep {
    public enum Phase {
        EXERCISE,
        REST,
        COMPLETED
    }

    private final Phase phase;
    private final String name;
    private final String description;
    private final int durationSeconds;
    private final int repetitions;
    private final int setNumber;
    private final int totalSets;
    private final int restNumber;
    private final int totalRests;

    public WorkoutStep(
            Phase phase,
            String name,
            String description,
            int durationSeconds,
            int repetitions,
            int setNumber,
            int totalSets,
            int restNumber,
            int totalRests
    ) {
        this.phase = Objects.requireNonNull(phase);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        this.durationSeconds = durationSeconds;
        this.repetitions = repetitions;
        this.setNumber = setNumber;
        this.totalSets = totalSets;
        this.restNumber = restNumber;
        this.totalRests = totalRests;
    }

    public Phase getPhase() { return phase; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getRepetitions() { return repetitions; }
    public int getSetNumber() { return setNumber; }
    public int getTotalSets() { return totalSets; }
    public int getRestNumber() { return restNumber; }
    public int getTotalRests() { return totalRests; }
}
