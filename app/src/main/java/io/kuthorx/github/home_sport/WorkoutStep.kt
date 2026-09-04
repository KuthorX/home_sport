package io.kuthorx.github.home_sport

class WorkoutStep(
    val phase: Phase,
    val name: String,
    val description: String,
    val durationSeconds: Int,
    val repetitions: Int,
    val setNumber: Int,
    val totalSets: Int,
    val restNumber: Int,
    val totalRests: Int,
) {
    init {
        require(durationSeconds > 0) { "durationSeconds must be positive" }
    }

    enum class Phase {
        EXERCISE,
        REST,
        COMPLETED,
    }
}
