package com.example.poseexercise.posedetector.classification;

import com.google.mlkit.vision.common.PointF3D;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class FitSenseFormAnalyzer {

    public static class FormAnalysis {
        private final String exercise;
        private final int formScore;
        private final int riskScore;
        private final String riskLevel;
        private final String warning;

        private final String jointMetric1Label;
        private final String jointMetric1Value;
        private final String jointMetric2Label;
        private final String jointMetric2Value;
        private final String jointMetric3Label;
        private final String jointMetric3Value;
        private final String jointMetric4Label;
        private final String jointMetric4Value;

        public FormAnalysis(
                String exercise,
                int formScore,
                int riskScore,
                String riskLevel,
                String warning,
                String m1Label, String m1Val,
                String m2Label, String m2Val,
                String m3Label, String m3Val,
                String m4Label, String m4Val) {
            this.exercise = exercise;
            this.formScore = formScore;
            this.riskScore = riskScore;
            this.riskLevel = riskLevel;
            this.warning = warning;
            this.jointMetric1Label = m1Label != null ? m1Label : "";
            this.jointMetric1Value = m1Val != null ? m1Val : "";
            this.jointMetric2Label = m2Label != null ? m2Label : "";
            this.jointMetric2Value = m2Val != null ? m2Val : "";
            this.jointMetric3Label = m3Label != null ? m3Label : "";
            this.jointMetric3Value = m3Val != null ? m3Val : "";
            this.jointMetric4Label = m4Label != null ? m4Label : "";
            this.jointMetric4Value = m4Val != null ? m4Val : "";
        }

        public FormAnalysis(
                String exercise,
                int formScore,
                int riskScore,
                String riskLevel,
                String warning) {
            this(exercise, formScore, riskScore, riskLevel, warning,
                 "", "", "", "", "", "", "", "");
        }

        public String getExercise() { return exercise; }
        public int getFormScore() { return formScore; }
        public int getRiskScore() { return riskScore; }
        public String getRiskLevel() { return riskLevel; }
        public String getWarning() { return warning; }

        public String getJointMetric1Label() { return jointMetric1Label; }
        public String getJointMetric1Value() { return jointMetric1Value; }
        public String getJointMetric2Label() { return jointMetric2Label; }
        public String getJointMetric2Value() { return jointMetric2Value; }
        public String getJointMetric3Label() { return jointMetric3Label; }
        public String getJointMetric3Value() { return jointMetric3Value; }
        public String getJointMetric4Label() { return jointMetric4Label; }
        public String getJointMetric4Value() { return jointMetric4Value; }
    }

    public FormAnalysis analyze(Pose pose, String exercise) {
        if (pose == null || pose.getAllPoseLandmarks().isEmpty()) {
            return new FormAnalysis(
                    exercise != null ? exercise : "Exercise",
                    0, 100, "HIGH", "Pose not detected");
        }

        if (exercise == null) {
            exercise = "squats";
        }

        String exLower = exercise.toLowerCase();

        if (exLower.contains("squat")) {
            return analyzeSquat(pose);
        }

        if (exLower.contains("push")) {
            return analyzePushup(pose);
        }

        if (exLower.contains("dead")) {
            return analyzeDeadlift(pose);
        }

        if (exLower.contains("lunge")) {
            return analyzeLunge(pose);
        }

        // Generic joint analyzer for other exercises (e.g. shoulder press, bicep curl)
        return analyzeGenericExercise(pose, exercise);
    }

    // ---------------------------------------------------------
    // SQUAT
    // ---------------------------------------------------------
    private FormAnalysis analyzeSquat(Pose pose) {
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        if (leftHip == null || leftKnee == null || leftAnkle == null ||
                rightHip == null || rightKnee == null || rightAnkle == null ||
                leftShoulder == null || rightShoulder == null) {
            return insufficientLandmarks("Squat");
        }

        // Real-Time ML Kit PoseLandmark Angles
        int leftKneeAngle = (int) Math.round(calculateAngle(
                leftHip.getPosition3D(),
                leftKnee.getPosition3D(),
                leftAnkle.getPosition3D()));

        int rightKneeAngle = (int) Math.round(calculateAngle(
                rightHip.getPosition3D(),
                rightKnee.getPosition3D(),
                rightAnkle.getPosition3D()));

        int symmetryDifference = Math.abs(leftKneeAngle - rightKneeAngle);

        int leftHipAngle = (int) Math.round(calculateAngle(
                leftShoulder.getPosition3D(),
                leftHip.getPosition3D(),
                leftKnee.getPosition3D()));

        double shoulderTilt = Math.abs(
                leftShoulder.getPosition3D().getY() - rightShoulder.getPosition3D().getY()
        );

        int risk = 0;
        String warning = "Good alignment";

        if (symmetryDifference > 15) {
            risk += 20;
            warning = "Knee asymmetry detected";
        }
        if (symmetryDifference > 30) {
            risk += 20;
            warning = "Correct leg imbalance";
        }
        if (shoulderTilt > 0.12) {
            risk += 20;
            warning = "Correct upper body lean";
        }

        double averageKneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0;
        if (averageKneeAngle > 175) {
            risk += 5;
        }

        risk = Math.min(100, Math.max(0, risk));
        int formScore = 100 - risk;
        String riskLevel = risk >= 60 ? "HIGH" : (risk >= 30 ? "MODERATE" : "LOW");

        return new FormAnalysis(
                "Squat", formScore, risk, riskLevel, warning,
                "Left Knee", leftKneeAngle + "°",
                "Right Knee", rightKneeAngle + "°",
                "Symmetry", symmetryDifference + "°",
                "Hip Angle", leftHipAngle + "°");
    }

    // ---------------------------------------------------------
    // PUSH-UP
    // ---------------------------------------------------------
    private FormAnalysis analyzePushup(Pose pose) {
        PoseLandmark shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
        PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        PoseLandmark hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        if (shoulder == null || elbow == null || wrist == null || hip == null || ankle == null) {
            return insufficientLandmarks("Push-up");
        }

        int elbowAngle = (int) Math.round(calculateAngle(
                shoulder.getPosition3D(),
                elbow.getPosition3D(),
                wrist.getPosition3D()));

        int bodyAngle = (int) Math.round(calculateAngle(
                shoulder.getPosition3D(),
                hip.getPosition3D(),
                ankle.getPosition3D()));

        int risk = 0;
        String warning = "Good alignment";

        if (elbowAngle > 165) {
            risk += 15;
            warning = "Lower with controlled elbow bend";
        }
        if (bodyAngle < 150) {
            risk += 30;
            warning = "Correct body alignment";
        }

        risk = Math.min(100, Math.max(0, risk));
        int formScore = 100 - risk;
        String riskLevel = risk >= 60 ? "HIGH" : (risk >= 30 ? "MODERATE" : "LOW");

        return new FormAnalysis(
                "Push-up", formScore, risk, riskLevel, warning,
                "Elbow Angle", elbowAngle + "°",
                "Body Alignment", bodyAngle + "°",
                "", "",
                "", "");
    }

    // ---------------------------------------------------------
    // DEADLIFT
    // ---------------------------------------------------------
    private FormAnalysis analyzeDeadlift(Pose pose) {
        PoseLandmark shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        if (shoulder == null || hip == null || knee == null || ankle == null) {
            return insufficientLandmarks("Deadlift");
        }

        int hipAngle = (int) Math.round(calculateAngle(
                shoulder.getPosition3D(),
                hip.getPosition3D(),
                knee.getPosition3D()));

        int kneeAngle = (int) Math.round(calculateAngle(
                hip.getPosition3D(),
                knee.getPosition3D(),
                ankle.getPosition3D()));

        int risk = 0;
        String warning = "Good alignment";

        if (hipAngle < 45) {
            risk += 30;
            warning = "Maintain controlled hip hinge";
        }
        if (kneeAngle < 70) {
            risk += 20;
            warning = "Keep movement as hip hinge";
        }

        risk = Math.min(100, Math.max(0, risk));
        int formScore = 100 - risk;
        String riskLevel = risk >= 60 ? "HIGH" : (risk >= 30 ? "MODERATE" : "LOW");

        return new FormAnalysis(
                "Deadlift", formScore, risk, riskLevel, warning,
                "Hip Hinge", hipAngle + "°",
                "Knee Angle", kneeAngle + "°",
                "", "",
                "", "");
    }

    // ---------------------------------------------------------
    // LUNGE
    // ---------------------------------------------------------
    private FormAnalysis analyzeLunge(Pose pose) {
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        PoseLandmark rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);
        PoseLandmark rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE);
        PoseLandmark rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);

        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);

        if (leftHip == null || leftKnee == null || leftAnkle == null ||
                rightHip == null || rightKnee == null || rightAnkle == null ||
                leftShoulder == null || rightShoulder == null) {
            return insufficientLandmarks("Lunge");
        }

        int leftKneeAngle = (int) Math.round(calculateAngle(
                leftHip.getPosition3D(),
                leftKnee.getPosition3D(),
                leftAnkle.getPosition3D()));

        int rightKneeAngle = (int) Math.round(calculateAngle(
                rightHip.getPosition3D(),
                rightKnee.getPosition3D(),
                rightAnkle.getPosition3D()));

        int symmetryDifference = Math.abs(leftKneeAngle - rightKneeAngle);

        int leftHipAngle = (int) Math.round(calculateAngle(
                leftShoulder.getPosition3D(),
                leftHip.getPosition3D(),
                leftKnee.getPosition3D()));

        int risk = 0;
        String warning = "Good alignment";

        if (leftKneeAngle > 165 && rightKneeAngle > 165) {
            warning = "Step into lunge position";
        } else if (symmetryDifference < 15) {
            risk += 15;
            warning = "Bend front leg to 90°";
        }

        if (leftHipAngle < 130) {
            risk += 20;
            warning = "Keep torso upright";
        }

        risk = Math.min(100, Math.max(0, risk));
        int formScore = 100 - risk;
        String riskLevel = risk >= 60 ? "HIGH" : (risk >= 30 ? "MODERATE" : "LOW");

        return new FormAnalysis(
                "Lunge", formScore, risk, riskLevel, warning,
                "Left Knee", leftKneeAngle + "°",
                "Right Knee", rightKneeAngle + "°",
                "Symmetry", symmetryDifference + "°",
                "Hip Angle", leftHipAngle + "°");
    }

    // ---------------------------------------------------------
    // GENERIC EXERCISE FALLBACK
    // ---------------------------------------------------------
    private FormAnalysis analyzeGenericExercise(Pose pose, String exercise) {
        PoseLandmark shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark elbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
        PoseLandmark wrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        PoseLandmark hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        String label1 = ""; String val1 = "";
        String label2 = ""; String val2 = "";

        if (shoulder != null && elbow != null && wrist != null) {
            int elbowAngle = (int) Math.round(calculateAngle(
                    shoulder.getPosition3D(), elbow.getPosition3D(), wrist.getPosition3D()));
            label1 = "Elbow Angle";
            val1 = elbowAngle + "°";
        }

        if (hip != null && knee != null && ankle != null) {
            int kneeAngle = (int) Math.round(calculateAngle(
                    hip.getPosition3D(), knee.getPosition3D(), ankle.getPosition3D()));
            label2 = "Knee Angle";
            val2 = kneeAngle + "°";
        }

        return new FormAnalysis(
                exercise, 100, 0, "LOW", "Good alignment",
                label1, val1,
                label2, val2,
                "", "",
                "", "");
    }

    private FormAnalysis insufficientLandmarks(String exercise) {
        return new FormAnalysis(
                exercise, 0, 100, "HIGH", "Insufficient pose landmarks",
                "Status", "--", "", "", "", "", "", "");
    }

    private double calculateAngle(PointF3D a, PointF3D b, PointF3D c) {
        double abX = a.getX() - b.getX();
        double abY = a.getY() - b.getY();
        double cbX = c.getX() - b.getX();
        double cbY = c.getY() - b.getY();

        double dotProduct = (abX * cbX) + (abY * cbY);
        double magnitudeAB = Math.sqrt((abX * abX) + (abY * abY));
        double magnitudeCB = Math.sqrt((cbX * cbX) + (cbY * cbY));

        if (magnitudeAB == 0 || magnitudeCB == 0) {
            return 180;
        }

        double cosine = dotProduct / (magnitudeAB * magnitudeCB);
        cosine = Math.max(-1.0, Math.min(1.0, cosine));

        return Math.toDegrees(Math.acos(cosine));
    }
}