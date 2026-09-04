package io.kuthorx.github.home_sport;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final String STATE_HAS_ENGINE = "has_engine";
    private static final String STATE_STEP_INDEX = "step_index";
    private static final String STATE_SECONDS = "seconds";
    private static final String STATE_PAUSED = "paused";
    private static final String STATE_COMPLETE = "complete";

    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (engine == null || engine.isPaused() || engine.isComplete()) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            int elapsedSeconds = (int) ((now - lastTickAtMillis) / 1_000L);
            if (elapsedSeconds == 0) {
                timer.postDelayed(this, 1_000L - (now - lastTickAtMillis));
                return;
            }
            WorkoutStep previous = engine.currentStep();
            for (int second = 0; second < elapsedSeconds && !engine.isComplete(); second++) {
                engine.tick();
            }
            lastTickAtMillis += elapsedSeconds * 1_000L;
            renderWorkout();
            if (engine.isComplete()) {
                finishWorkout(true);
                return;
            }
            if (previous != engine.currentStep()) {
                announceCurrentStep();
            } else {
                announceCountdown(engine.getSecondsRemaining());
            }
            long delay = Math.max(50L, 1_000L - (SystemClock.elapsedRealtime() - lastTickAtMillis));
            timer.postDelayed(this, delay);
        }
    };

    private WorkoutPlan plan;
    private WorkoutEngine engine;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private long lastTickAtMillis;

    private TextView phaseLabel;
    private TextView exerciseName;
    private TextView countdown;
    private TextView progressText;
    private TextView exerciseGuide;
    private ProgressBar progressBar;
    private MaterialButton primaryButton;
    private LinearLayout secondaryActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phaseLabel = findViewById(R.id.phaseLabel);
        exerciseName = findViewById(R.id.exerciseName);
        countdown = findViewById(R.id.countdown);
        progressText = findViewById(R.id.progressText);
        exerciseGuide = findViewById(R.id.exerciseGuide);
        progressBar = findViewById(R.id.progressBar);
        primaryButton = findViewById(R.id.primaryButton);
        secondaryActions = findViewById(R.id.secondaryActions);

        plan = WorkoutPlan.daily();
        tts = new TextToSpeech(this, this);
        primaryButton.setOnClickListener(view -> onPrimaryAction());
        findViewById(R.id.skipButton).setOnClickListener(view -> skipStep());
        findViewById(R.id.stopButton).setOnClickListener(view -> confirmStop());
        boolean restored = false;
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_HAS_ENGINE)) {
            try {
                engine = WorkoutEngine.restore(
                        plan,
                        savedInstanceState.getInt(STATE_STEP_INDEX),
                        savedInstanceState.getInt(STATE_SECONDS),
                        savedInstanceState.getBoolean(STATE_PAUSED),
                        savedInstanceState.getBoolean(STATE_COMPLETE)
                );
                restored = true;
                if (engine.isComplete()) {
                    finishWorkout(false);
                } else {
                    engine.pause();
                    secondaryActions.setVisibility(View.VISIBLE);
                    renderWorkout();
                }
            } catch (IllegalArgumentException ignored) {
                engine = null;
            }
        }
        if (!restored) {
            renderReady();
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            showTtsUnavailable();
            return;
        }
        int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                && result != TextToSpeech.LANG_NOT_SUPPORTED;
        tts.setSpeechRate(0.95f);
        if (ttsReady && pendingSpeech != null) {
            tts.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "workout-ready");
            pendingSpeech = null;
        } else if (!ttsReady) {
            pendingSpeech = null;
            showTtsUnavailable();
        }
    }

    private void onPrimaryAction() {
        if (engine == null || engine.isComplete()) {
            startWorkout();
        } else if (engine.isPaused()) {
            engine.resume();
            primaryButton.setText(R.string.pause_workout);
            speak("继续训练", TextToSpeech.QUEUE_FLUSH);
            startTimer();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            pauseWorkout(true);
        }
        renderWorkout();
    }

    private void startWorkout() {
        engine = new WorkoutEngine(plan);
        secondaryActions.setVisibility(View.VISIBLE);
        primaryButton.setText(R.string.pause_workout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderWorkout();
        announceCurrentStep("训练开始。全程保持低冲击，如有胸闷、头晕或明显不适，请立即停止。");
        startTimer();
    }

    private void pauseWorkout(boolean announce) {
        if (engine == null || engine.isComplete() || engine.isPaused()) {
            return;
        }
        engine.pause();
        timer.removeCallbacks(tick);
        if (tts != null) {
            tts.stop();
        }
        primaryButton.setText(R.string.resume_workout);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (announce) {
            speak("训练已暂停", TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void skipStep() {
        if (engine == null || engine.isComplete()) {
            return;
        }
        if (tts != null) {
            tts.stop();
        }
        engine.skip();
        renderWorkout();
        if (engine.isComplete()) {
            finishWorkout(true);
        } else {
            announceCurrentStep();
            if (!engine.isPaused()) {
                startTimer();
            }
        }
    }

    private void confirmStop() {
        pauseWorkout(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle("结束本次训练？")
                .setMessage("当前进度不会保存。今天状态不好时，少练一点也比勉强透支更合适。")
                .setNegativeButton("继续训练", (dialog, which) -> onPrimaryAction())
                .setPositiveButton("结束", (dialog, which) -> resetWorkout())
                .setOnCancelListener(dialog -> onPrimaryAction())
                .show();
    }

    private void resetWorkout() {
        timer.removeCallbacks(tick);
        if (tts != null) {
            tts.stop();
        }
        pendingSpeech = null;
        engine = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderReady();
    }

    private void finishWorkout(boolean announce) {
        timer.removeCallbacks(tick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        phaseLabel.setText(R.string.status_complete);
        exerciseName.setText(R.string.complete_title);
        countdown.setText(R.string.complete_word);
        progressText.setText(R.string.complete_summary);
        exerciseGuide.setText(R.string.cool_down_guide);
        progressBar.setProgress(100);
        primaryButton.setText(R.string.repeat_workout);
        secondaryActions.setVisibility(View.GONE);
        if (announce) {
            speak("训练完成。缓慢走动并补充水分，让心率逐渐恢复。", TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void renderReady() {
        phaseLabel.setText(R.string.status_ready);
        exerciseName.setText(R.string.todays_workout);
        countdown.setText(formatTime(totalDurationSeconds()));
        progressText.setText(R.string.ready_progress);
        exerciseGuide.setText(R.string.ready_guide);
        progressBar.setProgress(0);
        primaryButton.setText(R.string.start_workout);
        secondaryActions.setVisibility(View.GONE);
    }

    private void renderWorkout() {
        if (engine == null || engine.isComplete()) {
            return;
        }
        WorkoutStep step = engine.currentStep();
        boolean rest = step.getPhase() == WorkoutStep.Phase.REST;
        phaseLabel.setText(engine.isPaused() ? R.string.status_paused
                : rest ? R.string.status_rest : R.string.status_training);
        exerciseName.setText(step.getName());
        countdown.setText(formatTime(engine.getSecondsRemaining()));
        if (rest) {
            progressText.setText(getString(R.string.rest_progress,
                    step.getRestNumber(), step.getTotalRests()));
        } else {
            progressText.setText(step.getRepetitions() > 0
                    ? getString(R.string.set_progress_reps, step.getSetNumber(),
                            step.getTotalSets(), step.getRepetitions())
                    : getString(R.string.set_progress, step.getSetNumber(), step.getTotalSets()));
        }
        exerciseGuide.setText(step.getDescription());
        progressBar.setProgress(overallProgress());
        primaryButton.setText(engine.isPaused() ? R.string.resume_workout : R.string.pause_workout);
    }

    private void announceCurrentStep() {
        announceCurrentStep("");
    }

    private void announceCurrentStep(String prefix) {
        WorkoutStep step = engine.currentStep();
        if (step.getPhase() == WorkoutStep.Phase.REST) {
            speak(prefix + "休息。第" + step.getRestNumber() + "次，共" + step.getTotalRests()
                    + "次。" + step.getDurationSeconds() + "秒。", TextToSpeech.QUEUE_FLUSH);
            return;
        }
        String target = step.getRepetitions() > 0
                ? step.getRepetitions() + "次，限时" + step.getDurationSeconds() + "秒。"
                : step.getDurationSeconds() + "秒。";
        speak(prefix + step.getName() + "。第" + step.getSetNumber() + "组，共" + step.getTotalSets()
                + "组。" + target + step.getDescription(), TextToSpeech.QUEUE_FLUSH);
    }

    private void announceCountdown(int seconds) {
        if (seconds == 30 || seconds == 10 || seconds <= 5 && seconds > 0) {
            speak(seconds + "秒", TextToSpeech.QUEUE_FLUSH);
        }
    }

    private void startTimer() {
        timer.removeCallbacks(tick);
        lastTickAtMillis = SystemClock.elapsedRealtime();
        timer.postDelayed(tick, 1_000L);
    }

    private int overallProgress() {
        List<WorkoutStep> steps = plan.getSteps();
        int elapsed = 0;
        for (WorkoutStep step : steps) {
            if (step == engine.currentStep()) {
                elapsed += step.getDurationSeconds() - engine.getSecondsRemaining();
                break;
            }
            elapsed += step.getDurationSeconds();
        }
        return Math.min(100, elapsed * 100 / totalDurationSeconds());
    }

    private int totalDurationSeconds() {
        int total = 0;
        for (WorkoutStep step : plan.getSteps()) {
            total += step.getDurationSeconds();
        }
        return total;
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void speak(String text, int queueMode) {
        if (ttsReady) {
            tts.speak(text, queueMode, null, "workout-" + System.nanoTime());
        } else {
            pendingSpeech = queueMode == TextToSpeech.QUEUE_FLUSH || pendingSpeech == null
                    ? text : pendingSpeech + text;
        }
    }

    private void showTtsUnavailable() {
        Toast.makeText(this, "系统未安装中文语音数据，训练计时仍可使用", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        pauseWorkout(false);
        if (tts != null) {
            tts.stop();
        }
        pendingSpeech = null;
        renderWorkout();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (engine != null) {
            outState.putBoolean(STATE_HAS_ENGINE, true);
            outState.putInt(STATE_STEP_INDEX, engine.getStepIndex());
            outState.putInt(STATE_SECONDS, engine.getSecondsRemaining());
            outState.putBoolean(STATE_PAUSED, engine.isPaused());
            outState.putBoolean(STATE_COMPLETE, engine.isComplete());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        timer.removeCallbacks(tick);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
