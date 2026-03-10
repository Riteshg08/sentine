package com.example.sentineiai

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

data class RiskResult(
    val score: Int,
    val level: RiskLevel,
    val triggers: List<String>
)

class RiskScoringEngine(
    private val uploadSpikeThreshold: Long = 1024 * 1024, // 1MB for "spike"
    private val highUploadThreshold: Long = 10 * 1024 * 1024 // 10MB for "high"
) {

    /**
     * Evaluates the security risk based on behavioral signals.
     * 
     * @param signals The collected behavioral data
     * @return A RiskResult containing the score, level, and identified risk triggers
     */
    fun evaluateRisk(signals: BehaviorSignals): RiskResult {
        var score = 0
        val triggers = mutableListOf<String>()

        // 1. If screen is OFF and network upload is significant -> Potential exfiltration
        // Using a lower threshold for "spike" when screen is off as background activity should be minimal.
        if (!signals.screenOn && signals.uploadBytes > uploadSpikeThreshold) {
            score += 40
            triggers.add("Significant background upload while screen is OFF")
        }

        // 2. Check for background apps with sensitive permissions
        // If an app has Microphone permission and is NOT in the foreground, it's a privacy risk.
        val backgroundMicApps = signals.appsWithMicPermission.filter { it != signals.foregroundAppPackage }
        if (backgroundMicApps.isNotEmpty()) {
            score += 30
            triggers.add("Background apps have Microphone access: ${backgroundMicApps.size} apps")
        }
        
        // Additional check for Camera (implied by input signals requirements)
        val backgroundCameraApps = signals.appsWithCameraPermission.filter { it != signals.foregroundAppPackage }
        if (backgroundCameraApps.isNotEmpty()) {
            score += 20
            triggers.add("Background apps have Camera access: ${backgroundCameraApps.size} apps")
        }

        // 3. General high upload traffic threshold
        if (signals.uploadBytes > highUploadThreshold) {
            score += 20
            triggers.add("Upload traffic exceeds high threshold (>10MB)")
        }

        // Clamp score to 100
        val finalScore = score.coerceAtMost(100)

        val level = when {
            finalScore >= 60 -> RiskLevel.HIGH
            finalScore >= 30 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return RiskResult(finalScore, level, triggers)
    }
}
