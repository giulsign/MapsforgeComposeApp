package it.fourSTL.PositionMarker

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import it.fourSTL.PositionMarker.ui.theme.MyCustomFont
import it.fourSTL.PositionMarker.ui.theme.Purple40
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * GroupSharingUI.kt
 * Interfaccia utente Compose per gestione gruppi e condivisione posizione GPS
 */

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSharingScreen(
    context: Context,
    groupManager: GroupManager,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showGroupDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activeGroup by remember {
        derivedStateOf { groupManager.getActiveGroup() }
    }

    val allGroups by remember {
        derivedStateOf { groupManager.getAllGroups() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Group Sharing",
                        fontFamily = MyCustomFont,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xB3E0E0E0)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Active Group Card
            if (activeGroup != null) {
                ActiveGroupCard(
                    group = activeGroup!!,
                    onViewDetails = { showGroupDetails = true },
                    onLeave = { showDeleteConfirm = true }
                )
            } else {
                NoActiveGroupCard()
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RectangleShape
                ) {
                    Icon(Icons.Default.Add, "Create", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Create Group", color = Color.White, fontFamily = MyCustomFont)
                }

                Button(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    shape = RectangleShape
                ) {
                    Icon(Icons.Default.Search, "Join", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Join Group", color = Color.White, fontFamily = MyCustomFont)
                }
            }

            // All Groups List
            Text(
                "Saved Groups",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MyCustomFont,
                color = Purple40
            )

            if (allGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No groups saved yet",
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allGroups) { group ->
                        GroupListItem(
                            group = group,
                            isActive = group.id == activeGroup?.id,
                            onClick = {
                                groupManager.setActiveGroup(group)
                                Toast.makeText(context, "Activated: ${group.name}", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                groupManager.deleteGroup(group.id)
                                Toast.makeText(context, "Deleted: ${group.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateGroupDialog(
            context = context,
            groupManager = groupManager,
            onDismiss = { showCreateDialog = false }
        )
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            context = context,
            groupManager = groupManager,
            onDismiss = { showJoinDialog = false }
        )
    }

    if (showGroupDetails && activeGroup != null) {
        GroupDetailsDialog(
            context = context,
            group = activeGroup!!,
            groupManager = groupManager,
            onDismiss = { showGroupDetails = false }
        )
    }

    if (showDeleteConfirm && activeGroup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Leave Group", fontFamily = MyCustomFont) },
            text = { Text("Leave ${activeGroup!!.name}? You'll need to join again to reconnect.", fontFamily = MyCustomFont) },
            confirmButton = {
                TextButton(onClick = {
                    groupManager.leaveActiveGroup()
                    showDeleteConfirm = false
                    Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Leave", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==================== ACTIVE GROUP CARD ====================

@Composable
fun ActiveGroupCard(
    group: Group,
    onViewDetails: () -> Unit,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        ),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Active Group",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )
                    Text(
                        group.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MyCustomFont,
                        color = Purple40
                    )
                    Text(
                        if (group.isHost) "HOST" else "GUEST",
                        fontSize = 14.sp,
                        color = if (group.isHost) Color(0xFF4CAF50) else Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold,
                        fontFamily = MyCustomFont
                    )
                }

                Icon(
                    Icons.Default.Star,
                    contentDescription = "Active",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape
                ) {
                    Text("Details", fontFamily = MyCustomFont)
                }

                OutlinedButton(
                    onClick = onLeave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    ),
                    shape = RectangleShape
                ) {
                    Text("Leave", fontFamily = MyCustomFont)
                }
            }
        }
    }
}

@Composable
fun NoActiveGroupCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "No Group",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No Active Group",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                fontFamily = MyCustomFont
            )
            Text(
                "Create or join a group to start sharing",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontFamily = MyCustomFont
            )
        }
    }
}

// ==================== GROUP LIST ITEM ====================

