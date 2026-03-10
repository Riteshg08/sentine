package com.example.sentineiai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sentineiai.ui.theme.SentineIAITheme
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Monitor : Screen("monitor", "Monitor", Icons.Default.Info)
    object Alerts : Screen("alerts", "Alerts", Icons.Default.Notifications)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SentineIAITheme(darkTheme = true) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val collector = remember { BehaviorSignalCollector(context) }
    val engine = remember { RiskScoringEngine() }
    val dbHelper = remember { SentinelDatabaseHelper(context) }

    var signals by remember { mutableStateOf<BehaviorSignals?>(null) }
    var riskResult by remember { mutableStateOf<RiskResult?>(null) }
    var hasUsagePermission by remember { mutableStateOf(collector.isUsageStatsPermissionGranted()) }

    fun processSignals(collected: BehaviorSignals) {
        val result = engine.evaluateRisk(collected)
        signals = collected
        riskResult = result

        dbHelper.insertLog(BehaviorLogEntry(
            timestamp = System.currentTimeMillis(),
            foregroundApp = collected.foregroundAppPackage ?: "None",
            uploadBytes = collected.uploadBytes,
            screenState = if (collected.screenOn) "ON" else "OFF",
            riskScore = result.score,
            riskLevel = result.level.name
        ))
    }

    fun refreshAudit() {
        hasUsagePermission = collector.isUsageStatsPermissionGranted()
        processSignals(collector.getBehaviorSignals())
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(Screen.Dashboard, Screen.Monitor, Screen.Alerts)
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    riskResult = riskResult,
                    hasPermission = hasUsagePermission,
                    onRequestPermission = { collector.requestUsageStatsPermission() },
                    onRefresh = { refreshAudit() },
                    onViewAlerts = { navController.navigate(Screen.Alerts.route) }
                )
            }
            composable(Screen.Monitor.route) {
                MonitorScreen(signals = signals)
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(dbHelper = dbHelper)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    riskResult: RiskResult?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onViewAlerts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SentinelAI - Privacy Monitor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        RiskScoreCard(riskResult)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "LIVE SIGNALS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        LiveSignalsGrid(riskResult)

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onRefresh,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Now")
            }
            
            OutlinedButton(
                onClick = onViewAlerts,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Alerts")
            }
        }

        if (!hasPermission) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usage Permission required for full scan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRequestPermission) {
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RiskScoreCard(riskResult: RiskResult?) {
    val score = riskResult?.score ?: 0
    val level = riskResult?.level?.name ?: "UNKNOWN"
    val color = when (riskResult?.level) {
        RiskLevel.HIGH -> Color(0xFFEF5350)
        RiskLevel.MEDIUM -> Color(0xFFFFB74D)
        RiskLevel.LOW -> Color(0xFF66BB6A)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient for aesthetic
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
            )
            
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "RISK SCORE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.8f)
                )
                Text(
                    text = score.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = "$level RISK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
        }
    }
}

@Composable
fun LiveSignalsGrid(riskResult: RiskResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalCard(Modifier.weight(1f), "Foreground App", "Instagram", Icons.Default.Info)
            SignalCard(Modifier.weight(1f), "Upload Rate", "1.2 MB/s", Icons.Default.Info)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalCard(Modifier.weight(1f), "Screen State", "OFF", Icons.Default.Info)
            SignalCard(Modifier.weight(1f), "Mic Permission", "YES", Icons.Default.Info)
        }
    }
}

@Composable
fun SignalCard(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label, 
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value, 
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MonitorScreen(signals: BehaviorSignals?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Activity Monitor", 
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (signals != null) {
            ActivityCard("Foreground App", signals.foregroundAppPackage ?: "None/Background", Icons.Default.Home)
            ActivityCard("Network Traffic", "${signals.uploadBytes / 1024} KB Total Upload", Icons.Default.Info)
            ActivityCard("Screen State", if (signals.screenOn) "Interactive (ON)" else "Idle (OFF)", Icons.Default.Info)
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Permissions Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            PermissionAuditCard("Microphone Access", signals.appsWithMicPermission)
            PermissionAuditCard("Camera Access", signals.appsWithCameraPermission)
            
        } else {
            Box(Modifier.fillMaxSize().height(400.dp), contentAlignment = Alignment.Center) {
                Text("Run an audit on the dashboard to see live signals.", color = Color.Gray)
            }
        }
    }
}

@Composable
fun PermissionAuditCard(label: String, apps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, fontWeight = FontWeight.Bold)
                Text("${apps.size} apps", color = if (apps.isNotEmpty()) Color(0xFFEF5350) else Color(0xFF66BB6A))
            }
            if (apps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = apps.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AlertsScreen(dbHelper: SentinelDatabaseHelper) {
    var logs by remember { mutableStateOf(dbHelper.getRecentLogs(20)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "Security Alerts", 
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No incidents logged.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs) { log ->
                    AlertItem(log)
                }
            }
        }
    }
}

@Composable
fun AlertItem(log: BehaviorLogEntry) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    val isHighRisk = log.riskLevel == "HIGH"
    val color = when (log.riskLevel) {
        "HIGH" -> Color(0xFFEF5350)
        "MEDIUM" -> Color(0xFFFFB74D)
        else -> Color(0xFF66BB6A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighRisk) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isHighRisk) CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(color, color.copy(alpha = 0.5f)))) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = color)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = log.foregroundApp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    text = "Background Activity Detected", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = sdf.format(Date(log.timestamp)), 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = log.riskScore.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = log.riskLevel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
fun ActivityCard(label: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    label, 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    value, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RiskSummaryCard(risk: RiskResult) {
    val color = when (risk.level) {
        RiskLevel.HIGH -> Color(0xFFEF5350)
        RiskLevel.MEDIUM -> Color(0xFFFFB74D)
        RiskLevel.LOW -> Color(0xFF66BB6A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Risk Level: ${risk.level}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                Text(text = "${risk.score}/100", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
            }
            
            if (risk.triggers.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = color.copy(alpha = 0.3f))
                Text(text = "Risk Triggers:", style = MaterialTheme.typography.labelLarge)
                risk.triggers.forEach { trigger ->
                    Text(text = "• $trigger", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
