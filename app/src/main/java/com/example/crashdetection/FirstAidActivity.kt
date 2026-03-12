package com.example.crashdetection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.crashdetection.ui.theme.CrashDetectionTheme

class FirstAidActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val severity = intent.getStringExtra(CrashMonitorService.EXTRA_SEVERITY) ?: "UNKNOWN"

        setContent {
            CrashDetectionTheme {
                FirstAidScreen(severity = severity)
            }
        }
    }
}

@Composable
private fun FirstAidScreen(severity: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("First Aid Guidance ($severity)") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GuidanceCard(
                title = "CPR",
                steps = listOf(
                    "Check responsiveness and breathing.",
                    "Call emergency services immediately.",
                    "Begin chest compressions: 100-120/min, depth 5-6 cm.",
                    "If trained, provide 30 compressions and 2 rescue breaths."
                )
            )
            GuidanceCard(
                title = "Bleeding Control",
                steps = listOf(
                    "Apply direct pressure using clean cloth or gauze.",
                    "Elevate the injured area if possible.",
                    "Do not remove deeply embedded objects.",
                    "Maintain pressure until help arrives."
                )
            )
            GuidanceCard(
                title = "Unconscious Victim Response",
                steps = listOf(
                    "Ensure scene safety before approaching.",
                    "Check airway and breathing.",
                    "Place victim in recovery position if breathing.",
                    "Monitor continuously until responders arrive."
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuidanceCard(title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            steps.forEachIndexed { index, step ->
                Text(text = "${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
