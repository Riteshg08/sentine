package com.example.sentineiai

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

data class BehaviorSignals(
    val foregroundAppPackage: String?,
    val uploadBytes: Long,
    val screenOn: Boolean,
    val appsWithMicPermission: List<String>,
    val appsWithCameraPermission: List<String>,
    val errors: List<String> = emptyList()
)

class BehaviorSignalCollector(private val context: Context) {

    /**
     * Collects behavioral signals from the device.
     * Note: Requires user to grant Usage Access permission for foreground app tracking.
     */
    fun getBehaviorSignals(): BehaviorSignals {
        val errors = mutableListOf<String>()

        val foregroundApp = try {
            getForegroundApp()
        } catch (e: Exception) {
            errors.add("ForegroundApp: ${e.message}")
            null
        }

        val uploadBytes = try {
            TrafficStats.getTotalTxBytes()
        } catch (e: Exception) {
            errors.add("TrafficStats: ${e.message}")
            -1L
        }

        val screenOn = try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isInteractive
        } catch (e: Exception) {
            errors.add("PowerManager: ${e.message}")
            false
        }

        val (micApps, cameraApps) = try {
            getAppsWithPermissions()
        } catch (e: Exception) {
            errors.add("PackageManager: ${e.message}")
            Pair(emptyList<String>(), emptyList<String>())
        }

        return BehaviorSignals(
            foregroundAppPackage = foregroundApp,
            uploadBytes = uploadBytes,
            screenOn = screenOn,
            appsWithMicPermission = micApps,
            appsWithCameraPermission = cameraApps,
            errors = errors
        )
    }

    private fun getForegroundApp(): String? {
        if (!isUsageStatsPermissionGranted()) {
            throw SecurityException("Usage Access permission not granted")
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Look back 1 minute

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun getAppsWithPermissions(): Pair<List<String>, List<String>> {
        val micApps = mutableListOf<String>()
        val cameraApps = mutableListOf<String>()

        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        for (pkg in packages) {
            val permissions = pkg.requestedPermissions ?: continue
            val flags = pkg.requestedPermissionsFlags ?: continue

            for (i in permissions.indices) {
                // Check if permission is granted
                if ((flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    when (permissions[i]) {
                        android.Manifest.permission.RECORD_AUDIO -> micApps.add(pkg.packageName)
                        android.Manifest.permission.CAMERA -> cameraApps.add(pkg.packageName)
                    }
                }
            }
        }

        return Pair(micApps, cameraApps)
    }

    fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