@Composable
fun GroupListItem(
    group: Group,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFE8F5E9) else Color.White
        ),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) Color(0xFF4CAF50) else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MyCustomFont
                )
                Text(
                    "${if (group.isHost) "HOST" else "GUEST"} • ${group.members.size} members",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontFamily = MyCustomFont
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red
                )
            }
        }
    }
}

// ==================== CREATE GROUP DIALOG ====================

@Composable
fun CreateGroupDialog(
    context: Context,
    groupManager: GroupManager,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val bleSharing = remember { BluetoothGroupSharing(context, groupManager) }
    val sharingState by bleSharing.sharingState.collectAsState()

    LaunchedEffect(sharingState) {
        when (sharingState) {
            is BluetoothGroupSharing.SharingState.Success -> {
                Toast.makeText(context, "Group shared successfully!", Toast.LENGTH_SHORT).show()
                delay(1000)
                bleSharing.cleanup()
                onDismiss()
            }
            is BluetoothGroupSharing.SharingState.Error -> {
                val error = (sharingState as BluetoothGroupSharing.SharingState.Error).message
                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                isCreating = false
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { bleSharing.cleanup() }
    }

    Dialog(onDismissRequest = {
        if (!isCreating) {
            bleSharing.cleanup()
            onDismiss()
        }
    }) {
        Surface(
            shape = RectangleShape,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFF99CCFF))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Create New Group",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MyCustomFont,
                    color = Purple40
                )

                if (!isCreating) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name", fontFamily = MyCustomFont) },
                        placeholder = { Text("e.g., Hiking Team", fontFamily = MyCustomFont) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        "You'll be the HOST. Others can join via Bluetooth.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDismiss() },
                            modifier = Modifier.weight(1f),
                            shape = RectangleShape
                        ) {
                            Text("Cancel", fontFamily = MyCustomFont)
                        }

                        Button(
                            onClick = {
                                if (groupName.isNotBlank()) {
                                    val group = groupManager.createGroup(groupName.trim())
                                    isCreating = true
                                    bleSharing.startAdvertising(group)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = groupName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RectangleShape
                        ) {
                            Text("Create", fontFamily = MyCustomFont)
                        }
                    }
                } else {
                    // Advertising state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF2196F3)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            when (sharingState) {
                                is BluetoothGroupSharing.SharingState.Advertising ->
                                    "Broadcasting \"$groupName\"...\nWaiting for guests to connect"
                                is BluetoothGroupSharing.SharingState.Handshaking ->
                                    "Guest found! Handshaking..."
                                else -> "Preparing..."
                            },
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = MyCustomFont
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                bleSharing.stopAdvertising()
                                onDismiss()
                            },
                            shape = RectangleShape
                        ) {
                            Text("Stop Advertising", fontFamily = MyCustomFont)
                        }
                    }
                }
            }
        }
    }
}

// ==================== JOIN GROUP DIALOG ====================

