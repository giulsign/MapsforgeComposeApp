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
import androidx.compose.ui.res.stringResource
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

    // USE SINGLETON
    val locationService = remember { FirebaseLocationService.getInstance(context) }

    var currentRole by remember { mutableStateOf(SessionRole.NONE) }
    var sessionCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("MyDevice") }
    var joinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // SYNC WITH ACTIVE SERVICE
    LaunchedEffect(Unit) {
        currentRole = locationService.getCurrentRole()
        sessionCode = locationService.getCurrentSessionId() ?: ""
    }

    // READ ID
    val deviceId = locationService.getDeviceId()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_gps_sharing), fontFamily = MyCustomFont) },
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
            // DEVICE INFO
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📱"+stringResource(R.string.your_device),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("ID: ${deviceId.take(8)}...", fontSize = 12.sp, color = Color.Gray)

                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text(stringResource(R.string.device_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // CREATE SESSION (HOST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🎯"+ stringResource(R.string.create_session) + "(" + stringResource(R.string.host) + ")",
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
                                    Text("✅"+ stringResource(R.string.session_active)+" :", fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.session_code)+": $sessionCode", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.share_this)+": $deviceName", fontSize = 12.sp, color = Color.Gray)

                                    // DEBUG INFO
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.role)+": $currentRole",
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
                                        Toast.makeText(context, context.getString(R.string.session_closed), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.leave_session))
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
                                                Toast.makeText(context, context.getString(R.string.session_created_with_code)+" : $sessionCode", Toast.LENGTH_LONG).show()
                                            }
                                            is FirebaseResult.Error -> {
                                                Toast.makeText(context, context.getString(R.string.error)+": ${result.message}", Toast.LENGTH_LONG).show()
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
                                Text(stringResource(R.string.create_session))
                            }
                        }
                    }
                }
            }

            // JOIN SESSION (GUEST)
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔗"+ stringResource(R.string.join_session) + "(" + stringResource(R.string.guest) + ")",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = MyCustomFont
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it },
                            label = { Text(stringResource(R.string.session_code)) },
                            placeholder = { Text(stringResource(R.string.enter_code)) },
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
                                            Toast.makeText(context, context.getString(R.string.join_session)+" $joinCode!", Toast.LENGTH_SHORT).show()
                                        }
                                        is FirebaseResult.Error -> {
                                            Toast.makeText(context, context.getString(R.string.error)+": ${result.message}", Toast.LENGTH_LONG).show()
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
                            Text(stringResource(R.string.join_session))
                        }
                    }
                }
            }

            // INFO
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF9C4)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️"+ stringResource(R.string.how_it_works), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "•"+ stringResource(R.string.host_creates_group)+"\n" +
                                    "•"+ stringResource(R.string.guest_enter_code)+"\n" +
                                    "•"+ stringResource(R.string.all_guest)+"\n" +
                                    "•"+ stringResource(R.string.max_5)+"\n" +
                                    "•"+ stringResource(R.string.connection_persist)+"\n",
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
}
