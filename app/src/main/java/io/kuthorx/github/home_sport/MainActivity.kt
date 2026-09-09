package io.kuthorx.github.home_sport

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
        progressBar = findViewById(R.id.progressBar)
        primaryButton = findViewById(R.id.primaryButton)
        secondaryActions = findViewById(R.id.secondaryActions)

        plan = WorkoutPlan.daily()
        store = WorkoutStore(this)
        requestNotificationPermission()
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
        engine = store.restoreProgress(plan) ?: return false
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
                WorkoutService.resume(this)
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
                store.saveProgress(active, true)
                WorkoutService.resume(this)
                primaryButton.setText(R.string.pause_workout)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> pauseWorkout()
        }
        renderWorkout()
    }

    private fun startWorkout() {
        engine = WorkoutEngine(plan).also { store.saveProgress(it, true) }
        secondaryActions.visibility = View.VISIBLE
        primaryButton.setText(R.string.pause_workout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderWorkout()
        WorkoutService.start(this)
    }

    private fun pauseWorkout() {
        val active = engine ?: return
        if (active.isComplete || active.isPaused) return
        active.pause()
        store.saveProgress(active, true)
        WorkoutService.pause(this)
        primaryButton.setText(R.string.resume_workout)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun skipStep() {
        val active = engine ?: return
        if (active.isComplete) return
        WorkoutService.skip(this)
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
        WorkoutService.stop(this)
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
        progressText.setText(if (completedAll) R.string.complete_summary else R.string.partial_summary)
        exerciseGuide.setText(if (completedAll) R.string.cool_down_guide else R.string.partial_guide)
        progressBar.progress = 100
        primaryButton.setText(R.string.repeat_workout)
        secondaryActions.visibility = View.GONE
    }

    private fun renderReady() {
        phaseLabel.setText(R.string.status_ready)
        exerciseName.setText(R.string.todays_workout)
        countdown.text = formatTime(totalDurationSeconds())
        progressText.setText(R.string.ready_progress)
        exerciseGuide.setText(R.string.ready_guide)
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

    private fun refreshWorkout() {
        engine = store.restoreProgress(plan)
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
        private const val STATE_HAS_ENGINE = "has_engine"
        private const val STATE_STEP_INDEX = "step_index"
        private const val STATE_SECONDS = "seconds"
        private const val STATE_PAUSED = "paused"
        private const val STATE_COMPLETE = "complete"
        private const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
}