@Composable
fun JoinGroupDialog(
    context: Context,
    groupManager: GroupManager,
    onDismiss: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }

    val bleSharing = remember { BluetoothGroupSharing(context, groupManager) }
    val discoveredGroups by bleSharing.discoveredGroups.collectAsState()
    val sharingState by bleSharing.sharingState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            isScanning = true
            bleSharing.startScanning()
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(sharingState) {
        when (sharingState) {
            is BluetoothGroupSharing.SharingState.Success -> {
                val groupName = (sharingState as BluetoothGroupSharing.SharingState.Success).groupName
                Toast.makeText(context, "Joined: $groupName", Toast.LENGTH_SHORT).show()
                delay(1000)
                bleSharing.cleanup()
                onDismiss()
            }
            is BluetoothGroupSharing.SharingState.Error -> {
                val error = (sharingState as BluetoothGroupSharing.SharingState.Error).message
                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                isScanning = false
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bleSharing.stopScanning()
            bleSharing.cleanup()
        }
    }

    Dialog(onDismissRequest = {
        if (sharingState !is BluetoothGroupSharing.SharingState.Connecting &&
            sharingState !is BluetoothGroupSharing.SharingState.Handshaking) {
            bleSharing.stopScanning()
            bleSharing.cleanup()
            onDismiss()
        }
    }) {
        Surface(
            shape = RectangleShape,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color(0xFF99CCFF))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Join a Group",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MyCustomFont,
                    color = Purple40
                )

                when (sharingState) {
                    is BluetoothGroupSharing.SharingState.Idle -> {
                        Text(
                            "Scan for nearby groups via Bluetooth",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontFamily = MyCustomFont
                        )

                        Button(
                            onClick = {
                                if (bleSharing.hasBluetoothPermissions()) {
                                    isScanning = true
                                    bleSharing.startScanning()
                                } else {
                                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    } else {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH,
                                            Manifest.permission.BLUETOOTH_ADMIN,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    }
                                    permissionLauncher.launch(permissions)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            shape = RectangleShape
                        ) {
                            Icon(Icons.Default.Search, "Scan", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Scanning", fontFamily = MyCustomFont)
                        }
                    }

                    is BluetoothGroupSharing.SharingState.Scanning -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFF2196F3)
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                "Scanning for groups...",
                                fontSize = 14.sp,
                                fontFamily = MyCustomFont
                            )

                            Spacer(Modifier.height(16.dp))

                            if (discoveredGroups.isNotEmpty()) {
                                Text(
                                    "Found ${discoveredGroups.size} group(s):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = MyCustomFont
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(discoveredGroups) { discovered ->
                                        DiscoveredGroupItem(
                                            group = discovered,
                                            onClick = {
                                                bleSharing.connectToGroup(discovered)
                                            }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "No groups found yet...",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontFamily = MyCustomFont
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    bleSharing.stopScanning()
                                    isScanning = false
                                },
                                shape = RectangleShape
                            ) {
                                Text("Stop Scanning", fontFamily = MyCustomFont)
                            }
                        }
                    }

                    is BluetoothGroupSharing.SharingState.Connecting,
                    is BluetoothGroupSharing.SharingState.Handshaking -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFF4CAF50)
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                when (sharingState) {
                                    is BluetoothGroupSharing.SharingState.Connecting ->
                                        "Connecting to host..."
                                    is BluetoothGroupSharing.SharingState.Handshaking ->
                                        "Secure handshake in progress..."
                                    else -> "Processing..."
                                },
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = MyCustomFont
                            )
                        }
                    }

                    else -> {}
                }

                if (sharingState is BluetoothGroupSharing.SharingState.Idle && !isScanning) {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape
                    ) {
                        Text("Cancel", fontFamily = MyCustomFont)
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredGroupItem(
    group: BluetoothGroupSharing.DiscoveredGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        ),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.groupName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MyCustomFont
                )
                Text(
                    "Host: ${group.deviceName}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontFamily = MyCustomFont
                )
            }

            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Connect",
                tint = Color(0xFF4CAF50)
            )
        }
    }
}

// ==================== GROUP DETAILS DIALOG ====================

