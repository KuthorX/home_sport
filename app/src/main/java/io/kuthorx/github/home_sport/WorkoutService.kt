package io.kuthorx.github.home_sport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.util.Locale

class WorkoutService : Service(), TextToSpeech.OnInitListener {
    private val timer = Handler(Looper.getMainLooper())
    private lateinit var plan: WorkoutPlan
    private lateinit var store: WorkoutStore
    private var engine: WorkoutEngine? = null
    private var lastTickAtMillis = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

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
            broadcastState()

            if (active.isComplete) {
                finishWorkout()
                return
            }
            if (previous !== active.currentStep()) {
                announceCurrentStep()
                updateNotification()
            } else {
                announceCountdown(active.secondsRemaining)
            }

            timer.postDelayed(
                this,
                maxOf(50L, 1_000L - (SystemClock.elapsedRealtime() - lastTickAtMillis)),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        plan = WorkoutPlan.daily()
        store = WorkoutStore(this)
        engine = store.restoreProgress(plan)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWorkout()
            ACTION_RESUME -> resumeWorkout()
            ACTION_PAUSE -> pauseWorkout()
            ACTION_SKIP -> skipStep()
            ACTION_STOP -> stopWorkout()
            else -> restoreWorkout()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        val speech = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val result = speech.setLanguage(Locale.SIMPLIFIED_CHINESE)
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        speech.setSpeechRate(0.95f)
        pendingSpeech?.takeIf { ttsReady }?.let {
            speech.speak(it, TextToSpeech.QUEUE_FLUSH, null, "workout-ready")
        }
        pendingSpeech = null
    }

    override fun onDestroy() {
        timer.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun startWorkout() {
        engine = WorkoutEngine(plan).also { store.saveProgress(it, true) }
        startForeground(NOTIFICATION_ID, notification())
        announceCurrentStep("训练开始。全程保持低冲击，如有胸闷、头晕或明显不适，请立即停止。")
        startTimer()
        broadcastState()
    }

    private fun resumeWorkout() {
        val active = loadEngine() ?: return stopWorkout()
        active.resume()
        store.saveProgress(active, true)
        startForeground(NOTIFICATION_ID, notification())
        speak("继续训练")
        startTimer()
        broadcastState()
    }

    private fun pauseWorkout() {
        val active = loadEngine() ?: return stopWorkout()
        active.pause()
        store.saveProgress(active, true)
        timer.removeCallbacks(tick)
        tts?.stop()
        startForeground(NOTIFICATION_ID, notification())
        broadcastState()
    }

    private fun skipStep() {
        val active = loadEngine() ?: return stopWorkout()
        if (active.isComplete) return
        startForeground(NOTIFICATION_ID, notification())
        tts?.stop()
        active.skip()
        store.saveProgress(active, true)
        if (active.isComplete) {
            broadcastState()
            finishWorkout()
        } else {
            announceCurrentStep()
            updateNotification()
            broadcastState()
            if (!active.isPaused) startTimer()
        }
    }

    private fun restoreWorkout() {
        val active = loadEngine() ?: return stopSelf()
        if (active.isComplete) return finishWorkout()
        startForeground(NOTIFICATION_ID, notification())
        if (!active.isPaused) startTimer()
        broadcastState()
    }

    private fun stopWorkout() {
        timer.removeCallbacks(tick)
        tts?.stop()
        store.clearProgress()
        engine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastState()
    }

    private fun finishWorkout() {
        timer.removeCallbacks(tick)
        speak(
            if ((engine?.skippedSteps ?: 0) == 0) {
                "训练完成。缓慢走动并补充水分，让心率逐渐恢复。"
            } else {
                "本次训练结束。实际完成的动作已经记录。"
            },
        )
        val manager = getSystemService(NotificationManager::class.java)
        stopForeground(STOP_FOREGROUND_DETACH)
        manager.notify(NOTIFICATION_ID, notification(completed = true))
        timer.postDelayed({ stopSelf() }, 5_000L)
    }

    private fun loadEngine(): WorkoutEngine? {
        if (engine == null) engine = store.restoreProgress(plan)
        return engine
    }

    private fun startTimer() {
        timer.removeCallbacks(tick)
        lastTickAtMillis = SystemClock.elapsedRealtime()
        timer.postDelayed(tick, 1_000L)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification())
    }

    private fun notification(completed: Boolean = false): Notification {
        val active = engine
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_small)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (completed || active?.isComplete == true) {
            val completedAll = (active?.skippedSteps ?: 0) == 0
            return builder
                .setContentTitle(
                    getString(
                        if (completedAll) R.string.workout_notification_complete
                        else R.string.status_finished,
                    ),
                )
                .setContentText(
                    getString(if (completedAll) R.string.cool_down_guide else R.string.partial_guide),
                )
                .setAutoCancel(true)
                .build()
        }

        val step = active?.currentStep()
        val paused = active?.isPaused != false
        builder
            .setContentTitle(step?.name ?: getString(R.string.app_name))
            .setContentText(
                if (paused) getString(R.string.workout_notification_paused)
                else step?.description.orEmpty(),
            )
            .setOngoing(true)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                getString(if (paused) R.string.resume_workout else R.string.pause_workout),
                serviceIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 1),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_workout),
                serviceIntent(ACTION_STOP, 2),
            )
        if (!paused) {
            builder
                .setWhen(System.currentTimeMillis() + active.secondsRemaining * 1_000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.workout_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.workout_notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun broadcastState() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun announceCurrentStep(prefix: String = "") {
        val step = engine?.currentStep() ?: return
        if (step.phase == WorkoutStep.Phase.REST) {
            speak("$prefix 休息。第${step.restNumber}次，共${step.totalRests}次。${step.durationSeconds}秒。")
            return
        }
        val target = if (step.repetitions > 0) {
            "${step.repetitions}次，限时${step.durationSeconds}秒。"
        } else {
            "${step.durationSeconds}秒。"
        }
        speak("$prefix${step.name}。第${step.setNumber}组，共${step.totalSets}组。$target${step.description}")
    }

    private fun announceCountdown(seconds: Int) {
        if (seconds == 30 || seconds == 10 || seconds in 1..5) speak("${seconds}秒")
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "workout-${System.nanoTime()}")
        else pendingSpeech = text
    }

    companion object {
        const val ACTION_STATE_CHANGED = "io.kuthorx.github.home_sport.WORKOUT_STATE_CHANGED"
        private const val ACTION_START = "io.kuthorx.github.home_sport.START"
        private const val ACTION_RESUME = "io.kuthorx.github.home_sport.RESUME"
        private const val ACTION_PAUSE = "io.kuthorx.github.home_sport.PAUSE"
        private const val ACTION_SKIP = "io.kuthorx.github.home_sport.SKIP"
        private const val ACTION_STOP = "io.kuthorx.github.home_sport.STOP"
        private const val CHANNEL_ID = "workout_countdown"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) = send(context, ACTION_START)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun skip(context: Context) = send(context, ACTION_SKIP)
        fun stop(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WorkoutService::class.java).setAction(action),
            )
        }
    }
}
