package com.example.crashdetection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.crashdetection.ui.theme.CrashDetectionTheme

class MainActivity : ComponentActivity() {

    private var message by mutableStateOf("Monitoring for crashes...")
    private var countdown by mutableIntStateOf(-1)
    private var isConfirmed by mutableStateOf(false)
    private val simulationReceiver = SimulationReceiver()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CrashMonitorService.ACTION_CRASH_UPDATE) {
                message = intent.getStringExtra(CrashMonitorService.EXTRA_MESSAGE) ?: ""
                countdown = intent.getIntExtra(CrashMonitorService.EXTRA_COUNTDOWN, -1)
                isConfirmed = intent.getBooleanExtra(CrashMonitorService.EXTRA_IS_CONFIRMED, false)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        
        if (smsGranted && locationGranted) {
            startCrashService()
            checkBackgroundLocationPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = AudioManager.STREAM_ALARM
        ContactStore.save(this, "9562153025")
        FirebaseLogger.init(this)
        ContextCompat.registerReceiver(
            this,
            simulationReceiver,
            IntentFilter(SimulationReceiver.ACTION_SIMULATE_CRASH),
            ContextCompat.RECEIVER_EXPORTED
        )

        checkAndRequestPermissions()
        enableEdgeToEdge()

        setContent {
            CrashDetectionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CrashScreen(
                        message = message,
                        countdown = countdown,
                        isConfirmed = isConfirmed,
                        onCancel = {
                            val intent = Intent(this, CrashMonitorService::class.java).apply {
                                action = CrashMonitorService.ACTION_CANCEL_COUNTDOWN
                            }
                            startService(intent)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startCrashService()
        } else {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    private fun startCrashService() {
        val serviceIntent = Intent(this, CrashMonitorService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(CrashMonitorService.ACTION_CRASH_UPDATE)
        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(simulationReceiver)
    }
}

@Composable
fun CrashScreen(
    message: String,
    countdown: Int,
    isConfirmed: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isConfirmed -> Color(0xFFB71C1C) // Deep Red
            countdown > 0 -> Color(0xFFFF8F00) // Vibrant Orange
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(durationMillis = 500), label = ""
    )

    val contentColor = if (isConfirmed || countdown > 0) Color.White else MaterialTheme.colorScheme.onBackground

    Surface(
        modifier = modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusIcon(countdown, isConfirmed)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = when {
                    isConfirmed -> "CRASH CONFIRMED"
                    countdown > 0 -> "POSSIBLE CRASH"
                    else -> "System Active"
                },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = contentColor.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = countdown > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "$countdown",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(56.dp).fillMaxWidth()
                    ) {
                        Text("I'M SAFE (CANCEL)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIcon(countdown: Int, isConfirmed: Boolean) {
    val icon: ImageVector = when {
        isConfirmed -> Icons.Default.Warning
        countdown > 0 -> Icons.Default.Notifications
        else -> Icons.Default.Info
    }
    
    val iconColor = when {
        isConfirmed -> Color.White
        countdown > 0 -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(if (countdown <= 0 && !isConfirmed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = iconColor
        )
    }
}
