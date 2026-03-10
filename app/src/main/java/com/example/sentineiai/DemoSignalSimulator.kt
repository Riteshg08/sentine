package com.example.sentineiai

/**
 * Simulator for generating behavioral signals to demonstrate security risk detection.
 * Used for hackathon demos to show how the RiskScoringEngine reacts to different scenarios.
 */
object DemoSignalSimulator {

    /**
     * Scenario: Normal usage.
     * Screen is ON, low network activity, no suspicious background permissions.
     */
    fun getNormalSignal(): BehaviorSignals {
        return BehaviorSignals(
            foregroundAppPackage = "com.android.chrome",
            uploadBytes = 50 * 1024, // 50 KB
            screenOn = true,
            appsWithMicPermission = listOf("com.android.chrome"), // Foreground app has permission
            appsWithCameraPermission = emptyList()
        )
    }

    /**
     * Scenario: Privacy Risk - Background Microphone Access.
     * A background app has microphone permission while another app is in the foreground.
     */
    fun getMicRiskSignal(): BehaviorSignals {
        return BehaviorSignals(
            foregroundAppPackage = "com.example.sentineiai",
            uploadBytes = 100 * 1024,
            screenOn = true,
            appsWithMicPermission = listOf("com.suspicious.background.recorder"),
            appsWithCameraPermission = emptyList()
        )
    }

    /**
     * Scenario: Potential Data Exfiltration.
     * High upload spike occurs while the screen is OFF.
     */
    fun getBackgroundExfiltrationSignal(): BehaviorSignals {
        return BehaviorSignals(
            foregroundAppPackage = null,
            uploadBytes = 5 * 1024 * 1024, // 5 MB (Above 1MB spike threshold)
            screenOn = false,
            appsWithMicPermission = emptyList(),
            appsWithCameraPermission = emptyList()
        )
    }

    /**
     * Scenario: Critical Risk (Combination).
     * High upload (>10MB) while screen is OFF + Background app with Mic/Camera.
     */
    fun getHighRiskCombinationSignal(): BehaviorSignals {
        return BehaviorSignals(
            foregroundAppPackage = "com.android.settings",
            uploadBytes = 15 * 1024 * 1024, // 15 MB
            screenOn = false,
            appsWithMicPermission = listOf("com.spyware.hidden"),
            appsWithCameraPermission = listOf("com.spyware.hidden")
        )
    }
}
