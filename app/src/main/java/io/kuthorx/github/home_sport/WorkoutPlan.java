package io.kuthorx.github.home_sport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorkoutPlan {
    private static final int REST_SECONDS = 30;

    private final List<WorkoutStep> steps;

    public WorkoutPlan(List<WorkoutStep> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public List<WorkoutStep> getSteps() {
        return steps;
    }

    public static WorkoutPlan daily() {
        List<Exercise> exercises = Arrays.asList(
                new Exercise("低冲击开合跳", "左右脚交替点地，不跳起。收紧核心，摆臂到头顶，落脚保持轻柔。", 20, 60, 0),
                new Exercise("靠墙静蹲", "背部贴墙，双脚向前，膝盖朝脚尖方向，保持均匀呼吸。", 3, 30, 0),
                new Exercise("深蹲", "双脚与肩同宽，臀部向后坐，膝盖朝脚尖方向，起身时呼气。", 3, 45, 12),
                new Exercise("跪姿俯卧撑", "膝盖着地，身体从头到膝保持直线，胸口靠近地面后推起。", 3, 45, 10),
                new Exercise("平板支撑", "手肘在肩膀正下方，收紧腹部和臀部，身体保持直线。", 3, 25, 0)
        );

        int totalRests = exercises.stream().mapToInt(exercise -> exercise.sets).sum() - 1;
        List<WorkoutStep> steps = new ArrayList<>();
        int restNumber = 0;
        for (Exercise exercise : exercises) {
            for (int set = 1; set <= exercise.sets; set++) {
                steps.add(new WorkoutStep(
                        WorkoutStep.Phase.EXERCISE,
                        exercise.name,
                        exercise.description,
                        exercise.durationSeconds,
                        exercise.repetitions,
                        set,
                        exercise.sets,
                        restNumber,
                        totalRests
                ));
                if (restNumber < totalRests) {
                    restNumber++;
                    steps.add(new WorkoutStep(
                            WorkoutStep.Phase.REST,
                            "休息",
                            "放松肌肉，缓慢呼吸，准备下一组。",
                            REST_SECONDS,
                            0,
                            0,
                            0,
                            restNumber,
                            totalRests
                    ));
                }
            }
        }
        return new WorkoutPlan(steps);
    }

    private static final class Exercise {
        private final String name;
        private final String description;
        private final int sets;
        private final int durationSeconds;
        private final int repetitions;

        private Exercise(String name, String description, int sets, int durationSeconds, int repetitions) {
            this.name = name;
            this.description = description;
            this.sets = sets;
            this.durationSeconds = durationSeconds;
            this.repetitions = repetitions;
        }
    }
}
