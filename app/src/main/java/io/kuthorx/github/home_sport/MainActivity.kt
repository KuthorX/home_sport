package io.kuthorx.github.home_sport

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var mode: WorkoutPlan.Mode
    private lateinit var plan: WorkoutPlan
    private var engine: WorkoutEngine? = null
    private lateinit var store: WorkoutStore
    private var listeningForUpdates = false
    private val workoutUpdates = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshWorkout()
    }

    private lateinit var phaseLabel: TextView
    private lateinit var exerciseName: TextView
    private lateinit var countdown: TextView
    private lateinit var progressText: TextView
    private lateinit var exerciseGuide: TextView
    private lateinit var planSummary: TextView
    private lateinit var planTitle: TextView
    private lateinit var planDetails: LinearLayout
    private lateinit var safetyNote: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var primaryButton: AppCompatButton
    private lateinit var secondaryActions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SystemBars.useOpaqueStatusBar(this)

        phaseLabel = findViewById(R.id.phaseLabel)
        exerciseName = findViewById(R.id.exerciseName)
        countdown = findViewById(R.id.countdown)
        progressText = findViewById(R.id.progressText)
        exerciseGuide = findViewById(R.id.exerciseGuide)
        planSummary = findViewById(R.id.planSummary)
        planTitle = findViewById(R.id.planTitle)
        planDetails = findViewById(R.id.planDetails)
        safetyNote = findViewById(R.id.safetyNote)
        progressBar = findViewById(R.id.progressBar)
        primaryButton = findViewById(R.id.primaryButton)
        secondaryActions = findViewById(R.id.secondaryActions)

        store = WorkoutStore(this)
        mode = WorkoutPlan.Mode.fromId(intent.getStringExtra(EXTRA_MODE))
            ?: store.restoreMode()
        plan = WorkoutPlan.forMode(mode)
        applyModeContent()
        requestNotificationPermission()
        findViewById<View>(R.id.homeButton).setOnClickListener { finish() }
        primaryButton.setOnClickListener { onPrimaryAction() }
        findViewById<View>(R.id.skipButton).setOnClickListener { skipStep() }
        findViewById<View>(R.id.stopButton).setOnClickListener { confirmStop() }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        var restored = restoreStoredProgress()
        if (!restored && savedInstanceState?.getBoolean(STATE_HAS_ENGINE) == true) {
            restored = restoreBundleProgress(savedInstanceState)
        }
        if (!restored) renderReady()
    }

    private fun restoreStoredProgress(): Boolean {
        engine = store.restoreProgress(plan, mode) ?: return false
        showRestoredProgress()
        return true
    }

    private fun restoreBundleProgress(state: Bundle): Boolean = try {
        engine = WorkoutEngine.restore(
            plan,
            state.getInt(STATE_STEP_INDEX),
            state.getInt(STATE_SECONDS),
            state.getBoolean(STATE_PAUSED),
            state.getBoolean(STATE_COMPLETE),
        )
        showRestoredProgress()
        true
    } catch (_: IllegalArgumentException) {
        engine = null
        false
    }

    private fun showRestoredProgress() {
        val restored = engine ?: return
        if (restored.isComplete) {
            finishWorkout()
        } else {
            secondaryActions.visibility = View.VISIBLE
            renderWorkout()
            if (!restored.isPaused) {
                WorkoutService.resume(this, mode)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun onPrimaryAction() {
        val active = engine
        when {
            active == null || active.isComplete -> startWorkout()
            active.isPaused -> {
                active.resume()
                store.saveProgress(active, true, mode)
                WorkoutService.resume(this, mode)
                primaryButton.setText(R.string.pause_workout)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> pauseWorkout()
        }
        renderWorkout()
    }

    private fun startWorkout() {
        engine = WorkoutEngine(plan).also { store.saveProgress(it, true, mode) }
        secondaryActions.visibility = View.VISIBLE
        primaryButton.setText(R.string.pause_workout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderWorkout()
        WorkoutService.start(this, mode)
    }

    private fun pauseWorkout() {
        val active = engine ?: return
        if (active.isComplete || active.isPaused) return
        active.pause()
        store.saveProgress(active, true, mode)
        WorkoutService.pause(this, mode)
        primaryButton.setText(R.string.resume_workout)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun skipStep() {
        val active = engine ?: return
        if (active.isComplete) return
        WorkoutService.skip(this, mode)
    }

    private fun confirmStop() {
        pauseWorkout()
        MaterialAlertDialogBuilder(this)
            .setTitle("结束本次训练？")
            .setMessage("结束后会清除未完成进度，已经完成的动作仍保留在打卡记录。")
            .setNegativeButton("继续训练") { _, _ -> onPrimaryAction() }
            .setPositiveButton("结束") { _, _ -> resetWorkout() }
            .setOnCancelListener { onPrimaryAction() }
            .show()
    }

    private fun resetWorkout() {
        WorkoutService.stop(this, mode)
        store.clearProgress()
        engine = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderReady()
    }

    private fun finishWorkout() {
        store.clearProgress()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val completedAll = (engine?.skippedSteps ?: 0) == 0
        phaseLabel.setText(if (completedAll) R.string.status_complete else R.string.status_finished)
        exerciseName.setText(if (completedAll) R.string.complete_title else R.string.partial_title)
        countdown.setText(R.string.complete_word)
        progressText.setText(
            if (completedAll) {
                when (mode) {
                    WorkoutPlan.Mode.DAILY -> R.string.complete_summary
                    WorkoutPlan.Mode.PIRIFORMIS -> R.string.piriformis_complete_summary
                    WorkoutPlan.Mode.OFFICE -> R.string.office_complete_summary
                }
            } else {
                R.string.partial_summary
            },
        )
        exerciseGuide.setText(if (completedAll) R.string.cool_down_guide else R.string.partial_guide)
        progressBar.progress = 100
        primaryButton.setText(R.string.repeat_workout)
        secondaryActions.visibility = View.GONE
    }

    private fun renderReady() {
        phaseLabel.setText(R.string.status_ready)
        exerciseName.setText(
            when (mode) {
                WorkoutPlan.Mode.DAILY -> R.string.todays_workout
                WorkoutPlan.Mode.PIRIFORMIS -> R.string.piriformis_workout
                WorkoutPlan.Mode.OFFICE -> R.string.office_workout
            },
        )
        countdown.text = formatTime(totalDurationSeconds())
        progressText.setText(
            when (mode) {
                WorkoutPlan.Mode.DAILY -> R.string.ready_progress
                WorkoutPlan.Mode.PIRIFORMIS -> R.string.piriformis_ready_progress
                WorkoutPlan.Mode.OFFICE -> R.string.office_ready_progress
            },
        )
        exerciseGuide.setText(
            when (mode) {
                WorkoutPlan.Mode.DAILY -> R.string.ready_guide
                WorkoutPlan.Mode.PIRIFORMIS -> R.string.piriformis_ready_guide
                WorkoutPlan.Mode.OFFICE -> R.string.office_ready_guide
            },
        )
        progressBar.progress = 0
        primaryButton.setText(R.string.start_workout)
        secondaryActions.visibility = View.GONE
    }

    private fun renderWorkout() {
        val active = engine ?: return
        if (active.isComplete) return
        val step = active.currentStep() ?: return
        val rest = step.phase == WorkoutStep.Phase.REST
        phaseLabel.setText(
            when {
                active.isPaused -> R.string.status_paused
                rest -> R.string.status_rest
                else -> R.string.status_training
            },
        )
        exerciseName.text = step.name
        countdown.text = formatTime(active.secondsRemaining)
        progressText.text = when {
            rest -> getString(R.string.rest_progress, step.restNumber, step.totalRests)
            step.repetitions > 0 -> getString(
                R.string.set_progress_reps,
                step.setNumber,
                step.totalSets,
                step.repetitions,
            )
            else -> getString(R.string.set_progress, step.setNumber, step.totalSets)
        }
        exerciseGuide.text = step.description
        progressBar.progress = overallProgress()
        primaryButton.setText(
            if (active.isPaused) R.string.resume_workout else R.string.pause_workout,
        )
    }

    private fun applyModeContent() {
        when (mode) {
            WorkoutPlan.Mode.DAILY -> {
                planSummary.setText(R.string.plan_summary)
                planTitle.setText(R.string.todays_exercises)
                renderPlanDetails(R.string.plan_details)
                safetyNote.setText(R.string.safety_note)
            }
            WorkoutPlan.Mode.PIRIFORMIS -> {
                planSummary.setText(R.string.piriformis_plan_summary)
                planTitle.setText(R.string.piriformis_plan_title)
                renderPlanDetails(R.string.piriformis_plan_details)
                safetyNote.setText(R.string.piriformis_safety_note)
            }
            WorkoutPlan.Mode.OFFICE -> {
                planSummary.setText(R.string.office_plan_summary)
                planTitle.setText(R.string.office_plan_title)
                renderPlanDetails(R.string.office_plan_details)
                safetyNote.setText(R.string.office_safety_note)
            }
        }
    }

    private fun renderPlanDetails(resourceId: Int) {
        planDetails.removeAllViews()
        getString(resourceId)
            .split("\n\n")
            .filter(String::isNotBlank)
            .forEach { block ->
                val lineBreak = block.indexOf('\n')
                val title = if (lineBreak >= 0) block.substring(0, lineBreak) else block
                val body = if (lineBreak >= 0) block.substring(lineBreak + 1) else ""
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.exercise_card)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }
                card.addView(TextView(this).apply {
                    text = title
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary_dark))
                    textSize = 16f
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                })
                if (body.isNotBlank()) {
                    card.addView(TextView(this).apply {
                        text = body
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                        textSize = 14f
                        setLineSpacing(dp(3).toFloat(), 1f)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(6) }
                    })
                }
                planDetails.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(10) })
            }
    }

    private fun overallProgress(): Int {
        val active = engine ?: return 0
        var elapsed = 0
        for (step in plan.steps) {
            if (step === active.currentStep()) {
                elapsed += step.durationSeconds - active.secondsRemaining
                break
            }
            elapsed += step.durationSeconds
        }
        return minOf(100, elapsed * 100 / totalDurationSeconds())
    }

    private fun totalDurationSeconds() = plan.steps.sumOf { it.durationSeconds }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun formatTime(seconds: Int) =
        String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            workoutUpdates,
            IntentFilter(WorkoutService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        listeningForUpdates = true
        refreshWorkout()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mode = WorkoutPlan.Mode.fromId(intent.getStringExtra(EXTRA_MODE)) ?: store.restoreMode()
        plan = WorkoutPlan.forMode(mode)
        applyModeContent()
        refreshWorkout()
    }

    private fun refreshWorkout() {
        engine = store.restoreProgress(plan, mode)
        val active = engine
        when {
            active == null -> renderReady()
            active.isComplete -> finishWorkout()
            else -> {
                secondaryActions.visibility = View.VISIBLE
                renderWorkout()
            }
        }
    }

    override fun onStop() {
        if (listeningForUpdates) {
            unregisterReceiver(workoutUpdates)
            listeningForUpdates = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        engine?.let { active ->
            outState.putBoolean(STATE_HAS_ENGINE, true)
            outState.putInt(STATE_STEP_INDEX, active.stepIndex)
            outState.putInt(STATE_SECONDS, active.secondsRemaining)
            outState.putBoolean(STATE_PAUSED, active.isPaused)
            outState.putBoolean(STATE_COMPLETE, active.isComplete)
        }
        super.onSaveInstanceState(outState)
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    companion object {
        const val EXTRA_MODE = "workout_mode"
        private const val STATE_HAS_ENGINE = "has_engine"
        private const val STATE_STEP_INDEX = "step_index"
        private const val STATE_SECONDS = "seconds"
        private const val STATE_PAUSED = "paused"
        private const val STATE_COMPLETE = "complete"
        private const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
}
