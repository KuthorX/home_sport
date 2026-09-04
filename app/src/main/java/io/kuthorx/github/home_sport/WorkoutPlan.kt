package io.kuthorx.github.home_sport

import java.util.Collections

class WorkoutPlan(steps: List<WorkoutStep>) {
    val steps: List<WorkoutStep>

    init {
        require(steps.isNotEmpty()) { "steps must not be empty" }
        this.steps = Collections.unmodifiableList(ArrayList(steps))
    }

    companion object {
        private const val REST_SECONDS = 30

        @JvmStatic
        fun daily(): WorkoutPlan {
            val exercises = listOf(
                Exercise("低冲击开合跳", "左右脚交替点地，不跳起。收紧核心，摆臂到头顶，落脚保持轻柔。", 20, 60, 0),
                Exercise("靠墙静蹲", "背部贴墙，双脚向前，膝盖朝脚尖方向，保持均匀呼吸。", 3, 30, 0),
                Exercise("深蹲", "双脚与肩同宽，臀部向后坐，膝盖朝脚尖方向，起身时呼气。", 3, 45, 12),
                Exercise("跪姿俯卧撑", "膝盖着地，身体从头到膝保持直线，胸口靠近地面后推起。", 3, 45, 10),
                Exercise("平板支撑", "手肘在肩膀正下方，收紧腹部和臀部，身体保持直线。", 3, 25, 0),
            )
            val totalRests = exercises.sumOf(Exercise::sets) - 1
            val steps = ArrayList<WorkoutStep>()
            var restNumber = 0

            for (exercise in exercises) {
                for (set in 1..exercise.sets) {
                    steps += WorkoutStep(
                        WorkoutStep.Phase.EXERCISE,
                        exercise.name,
                        exercise.description,
                        exercise.durationSeconds,
                        exercise.repetitions,
                        set,
                        exercise.sets,
                        restNumber,
                        totalRests,
                    )
                    if (restNumber < totalRests) {
                        restNumber++
                        steps += WorkoutStep(
                            WorkoutStep.Phase.REST,
                            "休息",
                            "放松肌肉，缓慢呼吸，准备下一组。",
                            REST_SECONDS,
                            0,
                            0,
                            0,
                            restNumber,
                            totalRests,
                        )
                    }
                }
            }
            return WorkoutPlan(steps)
        }
    }

    private data class Exercise(
        val name: String,
        val description: String,
        val sets: Int,
        val durationSeconds: Int,
        val repetitions: Int,
    )
}
