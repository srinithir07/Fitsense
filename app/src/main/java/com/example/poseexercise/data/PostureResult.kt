package com.example.poseexercise.data

/**
 * Data class representing the result of a posture detection
 * together with FitSense form and risk analysis.
 */
data class PostureResult @JvmOverloads constructor(
    var repetition: Int = 0,
    var confidence: Float = 0f,
    val postureType: String = "",

    @JvmField var formScore: Int = 0,
    @JvmField var riskScore: Int = 0,
    @JvmField var riskLevel: String = "",
    @JvmField var warning: String = "",

    @JvmField var jointMetric1Label: String = "",
    @JvmField var jointMetric1Value: String = "",
    @JvmField var jointMetric2Label: String = "",
    @JvmField var jointMetric2Value: String = "",
    @JvmField var jointMetric3Label: String = "",
    @JvmField var jointMetric3Value: String = "",
    @JvmField var jointMetric4Label: String = "",
    @JvmField var jointMetric4Value: String = ""
)




