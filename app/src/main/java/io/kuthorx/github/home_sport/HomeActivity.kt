package io.kuthorx.github.home_sport

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        SystemBars.useOpaqueStatusBar(this)

        findViewById<View>(R.id.dailyEntryButton).setOnClickListener {
            openWorkout(WorkoutPlan.Mode.DAILY)
        }
        findViewById<View>(R.id.piriformisEntryButton).setOnClickListener {
            openWorkout(WorkoutPlan.Mode.PIRIFORMIS)
        }
        findViewById<View>(R.id.officeEntryButton).setOnClickListener {
            openWorkout(WorkoutPlan.Mode.OFFICE)
        }
        findViewById<View>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun openWorkout(mode: WorkoutPlan.Mode) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_MODE, mode.id),
        )
    }
}
