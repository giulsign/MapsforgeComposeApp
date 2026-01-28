/*package it.fourSTL.PositionMarker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import it.fourSTL.PositionMarker.firebase.*
import it.fourSTL.PositionMarker.ui.theme.MyCustomFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSharingScreen(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val locationService = remember { FirebaseLocationService(context) }

    var currentRole by remember { mutableStateOf(SessionRole.NONE) }
    var sessionCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("My Device") }
    var joinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Ottieni device ID
    val deviceId = locationService.getDeviceId()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group GPS Sharing", fontFamily = MyCustomFont) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Info
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📱 Your Device",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("ID: ${deviceId.take(8)}...", fontSize = 12.sp, color = Color.Gray)

                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Device Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Create Session (HOST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🎯 Create Session (Host)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))

                        if (sessionCode.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("✅ Session Active", fontWeight = FontWeight.Bold)
                                    Text("Code: $sessionCode", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Share this code with guests", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        locationService.leaveSession()
                                        sessionCode = ""
                                        currentRole = SessionRole.NONE
                                        Toast.makeText(context, "Session closed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Close Session")
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        when (val result = locationService.createSession(deviceName)) {
                                            is FirebaseResult.Success -> {
                                                sessionCode = result.data
                                                currentRole = SessionRole.HOST
                                                Toast.makeText(context, "Session created!", Toast.LENGTH_SHORT).show()
                                            }
                                            is FirebaseResult.Error -> {
                                                Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                                            }
                                            else -> {}
                                        }
                                        isLoading = false
                                    }
                                },
                                enabled = !isLoading && deviceName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Session")
                            }
                        }
                    }
                }
            }

            // Join Session (GUEST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔗 Join Session (Guest)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it },
                            label = { Text("Session Code") },
                            placeholder = { Text("Enter 6-digit code") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    when (val result = locationService.joinSession(joinCode, deviceName)) {
                                        is FirebaseResult.Success -> {
                                            sessionCode = joinCode
                                            currentRole = SessionRole.GUEST
                                            Toast.makeText(context, "Joined session!", Toast.LENGTH_SHORT).show()
                                        }
                                        is FirebaseResult.Error -> {
                                            Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                                        }
                                        else -> {}
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && joinCode.length == 6 && deviceName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join as Guest")
                        }
                    }
                }
            }

            // Info
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF9C4)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️ How it works", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "• Host creates a session and shares the code\n" +
                                    "• Guests enter the code to join\n" +
                                    "• All participants see each other on the map\n" +
                                    "• Max 5 guests per session",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}*/

package it.fourSTL.PositionMarker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.widget.Toast
import it.fourSTL.PositionMarker.firebase.*
import it.fourSTL.PositionMarker.ui.theme.MyCustomFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSharingScreen(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // ✅ USA IL SINGLETON invece di creare una nuova istanza
    val locationService = remember { FirebaseLocationService.getInstance(context) }

    var currentRole by remember { mutableStateOf(SessionRole.NONE) }
    var sessionCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("My Device") }
    var joinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // ✅ Sincronizza lo stato con il servizio all'avvio
    LaunchedEffect(Unit) {
        currentRole = locationService.getCurrentRole()
        sessionCode = locationService.getCurrentSessionId() ?: ""
    }

    // Ottieni device ID
    val deviceId = locationService.getDeviceId()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group GPS Sharing", fontFamily = MyCustomFont) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Info
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📱 Your Device",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("ID: ${deviceId.take(8)}...", fontSize = 12.sp, color = Color.Gray)

                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Device Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Create Session (HOST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🎯 Create Session (Host)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))

                        if (sessionCode.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("✅ Session Active", fontWeight = FontWeight.Bold)
                                    Text("Code: $sessionCode", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Share this code with guests", fontSize = 12.sp, color = Color.Gray)

                                    // ✅ Debug info
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Role: $currentRole",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        fontFamily = MyCustomFont
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        locationService.leaveSession()
                                        sessionCode = ""
                                        currentRole = SessionRole.NONE
                                        Toast.makeText(context, "Session closed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Close Session")
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        when (val result = locationService.createSession(deviceName)) {
                                            is FirebaseResult.Success -> {
                                                sessionCode = result.data
                                                currentRole = SessionRole.HOST
                                                Toast.makeText(context, "Session created! Code: $sessionCode", Toast.LENGTH_LONG).show()
                                            }
                                            is FirebaseResult.Error -> {
                                                Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                                            }
                                            else -> {}
                                        }
                                        isLoading = false
                                    }
                                },
                                enabled = !isLoading && deviceName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Session")
                            }
                        }
                    }
                }
            }

            // Join Session (GUEST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔗 Join Session (Guest)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it },
                            label = { Text("Session Code") },
                            placeholder = { Text("Enter 6-digit code") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    when (val result = locationService.joinSession(joinCode, deviceName)) {
                                        is FirebaseResult.Success -> {
                                            sessionCode = joinCode
                                            currentRole = SessionRole.GUEST
                                            Toast.makeText(context, "Joined session $joinCode!", Toast.LENGTH_SHORT).show()
                                        }
                                        is FirebaseResult.Error -> {
                                            Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                                        }
                                        else -> {}
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && joinCode.length == 6 && deviceName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join as Guest")
                        }
                    }
                }
            }

            // Info
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF9C4)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️ How it works", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "• Host creates a session and shares the code\n" +
                                    "• Guests enter the code to join\n" +
                                    "• All participants see each other on the map\n" +
                                    "• Max 5 guests per session\n" +
                                    "• Connection persists when you return to the map",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ✅ Debug Card (opzionale - rimuovila in produzione)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🐛 Debug Info", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            locationService.getDebugInfo(),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
