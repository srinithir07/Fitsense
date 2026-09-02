package com.example.poseexercise.posedetector.classification;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Looper;

import androidx.annotation.WorkerThread;

import android.util.Log;

import com.example.poseexercise.data.PostureResult;
import com.google.common.base.Preconditions;
import com.google.mlkit.vision.pose.Pose;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accepts a Pose and runs pose classification.
 */
public class PoseClassifierProcessor {
    private static final String TAG = "PoseClassifierProcessor";
    private static final String SQUAT_FILE = "pose_poses_csv/squats.csv";
    private static final String PUSH_UP_FILE = "pose_poses_csv/pushups.csv";
    private static final String SIT_UP_FILE = "pose_poses_csv/situp.csv";

    private static final String LUNGE_FILE = "pose_poses_csv/lunges.csv";
    private static final String CHEST_PRESS_FILE = "pose_poses_csv/chestpress.csv";
    private static final String DEAD_LIFT_FILE = "pose_poses_csv/deadlifts.csv";
    private static final String SHOULDER_PRESS_FILE = "pose_poses_csv/shoulderpress.csv";

    private static final String WARRIOR_YOGA_FILE = "pose_poses_csv/warrior.csv";
    private static final String TREE_YOGA_FILE = "pose_poses_csv/tree.csv";

    private static final String NEUTRAL_STANDING_FILE = "pose_poses_csv/neutral_standing.csv";

    public static final String SQUATS_CLASS = "squats";

    public static final String PUSHUPS_CLASS = "pushups";

    public static final String SITUP_UP_CLASS = "situp";

    public static final String LUNGES_CLASS = "lunges";

    public static final String CHEST_PRESS_CLASS = "chestpress";

    public static final String DEAD_LIFT_CLASS = "deadlifts";

    public static final String SHOULDER_PRESS_CLASS = "shoulderpress";

    public static final String WARRIOR_YOGA_CLASS = "warrior";
    public static final String WARRIOR_CLASS = "warrior";

    public static final String TREE_YOGA_CLASS = "tree";
    public static final String YOGA_TREE_CLASS = "tree";

    public static final String[] POSE_CLASSES = {
            SQUATS_CLASS,
            PUSHUPS_CLASS,
            SITUP_UP_CLASS,
            LUNGES_CLASS,
            CHEST_PRESS_CLASS,
            DEAD_LIFT_CLASS,
            SHOULDER_PRESS_CLASS,
            WARRIOR_YOGA_CLASS,
            TREE_YOGA_CLASS,
    };

    private final boolean isStreamMode;

    private EMASmoothing emaSmoothing;
    private List<RepetitionCounter> repCounters;
    private PoseClassifier poseClassifier;
    private final FitSenseFormAnalyzer fitSenseFormAnalyzer =
            new FitSenseFormAnalyzer();
    private final Map<String, PostureResult> postureResults = new HashMap<>();
    private String lastActiveExerciseClass = null;
    private long lastLogTimeMs = 0;

    @WorkerThread
    public PoseClassifierProcessor(Context context, boolean isStreamMode, List<String> plan) {
        Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
        this.isStreamMode = isStreamMode;
        if (isStreamMode) {
            emaSmoothing = new EMASmoothing();
            repCounters = new ArrayList<>();
        }

        if (plan != null) {
            Log.d("pose_classifier_processor: ", plan.toString());
            Log.d("pose_classifier_processor: ", mapExercisesToFiles(plan).toString());
        }

        combineAndLoadPoseSamples(context, mapExercisesToFiles(plan));
    }

    private void combineAndLoadPoseSamples(Context context, List<String> mappedPlan) {
        String combinedFilePath = context.getFilesDir().getPath() + File.separator + "combined_poses.csv";
        createNewFileReplacingPrevious(combinedFilePath);
        combineCSVFiles(context, combinedFilePath, mappedPlan);
        loadPoseSamples(context, combinedFilePath);
    }

