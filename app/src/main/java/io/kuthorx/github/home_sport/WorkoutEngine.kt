package io.kuthorx.github.home_sport

class WorkoutEngine private constructor(
    plan: WorkoutPlan,
    private var mutableStepIndex: Int,
    private var mutableSecondsRemaining: Int,
    private var mutablePaused: Boolean,
    private var mutableComplete: Boolean,
    private var mutableSkippedSteps: Int,
) {
    private val steps = plan.steps

    val stepIndex: Int
        get() = mutableStepIndex

    val secondsRemaining: Int
        get() = mutableSecondsRemaining

    val isPaused: Boolean
        get() = mutablePaused

    val isComplete: Boolean
        get() = mutableComplete

    val skippedSteps: Int
        get() = mutableSkippedSteps

    constructor(plan: WorkoutPlan) : this(
        plan,
        mutableStepIndex = 0,
        mutableSecondsRemaining = plan.steps[0].durationSeconds,
        mutablePaused = false,
        mutableComplete = false,
        mutableSkippedSteps = 0,
    )

    init {
        require(mutableSkippedSteps >= 0) { "skippedSteps must not be negative" }
        if (mutableComplete) {
            require(mutableStepIndex == steps.size && mutableSecondsRemaining == 0 && !mutablePaused) {
                "invalid completed workout state"
            }
        } else {
            require(
                mutableStepIndex in steps.indices &&
                    mutableSecondsRemaining > 0 &&
                    mutableSecondsRemaining <= steps[mutableStepIndex].durationSeconds,
            ) { "invalid active workout state" }
        }
    }

    fun tick() {
        if (mutablePaused || mutableComplete) return
        mutableSecondsRemaining--
        if (mutableSecondsRemaining == 0) advance()
    }

    fun pause() {
        if (!mutableComplete) mutablePaused = true
    }

    fun resume() {
        mutablePaused = false
    }

    fun skip() {
        if (!mutableComplete) {
            mutableSkippedSteps++
            advance()
        }
    }

    private fun advance() {
        mutableStepIndex++
        if (mutableStepIndex == steps.size) {
            mutableComplete = true
            mutablePaused = false
            mutableSecondsRemaining = 0
            return
        }
        mutableSecondsRemaining = currentStep()!!.durationSeconds
    }

    fun currentStep(): WorkoutStep? = if (mutableComplete) null else steps[mutableStepIndex]

    val phase: WorkoutStep.Phase
        get() = if (mutableComplete) WorkoutStep.Phase.COMPLETED else currentStep()!!.phase

    val currentSet: Int
        get() = if (mutableComplete) 0 else currentStep()!!.setNumber

    val totalSets: Int
        get() = if (mutableComplete) 0 else currentStep()!!.totalSets

    val currentRest: Int
        get() = if (mutableComplete) totalRests else currentStep()!!.restNumber

    val totalRests: Int
        get() = steps[0].totalRests

    companion object {
        @JvmStatic
        fun restore(
            plan: WorkoutPlan,
            stepIndex: Int,
            secondsRemaining: Int,
            paused: Boolean,
            complete: Boolean,
        ): WorkoutEngine = restore(plan, stepIndex, secondsRemaining, paused, complete, 0)

        @JvmStatic
        fun restore(
            plan: WorkoutPlan,
            stepIndex: Int,
            secondsRemaining: Int,
            paused: Boolean,
            complete: Boolean,
            skippedSteps: Int,
        ): WorkoutEngine = WorkoutEngine(
            plan,
            stepIndex,
            secondsRemaining,
            paused,
            complete,
            skippedSteps,
        )
    }
}
