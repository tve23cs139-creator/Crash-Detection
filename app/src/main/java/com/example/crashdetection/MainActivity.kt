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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

        checkAndRequestPermissions()
        enableEdgeToEdge()

        setContent {
            CrashDetectionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CrashScreen(
                        message = message,
                        countdown = countdown,
                        isConfirmed = isConfirmed,
                        contacts = ContactStore.getAll(this),
                        onAddContact = { ContactStore.add(this, it) },
                        onRemoveContact = { ContactStore.remove(this, it) },
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
            != PackageManager.PERMISSION_GRANTED
        ) {
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
}

@Composable
fun CrashScreen(
    message: String,
    countdown: Int,
    isConfirmed: Boolean,
    contacts: List<String>,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newContact by remember { mutableStateOf("") }
    val contactList = remember(contacts) { mutableStateListOf<String>().apply { addAll(contacts) } }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isConfirmed -> Color(0xFFB71C1C)
            countdown > 0 -> Color(0xFFFF8F00)
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
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            StatusIcon(countdown, isConfirmed)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = when {
                    isConfirmed -> "CRASH CONFIRMED"
                    countdown > 0 -> "POSSIBLE CRASH"
                    else -> "System Active"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = contentColor.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            EmergencyContactManager(
                contacts = contactList,
                contentColor = contentColor,
                onAdd = {
                    onAddContact(it)
                    contactList.clear(); contactList.addAll(contacts + it)
                },
                onRemove = {
                    onRemoveContact(it)
                    contactList.remove(it)
                },
                newContact = newContact,
                onContactChanged = { newContact = it }
            )

            AnimatedVisibility(
                visible = countdown > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "$countdown",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth()
                    ) {
                        Text("I'M SAFE (CANCEL)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyContactManager(
    contacts: List<String>,
    contentColor: Color,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    newContact: String,
    onContactChanged: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = contentColor.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Emergency Contacts", color = contentColor, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newContact,
                    onValueChange = onContactChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Phone") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(0.dp).padding(4.dp))
                Button(onClick = {
                    onAdd(newContact)
                    onContactChanged("")
                }) {
                    Text("Add")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(contacts) { number ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(number, color = contentColor)
                        IconButton(onClick = { onRemove(number) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = contentColor)
                        }
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