    private void createNewFileReplacingPrevious(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && !file.isDirectory()) {
                file.delete();
            }
            file.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Error creating file: " + filePath + "\n" + e);
        }
    }

    private void combineCSVFiles(Context context, String outputPath, List<String> inputFiles) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            for (String inputFile : inputFiles) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(inputFile)));
                String csvLine = reader.readLine();
                while (csvLine != null) {
                    writer.println(csvLine);
                    csvLine = reader.readLine();
                }
                writer.println();
                reader.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error when combining CSV files.\n" + e);
        }
    }

    private static List<String> mapExercisesToFiles(List<String> exercises) {
        List<String> files = new ArrayList<>();
        Set<String> uniqueFileNames = new HashSet<>();

        if (exercises != null) {
            for (String exercise : exercises) {
                switch (exercise) {
                    case "Squat" -> {
                        addUniqueFile(files, uniqueFileNames, SQUAT_FILE);
                        addUniqueFile(files, uniqueFileNames, NEUTRAL_STANDING_FILE);
                        addUniqueFile(files, uniqueFileNames, LUNGE_FILE);
                    }
                    case "Push up" -> addUniqueFile(files, uniqueFileNames, PUSH_UP_FILE);
                    case "Sit up" -> addUniqueFile(files, uniqueFileNames, SIT_UP_FILE);
                    case "Lunge" -> {
                        addUniqueFile(files, uniqueFileNames, LUNGE_FILE);
                        addUniqueFile(files, uniqueFileNames, NEUTRAL_STANDING_FILE);
                        addUniqueFile(files, uniqueFileNames, SQUAT_FILE);
                    }
                    case "Chest press" -> addUniqueFile(files, uniqueFileNames, CHEST_PRESS_FILE);
                    case "Dead lift" -> addUniqueFile(files, uniqueFileNames, DEAD_LIFT_FILE);
                    case "Shoulder press" -> addUniqueFile(files, uniqueFileNames, SHOULDER_PRESS_FILE);
                    default -> {}
                }
            }
        }

        addUniqueFile(files, uniqueFileNames, LUNGE_FILE);
        addUniqueFile(files, uniqueFileNames, NEUTRAL_STANDING_FILE);
        addUniqueFile(files, uniqueFileNames, SQUAT_FILE);
        files.add(WARRIOR_YOGA_FILE);
        files.add(TREE_YOGA_FILE);

        return files;
    }

    private static void addUniqueFile(List<String> files, Set<String> uniqueFileNames, String fileName) {
        if (uniqueFileNames.add(fileName)) {
            files.add(fileName);
        }
    }

    private void loadPoseSamples(Context context, String filePath) {
        List<PoseSample> poseSamples = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            String csvLine = reader.readLine();
            while (csvLine != null) {
                PoseSample poseSample = PoseSample.getPoseSample(csvLine, ",");
                if (poseSample != null) {
                    poseSamples.add(poseSample);
                }
                csvLine = reader.readLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error when loading pose samples.\n" + e);
        }
        poseClassifier = new PoseClassifier(poseSamples);
        if (isStreamMode) {
            for (String className : POSE_CLASSES) {
                repCounters.add(new RepetitionCounter(className));
            }
        }
    }

    @WorkerThread
    public Map<String, PostureResult> getPoseResult(Pose pose) {
        Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());

        ClassificationResult classification = poseClassifier.classify(pose);

        if (isStreamMode) {
            classification = emaSmoothing.getSmoothedResult(classification);

            if (pose.getAllPoseLandmarks().isEmpty()) {
                return new HashMap<>(postureResults);
            }

            for (RepetitionCounter repCounter : repCounters) {
                int repsBefore = repCounter.getNumRepeats();
                int repsAfter = repCounter.addClassificationResult(classification);
                if (repsAfter > repsBefore) {
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                    postureResults.put(repCounter.getClassName(), new PostureResult(repsAfter, 0, repCounter.getClassName()));
                    break;
                }
            }
        }

        if (!pose.getAllPoseLandmarks().isEmpty()) {
            String maxConfidenceClass = classification.getMaxConfidenceClass();

            if (!"neutral_standing".equalsIgnoreCase(maxConfidenceClass)) {
                lastActiveExerciseClass = maxConfidenceClass;
            }

            String targetExerciseForAnalysis = ("neutral_standing".equalsIgnoreCase(maxConfidenceClass) && lastActiveExerciseClass != null)
                    ? lastActiveExerciseClass
                    : maxConfidenceClass;

            FitSenseFormAnalyzer.FormAnalysis formAnalysis =
                    fitSenseFormAnalyzer.analyze(pose, targetExerciseForAnalysis);

            long nowMs = android.os.SystemClock.elapsedRealtime();
            if (nowMs - lastLogTimeMs > 1000) {
                lastLogTimeMs = nowMs;
                Log.d("FitSenseDebug", "exercise=" + targetExerciseForAnalysis
                        + " form=" + formAnalysis.getFormScore()
                        + " risk=" + formAnalysis.getRiskScore()
                        + " level=" + formAnalysis.getRiskLevel()
                        + " warning=" + formAnalysis.getWarning());
            }

            if (postureResults.containsKey(maxConfidenceClass)) {
                PostureResult result = postureResults.get(maxConfidenceClass);
                if (result != null) {
                    result.setConfidence(classification.getClassConfidence(maxConfidenceClass) / poseClassifier.confidenceRange());
                    result.formScore = formAnalysis.getFormScore();
                    result.riskScore = formAnalysis.getRiskScore();
                    result.riskLevel = formAnalysis.getRiskLevel();
                    result.warning = formAnalysis.getWarning();

                    result.jointMetric1Label = formAnalysis.getJointMetric1Label();
                    result.jointMetric1Value = formAnalysis.getJointMetric1Value();
                    result.jointMetric2Label = formAnalysis.getJointMetric2Label();
                    result.jointMetric2Value = formAnalysis.getJointMetric2Value();
                    result.jointMetric3Label = formAnalysis.getJointMetric3Label();
                    result.jointMetric3Value = formAnalysis.getJointMetric3Value();
                    result.jointMetric4Label = formAnalysis.getJointMetric4Label();
                    result.jointMetric4Value = formAnalysis.getJointMetric4Value();
                }
            } else {
                PostureResult result = new PostureResult(
                        0,
                        classification.getClassConfidence(maxConfidenceClass) / poseClassifier.confidenceRange(),
                        maxConfidenceClass);

                result.formScore = formAnalysis.getFormScore();
                result.riskScore = formAnalysis.getRiskScore();
                result.riskLevel = formAnalysis.getRiskLevel();
                result.warning = formAnalysis.getWarning();

                result.jointMetric1Label = formAnalysis.getJointMetric1Label();
                result.jointMetric1Value = formAnalysis.getJointMetric1Value();
                result.jointMetric2Label = formAnalysis.getJointMetric2Label();
                result.jointMetric2Value = formAnalysis.getJointMetric2Value();
                result.jointMetric3Label = formAnalysis.getJointMetric3Label();
                result.jointMetric3Value = formAnalysis.getJointMetric3Value();
                result.jointMetric4Label = formAnalysis.getJointMetric4Label();
                result.jointMetric4Value = formAnalysis.getJointMetric4Value();

                postureResults.put(maxConfidenceClass, result);
            }

            if (lastActiveExerciseClass != null && !lastActiveExerciseClass.equalsIgnoreCase(maxConfidenceClass)) {
                if (postureResults.containsKey(lastActiveExerciseClass)) {
                    PostureResult activeResult = postureResults.get(lastActiveExerciseClass);
                    if (activeResult != null) {
                        activeResult.formScore = formAnalysis.getFormScore();
                        activeResult.riskScore = formAnalysis.getRiskScore();
                        activeResult.riskLevel = formAnalysis.getRiskLevel();
                        activeResult.warning = formAnalysis.getWarning();

                        activeResult.jointMetric1Label = formAnalysis.getJointMetric1Label();
                        activeResult.jointMetric1Value = formAnalysis.getJointMetric1Value();
                        activeResult.jointMetric2Label = formAnalysis.getJointMetric2Label();
                        activeResult.jointMetric2Value = formAnalysis.getJointMetric2Value();
                        activeResult.jointMetric3Label = formAnalysis.getJointMetric3Label();
                        activeResult.jointMetric3Value = formAnalysis.getJointMetric3Value();
                        activeResult.jointMetric4Label = formAnalysis.getJointMetric4Label();
                        activeResult.jointMetric4Value = formAnalysis.getJointMetric4Value();
                    }
                } else {
                    PostureResult activeResult = new PostureResult(0, 0f, lastActiveExerciseClass);
                    activeResult.formScore = formAnalysis.getFormScore();
                    activeResult.riskScore = formAnalysis.getRiskScore();
                    activeResult.riskLevel = formAnalysis.getRiskLevel();
                    activeResult.warning = formAnalysis.getWarning();

                    activeResult.jointMetric1Label = formAnalysis.getJointMetric1Label();
                    activeResult.jointMetric1Value = formAnalysis.getJointMetric1Value();
                    activeResult.jointMetric2Label = formAnalysis.getJointMetric2Label();
                    activeResult.jointMetric2Value = formAnalysis.getJointMetric2Value();
                    activeResult.jointMetric3Label = formAnalysis.getJointMetric3Label();
                    activeResult.jointMetric3Value = formAnalysis.getJointMetric3Value();
                    activeResult.jointMetric4Label = formAnalysis.getJointMetric4Label();
                    activeResult.jointMetric4Value = formAnalysis.getJointMetric4Value();

                    postureResults.put(lastActiveExerciseClass, activeResult);
                }
            }
        }

        return new HashMap<>(postureResults);
    }
}
