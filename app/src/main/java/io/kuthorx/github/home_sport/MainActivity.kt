package io.kuthorx.github.home_sport

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val timer = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val active = engine ?: return
            if (active.isPaused || active.isComplete) return

            val now = SystemClock.elapsedRealtime()
            val elapsedSeconds = ((now - lastTickAtMillis) / 1_000L).toInt()
            if (elapsedSeconds == 0) {
                timer.postDelayed(this, 1_000L - (now - lastTickAtMillis))
                return
            }

            val previous = active.currentStep()
            repeat(elapsedSeconds) {
                if (active.isComplete) return@repeat
                val completed = active.currentStep() ?: return@repeat
                active.tick()
                if (completed !== active.currentStep()) {
                    store.completeStepAndSave(active, completed, LocalDate.now())
                }
            }
            store.saveProgress(active, false)
            lastTickAtMillis += elapsedSeconds * 1_000L
            renderWorkout()

            if (active.isComplete) {
                finishWorkout(true)
                return
            }
            if (previous !== active.currentStep()) announceCurrentStep()
            else announceCountdown(active.secondsRemaining)

            val delay = maxOf(
                50L,
                1_000L - (SystemClock.elapsedRealtime() - lastTickAtMillis),
            )
            timer.postDelayed(this, delay)
        }
    }

    private lateinit var plan: WorkoutPlan
    private var engine: WorkoutEngine? = null
    private lateinit var store: WorkoutStore
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var lastTickAtMillis = 0L

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
        tts = TextToSpeech(this, this)
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
            finishWorkout(false)
        } else {
            restored.pause()
            store.saveProgress(restored, true)
            secondaryActions.visibility = View.VISIBLE
            renderWorkout()
        }
    }

    override fun onInit(status: Int) {
        val speech = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            showTtsUnavailable()
            return
        }
        val result = speech.setLanguage(Locale.SIMPLIFIED_CHINESE)
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        speech.setSpeechRate(0.95f)
        if (ttsReady && pendingSpeech != null) {
            speech.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "workout-ready")
            pendingSpeech = null
        } else if (!ttsReady) {
            pendingSpeech = null
            showTtsUnavailable()
        }
    }

    private fun onPrimaryAction() {
        val active = engine
        when {
            active == null || active.isComplete -> startWorkout()
            active.isPaused -> {
                active.resume()
                store.saveProgress(active, false)
                primaryButton.setText(R.string.pause_workout)
                speak("继续训练", TextToSpeech.QUEUE_FLUSH)
                startTimer()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> pauseWorkout(true)
        }
        renderWorkout()
    }

    private fun startWorkout() {
        engine = WorkoutEngine(plan).also { store.saveProgress(it, true) }
        secondaryActions.visibility = View.VISIBLE
        primaryButton.setText(R.string.pause_workout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderWorkout()
        announceCurrentStep("训练开始。全程保持低冲击，如有胸闷、头晕或明显不适，请立即停止。")
        startTimer()
    }

    private fun pauseWorkout(announce: Boolean) {
        val active = engine ?: return
        if (active.isComplete || active.isPaused) return
        active.pause()
        store.saveProgress(active, true)
        timer.removeCallbacks(tick)
        tts?.stop()
        primaryButton.setText(R.string.resume_workout)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (announce) speak("训练已暂停", TextToSpeech.QUEUE_FLUSH)
    }

    private fun skipStep() {
        val active = engine ?: return
        if (active.isComplete) return
        tts?.stop()
        active.skip()
        store.saveProgress(active, true)
        renderWorkout()
        if (active.isComplete) {
            finishWorkout(true)
        } else {
            announceCurrentStep()
            if (!active.isPaused) startTimer()
        }
    }

    private fun confirmStop() {
        pauseWorkout(false)
        MaterialAlertDialogBuilder(this)
            .setTitle("结束本次训练？")
            .setMessage("结束后会清除未完成进度，已经完成的动作仍保留在打卡记录。")
            .setNegativeButton("继续训练") { _, _ -> onPrimaryAction() }
            .setPositiveButton("结束") { _, _ -> resetWorkout() }
            .setOnCancelListener { onPrimaryAction() }
            .show()
    }

    private fun resetWorkout() {
        timer.removeCallbacks(tick)
        tts?.stop()
        pendingSpeech = null
        store.clearProgress()
        engine = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderReady()
    }

    private fun finishWorkout(announce: Boolean) {
        timer.removeCallbacks(tick)
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
        if (announce) {
            speak(
                if (completedAll) {
                    "训练完成。缓慢走动并补充水分，让心率逐渐恢复。"
                } else {
                    "本次训练结束。实际完成的动作已经记录。"
                },
                TextToSpeech.QUEUE_FLUSH,
            )
        }
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

    private fun announceCurrentStep(prefix: String = "") {
        val step = engine?.currentStep() ?: return
        if (step.phase == WorkoutStep.Phase.REST) {
            speak(
                "$prefix 休息。第${step.restNumber}次，共${step.totalRests}次。${step.durationSeconds}秒。",
                TextToSpeech.QUEUE_FLUSH,
            )
            return
        }
        val target = if (step.repetitions > 0) {
            "${step.repetitions}次，限时${step.durationSeconds}秒。"
        } else {
            "${step.durationSeconds}秒。"
        }
        speak(
            "$prefix${step.name}。第${step.setNumber}组，共${step.totalSets}组。$target${step.description}",
            TextToSpeech.QUEUE_FLUSH,
        )
    }

    private fun announceCountdown(seconds: Int) {
        if (seconds == 30 || seconds == 10 || seconds in 1..5) {
            speak("${seconds}秒", TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun startTimer() {
        timer.removeCallbacks(tick)
        lastTickAtMillis = SystemClock.elapsedRealtime()
        timer.postDelayed(tick, 1_000L)
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

    private fun speak(text: String, queueMode: Int) {
        if (ttsReady) {
            tts?.speak(text, queueMode, null, "workout-${System.nanoTime()}")
        } else {
            pendingSpeech = if (queueMode == TextToSpeech.QUEUE_FLUSH || pendingSpeech == null) {
                text
            } else {
                pendingSpeech + text
            }
        }
    }

    private fun showTtsUnavailable() {
        Toast.makeText(this, "系统未安装中文语音数据，训练计时仍可使用", Toast.LENGTH_LONG).show()
    }

    override fun onStop() {
        super.onStop()
        pauseWorkout(false)
        tts?.stop()
        pendingSpeech = null
        renderWorkout()
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

    override fun onDestroy() {
        timer.removeCallbacks(tick)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val STATE_HAS_ENGINE = "has_engine"
        private const val STATE_STEP_INDEX = "step_index"
        private const val STATE_SECONDS = "seconds"
        private const val STATE_PAUSED = "paused"
        private const val STATE_COMPLETE = "complete"
    }
}
