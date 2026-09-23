package io.kuthorx.github.home_sport

import java.util.Collections

class WorkoutPlan(
    steps: List<WorkoutStep>,
    val mode: Mode = Mode.DAILY,
) {
    enum class Mode(val id: String) {
        DAILY("daily"),
        PIRIFORMIS("piriformis"),
        OFFICE("office"),
        ;

        companion object {
            @JvmStatic
            fun fromId(id: String?): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    val steps: List<WorkoutStep>

    init {
        require(steps.isNotEmpty()) { "steps must not be empty" }
        this.steps = Collections.unmodifiableList(ArrayList(steps))
    }

    companion object {
        private const val REST_SECONDS = 30

        @JvmStatic
        fun forMode(mode: Mode): WorkoutPlan = when (mode) {
            Mode.DAILY -> daily()
            Mode.PIRIFORMIS -> piriformis()
            Mode.OFFICE -> office()
        }

        @JvmStatic
        fun daily(): WorkoutPlan = buildPlan(
            Mode.DAILY,
            listOf(
                Exercise("低冲击开合跳", "左右脚交替点地，不跳起。收紧核心，摆臂到头顶，落脚保持轻柔。", 20, 60, 0),
                Exercise("靠墙静蹲", "背部贴墙，双脚向前，膝盖朝脚尖方向，保持均匀呼吸。", 3, 30, 0),
                Exercise("深蹲", "双脚与肩同宽，臀部向后坐，膝盖朝脚尖方向，起身时呼气。", 3, 45, 12),
                Exercise("跪姿俯卧撑", "膝盖着地，身体从头到膝保持直线，胸口靠近地面后推起。", 3, 45, 10),
                Exercise("平板支撑", "手肘在肩膀正下方，收紧腹部和臀部，身体保持直线。", 3, 25, 0),
            ),
        )

        @JvmStatic
        fun piriformis(): WorkoutPlan = buildPlan(
            Mode.PIRIFORMIS,
            listOf(
                Exercise(
                    "卧姿翘二郎腿拉伸",
                    "仰卧屈膝，将右脚踝跨到左膝呈 4 字，抱住左大腿向胸口靠近。保持 20–30 秒，呼吸平稳，左右两侧完成。",
                    3,
                    30,
                    0,
                ),
                Exercise(
                    "猫狗式",
                    "四足跪姿，吸气时抬头塌腰，呼气时含胸拱背收腹。动作缓慢顺畅，重复 8–10 次。",
                    1,
                    60,
                    10,
                ),
                Exercise(
                    "臀桥",
                    "仰卧屈膝，双脚与臀同宽。呼气收紧臀部和核心抬起骨盆，保持身体成一直线，避免过度拱腰。",
                    3,
                    45,
                    15,
                ),
                Exercise(
                    "蚌式展开",
                    "侧卧屈膝约 90 度，脚跟并拢，保持骨盆稳定，缓慢打开上方膝盖再还原。每侧 15 次。",
                    3,
                    45,
                    15,
                ),
            ),
        )

        @JvmStatic
        fun office(): WorkoutPlan = buildPlan(
            Mode.OFFICE,
            listOf(
                Exercise(
                    "坐姿猫牛式",
                    "坐在椅子前半段，双手放在膝上。吸气时抬头打开胸口，呼气时含胸收腹，动作缓慢。",
                    1,
                    60,
                    8,
                ),
                Exercise(
                    "坐姿躯干扭转",
                    "坐姿保持骨盆稳定，双手交叉于胸前，缓慢向左右旋转躯干，不要用力甩动。",
                    1,
                    40,
                    0,
                ),
                Exercise(
                    "坐姿“4”字拉伸",
                    "坐稳后将一侧脚踝放到另一侧膝上，背部保持平直，身体微微前倾至臀部温和拉伸。左右两侧完成。",
                    1,
                    30,
                    0,
                ),
                Exercise(
                    "靠墙/靠背胸椎伸展",
                    "背部贴墙或椅背，双手放在脑后，保持腰部舒适，打开胸口并缓慢回到起始位。",
                    1,
                    45,
                    0,
                ),
            ),
        )

        private fun buildPlan(mode: Mode, exercises: List<Exercise>): WorkoutPlan {
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
            return WorkoutPlan(steps, mode)
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