@Composable
fun GroupDetailsDialog(
    context: Context,
    group: Group,
    groupManager: GroupManager,
    onDismiss: () -> Unit
) {
    var showStartSharing by remember { mutableStateOf(false) }
    var locationService by remember { mutableStateOf<LocationSharingService?>(null) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? LocationSharingService.LocalBinder
                locationService = binder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                locationService = null
            }
        }
    }

    val serviceState by locationService?.serviceState?.collectAsState()
        ?: remember { mutableStateOf(LocationSharingService.ServiceState.Idle) }

    val connectedMembers by locationService?.connectedMembers?.collectAsState()
        ?: remember { mutableStateOf(emptyList<GroupMember>()) }

    DisposableEffect(Unit) {
        val intent = Intent(context, LocationSharingService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RectangleShape,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .border(2.dp, Color(0xFF99CCFF))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MyCustomFont,
                        color = Purple40,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Status
                Text(
                    "Role: ${if (group.isHost) "HOST" else "GUEST"}",
                    fontSize = 14.sp,
                    fontFamily = MyCustomFont
                )

                if (group.isHost && group.hostAddress.isNotEmpty()) {
                    Text(
                        "Address: ${group.hostAddress}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // Sharing Controls
                Text(
                    "Position Sharing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MyCustomFont
                )

                Spacer(Modifier.height(8.dp))

                when (serviceState) {
                    is LocationSharingService.ServiceState.Idle -> {
                        Button(
                            onClick = {
                                /*val intent = Intent(context, LocationSharingService::class.java).apply {
                                    action = if (group.isHost) {
                                        LocationSharingService.ACTION_START_HOST
                                    } else {
                                        LocationSharingService.ACTION_START_GUEST
                                        putExtra(LocationSharingService.EXTRA_HOST_ADDRESS, group.hostAddress)
                                    }
                                }*/
                                val intent = Intent(context, LocationSharingService::class.java).apply {
                                    action = (if (group.isHost) {
                                        LocationSharingService.ACTION_START_HOST
                                    } else {
                                        LocationSharingService.ACTION_START_GUEST
                                        putExtra(LocationSharingService.EXTRA_HOST_ADDRESS, group.hostAddress)
                                    }) as String?
                                } // casted to string after error detection
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RectangleShape
                        ) {
                            Icon(Icons.Default.PlayArrow, "Start", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Sharing", fontFamily = MyCustomFont)
                        }
                    }

                    is LocationSharingService.ServiceState.Starting -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Starting...", fontFamily = MyCustomFont)
                        }
                    }

                    is LocationSharingService.ServiceState.HostRunning -> {
                        val state = serviceState as LocationSharingService.ServiceState.HostRunning

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                ),
                                shape = RectangleShape
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Sharing as HOST",
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = MyCustomFont
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${state.ipAddress}:${state.port}",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontFamily = MyCustomFont
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(context, LocationSharingService::class.java).apply {
                                        action = LocationSharingService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red
                                ),
                                shape = RectangleShape
                            ) {
                                Icon(Icons.Default.Close, "Stop", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Sharing", fontFamily = MyCustomFont)
                            }
                        }
                    }

                    is LocationSharingService.ServiceState.GuestRunning -> {
                        val state = serviceState as LocationSharingService.ServiceState.GuestRunning

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE3F2FD)
                                ),
                                shape = RectangleShape
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Connected to HOST",
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = MyCustomFont
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        state.hostAddress,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontFamily = MyCustomFont
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val intent = Intent(context, LocationSharingService::class.java).apply {
                                        action = LocationSharingService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red
                                ),
                                shape = RectangleShape
                            ) {
                                Icon(Icons.Default.Close, "Stop", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Sharing", fontFamily = MyCustomFont)
                            }
                        }
                    }

                    is LocationSharingService.ServiceState.Error -> {
                        val error = (serviceState as LocationSharingService.ServiceState.Error).message

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RectangleShape
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Error",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Red,
                                        fontFamily = MyCustomFont
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    error,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontFamily = MyCustomFont
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(16.dp))

                // Members List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Members",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MyCustomFont
                    )

                    Text(
                        "${connectedMembers.size} online",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(group.members) { member ->
                        MemberListItem(
                            member = member,
                            isCurrentUser = member.deviceId == groupManager.getDeviceId()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemberListItem(
    member: GroupMember,
    isCurrentUser: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFFFFF9C4) else Color(0xFFF5F5F5)
        ),
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrentUser) Color(0xFFFFC107) else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (member.isOnline) Color(0xFF4CAF50) else Color.Gray,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )

                Spacer(Modifier.width(8.dp))

                Column {
                    Text(
                        member.deviceName + if (isCurrentUser) " (You)" else "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MyCustomFont
                    )

                    Text(
                        if (member.isOnline) "Online" else "Offline",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = MyCustomFont
                    )
                }
            }

            if (member.latitude != 0.0 && member.longitude != 0.0) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Has Location",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}