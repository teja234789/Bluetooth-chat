package com.example.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetooth.BluetoothChatManager
import com.example.data.BluetoothPeer
import com.example.data.ChatMessage
import com.example.data.CryptoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activePeer by viewModel.activePeer.collectAsStateWithLifecycle()
    val isTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val isSimulationActive by viewModel.isSimulationActive.collectAsStateWithLifecycle()
    val myName by viewModel.userProfileName.collectAsStateWithLifecycle()
    val customPasscode by viewModel.customPasscode.collectAsStateWithLifecycle()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var selectedCryptoMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    // Handlers for dynamic edge-to-edge content in the Single-View system
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Mesh network antenna",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "MeshChat Direct",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSimulationActive) "Offline Simulation Active" else "Physical Bluetooth Link",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier.testTag("profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "My profile summary",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { showPasscodeDialog = true },
                        modifier = Modifier.testTag("passcode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encryption passcode lock",
                            tint = if (customPasscode.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Router based on Connection state
            if (activePeer != null) {
                // Active Chat space (Disconnected/Offline, Connecting, or Connected)
                ActiveChatView(
                    viewModel = viewModel,
                    activePeer = activePeer!!,
                    connectionState = connectionState,
                    isTyping = isTyping,
                    myPasscode = customPasscode,
                    onInspectCryptoMessage = { selectedCryptoMessage = it },
                    onTriggerClearHistory = { showConfirmClearDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Connection Dashboard with beautiful scanning and settings
                DiscoveryDashboardView(
                    viewModel = viewModel,
                    isBluetoothEnabled = isBluetoothEnabled,
                    isScanning = connectionState == BluetoothChatManager.ConnectionState.DISCOVERING,
                    isSimulationActive = isSimulationActive,
                    myName = myName,
                    myPasscode = customPasscode,
                    onOpenProfile = { showProfileDialog = true },
                    onOpenPasscode = { showPasscodeDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dialogs
            if (showProfileDialog) {
                ProfileEditDialog(
                    currentName = myName,
                    onDismiss = { showProfileDialog = false },
                    onConfirm = {
                        viewModel.updateProfileName(it)
                        showProfileDialog = false
                    }
                )
            }

            if (showPasscodeDialog) {
                EncryptionKeyDialog(
                    currentKey = customPasscode,
                    onDismiss = { showPasscodeDialog = false },
                    onConfirm = {
                        viewModel.updatePasscode(it)
                        showPasscodeDialog = false
                    }
                )
            }

            if (showConfirmClearDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmClearDialog = false },
                    title = { Text("Clear Chat Log?") },
                    text = { Text("This will permanently wipe this peer's message history from your secure local database. It cannot be recovered.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearActiveChatHistory()
                                showConfirmClearDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmClearDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Secure Cryptographic Envelope Inspector BottomSheet style
            if (selectedCryptoMessage != null) {
                CryptoDetailsDialog(
                    message = selectedCryptoMessage!!,
                    currentKey = customPasscode,
                    onDismiss = { selectedCryptoMessage = null }
                )
            }
        }
    }
}

@Composable
fun DiscoveryDashboardView(
    viewModel: ChatViewModel,
    isBluetoothEnabled: Boolean,
    isScanning: Boolean,
    isSimulationActive: Boolean,
    myName: String,
    myPasscode: String,
    onOpenProfile: () -> Unit,
    onOpenPasscode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val simulatedPeers by viewModel.simulatedDevices.collectAsStateWithLifecycle()
    val knownPeers by viewModel.knownPeers.collectAsStateWithLifecycle(initialValue = emptyList())

    var permissionRequestedList = remember { mutableStateListOf<String>() }

    // Compose modern runtime permission handler
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val allGranted = map.all { it.value }
        if (allGranted) {
            Toast.makeText(context, "Bluetooth permissions granted! Starting Scan.", Toast.LENGTH_SHORT).show()
            viewModel.startBluetoothScanning()
        } else {
            Toast.makeText(context, "Scanning requires permissions to discover offline devices.", Toast.LENGTH_LONG).show()
        }
    }

    fun handleScanAction() {
        if (isSimulationActive) {
            viewModel.startBluetoothScanning()
            return
        }

        // Check platform and request appropriate permissions to keep Play Store compliant
        val reqs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            reqs.add(Manifest.permission.BLUETOOTH_SCAN)
            reqs.add(Manifest.permission.BLUETOOTH_CONNECT)
            reqs.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            reqs.add(Manifest.permission.ACCESS_FINE_LOCATION)
            reqs.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        permissionLauncher.launch(reqs.toTypedArray())
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper Quick Stats cards with asymmetric grid details
        item {
            Card(
                onClick = onOpenProfile,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_quick_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = myName.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Column {
                            Text(
                                text = myName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "My Broadcast Nickname",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit name logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Encryption Indicator Banner
        item {
            Card(
                onClick = onOpenPasscode,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crypto_quick_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EnhancedEncryption,
                            contentDescription = "Encryption locks",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Symmetric Room Security",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Pass Key: ${myPasscode.ifEmpty { "Default Lock" }}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Simulation selection controller
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emulator Demo Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Generates secure mock peers Alice, Bob, or Charlie for testing when Bluetooth hardware is absent in compiling emulators.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = isSimulationActive,
                        onCheckedChange = { viewModel.toggleSimulation(it) },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("simulation_switch")
                    )
                }
            }
        }

        // Radar Scanning area
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Beautiful Animated Pulse Canvas
                RadarSweepRingCircle(isScanning = isScanning)

                Text(
                    text = if (isScanning) "Searching for direct offline nodes..." else "Inactive. Press scan to search nearby.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedButton(
                    onClick = { handleScanAction() },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isScanning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("primary_scan_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Refresh,
                            contentDescription = "Scan controllers"
                        )
                        Text(
                            text = if (isScanning) "Stop Discovery" else "Scan for Mesh Peers",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active nodes section
        item {
            Text(
                text = "DISCOVERED PEERS NEARBY (${if (isSimulationActive) simulatedPeers.size else discoveredDevices.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isSimulationActive) {
            if (simulatedPeers.isEmpty()) {
                item {
                    EmptyStatusView(text = "Turn 'Emulator Demo' switch on to list immediate simulated chat nodes.")
                }
            } else {
                items(simulatedPeers) { peer ->
                    DiscoveryPeerCard(
                        name = peer.name,
                        address = peer.macAddress,
                        signalLevel = 4,
                        isSimulated = true,
                        onClick = { viewModel.connectToSimulatedPeer(peer) }
                    )
                }
            }
        } else {
            if (discoveredDevices.isEmpty()) {
                item {
                    EmptyStatusView(text = "No direct hardware nodes discovered. Try clicking the big scan button above to announce search.")
                }
            } else {
                items(discoveredDevices) { device ->
                    val safeName = remember(device) {
                        try {
                            device.name ?: "Unnamed Device"
                        } catch (e: SecurityException) {
                            "Unnamed Device"
                        } catch (e: Exception) {
                            "Unnamed Device"
                        }
                    }
                    DiscoveryPeerCard(
                        name = safeName,
                        address = device.address,
                        signalLevel = 3,
                        isSimulated = false,
                        onClick = { viewModel.connectToRealDevice(device) }
                    )
                }
            }
        }

        // Historic / Offline Chats section
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "PREVIOUS SECURE CONVERSATIONS (${knownPeers.size})",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (knownPeers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No previous secure chats. Connect to a peer or enable 'Emulator Demo Mode' to start.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(knownPeers) { peer ->
                DiscoveryPeerCard(
                    name = peer.name + " (Saved Node)",
                    address = peer.macAddress,
                    signalLevel = 4,
                    isSimulated = peer.macAddress.startsWith("MOCK_"),
                    onClick = { 
                        // Route directly to chat screen for this peer in offline mode
                        viewModel.selectOfflinePeer(peer)
                    }
                )
            }
        }

        // Secure metadata compliance log segment
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "COMMUNICATION COMPLIANCE SUMMARY",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComplianceRow(icon = Icons.Outlined.Bluetooth, title = "Zero Internet", desc = "All chats route locally block-by-block. No telemetry, completely isolated.")
                    ComplianceRow(icon = Icons.Outlined.Security, title = "Hardware Protected", desc = "AES-128 symmetrical encryption guards all bits transmitted over airwaves.")
                    ComplianceRow(icon = Icons.Outlined.VerifiedUser, title = "Google Play Policy", desc = "Optimized foreground sockets protect battery states, permissions managed safely.")
                }
            }
        }
    }
}

@Composable
fun DiscoveryPeerCard(
    name: String,
    address: String,
    signalLevel: Int,
    isSimulated: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_card_$address")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSimulated) Icons.Default.Laptop else Icons.Default.PhoneAndroid,
                            contentDescription = "Device forms",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = address,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Signal strength simple representation
                Row(
                    modifier = Modifier.width(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 1..4) {
                        val active = i <= signalLevel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((i * 4).dp)
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Tap to secure connect",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RadarSweepRingCircle(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isScanning) {
        Box(
            modifier = modifier
                .size(160.dp)
                .drawBehind {
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.1f),
                        radius = size.minDimension / 2.1f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Gray.copy(alpha = 0.05f))
                        ),
                        radius = size.minDimension / 2.1f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SensorsOff,
                contentDescription = "Inactive radar antenna representation",
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(46.dp)
            )
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
        val progress1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring1"
        )
        val progress2 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, delayMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring2"
        )

        Box(
            modifier = modifier
                .size(160.dp)
                .drawBehind {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.minDimension / 2f

                    // Ring 1
                    drawCircle(
                        color = Color(0xFF64FFDA).copy(alpha = 0.3f * (1.0f - progress1)),
                        radius = maxRadius * progress1,
                        center = center,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Ring 2
                    drawCircle(
                        color = Color(0xFF64FFDA).copy(alpha = 0.3f * (1.0f - progress2)),
                        radius = maxRadius * progress2,
                        center = center,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Solid central core
                    drawCircle(
                        color = Color(0xFF1E88E5).copy(alpha = 0.1f),
                        radius = maxRadius * 0.25f,
                        center = center
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Searching bluetooth devices logo animation",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
fun ActiveChatView(
    viewModel: ChatViewModel,
    activePeer: BluetoothPeer,
    connectionState: BluetoothChatManager.ConnectionState,
    isTyping: Boolean,
    myPasscode: String,
    onInspectCryptoMessage: (ChatMessage) -> Unit,
    onTriggerClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle(initialValue = emptyList())
    var inputString by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Send typing notification when input updates with 2s cooldown
    LaunchedEffect(inputString) {
        if (inputString.isNotEmpty()) {
            viewModel.sendTypingPresence(true)
            delay(2000)
            viewModel.sendTypingPresence(false)
        }
    }

    // Scroll to bottom on load or new message
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Session info header
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activePeer.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        // Presence active circle glow
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = when (connectionState) {
                                        BluetoothChatManager.ConnectionState.CONNECTED -> Color.Green
                                        BluetoothChatManager.ConnectionState.CONNECTING -> Color.Yellow
                                        else -> Color.Gray
                                    },
                                    shape = CircleShape
                                )
                                .align(Alignment.BottomEnd)
                        )
                    }
                    Column {
                        Text(
                            text = activePeer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Shield details",
                                tint = if (connectionState == BluetoothChatManager.ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when (connectionState) {
                                    BluetoothChatManager.ConnectionState.CONNECTED -> "Connected (AES-128)"
                                    BluetoothChatManager.ConnectionState.CONNECTING -> "Connecting node..."
                                    else -> "Offline (Stored to sync)"
                                },
                                fontSize = 11.sp,
                                color = if (connectionState == BluetoothChatManager.ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
                
                // Dropdown control actions
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTriggerClearHistory) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Wipe logs completely",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.disconnectSession() },
                        modifier = Modifier.testTag("disconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Leave mesh chat stream",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Active Message feed
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Safe locked envelope representation",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "Safe Tunnel Established",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Every byte of message text is encrypted in transit and at rest using your room passcode: '${myPasscode.ifEmpty { "Default" }}'. Click any bubble locked icon to inspect secure payload metadata.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageBubble(
                            message = message,
                            roomPasscode = myPasscode,
                            onInspect = { onInspectCryptoMessage(message) }
                        )
                    }

                    if (isTyping) {
                        item {
                            TypingIndicatorDotRow(peerName = activePeer.name)
                        }
                    }
                }
            }
        }

        // Secure text field send tool
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputString,
                    onValueChange = { inputString = it },
                    placeholder = { Text("Write local encrypted message...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_text_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (inputString.isNotEmpty()) {
                                    viewModel.sendMessageToActivePeer(inputString)
                                    inputString = ""
                                }
                            },
                            enabled = inputString.isNotEmpty(),
                            modifier = Modifier.testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send offline packets",
                                tint = if (inputString.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    roomPasscode: String,
    onInspect: () -> Unit
) {
    val showIncoming = message.isIncoming
    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    
    val decryptedText = remember(message.messageText, roomPasscode) {
        if (message.isEncrypted) {
            CryptoUtils.decrypt(message.messageText, roomPasscode)
        } else {
            message.messageText
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (showIncoming) Arrangement.Start else Arrangement.End
    ) {
        Column(
            horizontalAlignment = if (showIncoming) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Sender details for incoming
            if (showIncoming) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            // Message Body Cards
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (showIncoming) 2.dp else 16.dp,
                    bottomEnd = if (showIncoming) 16.dp else 2.dp
                ),
                color = if (showIncoming) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.clickable { onInspect() }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = decryptedText,
                        fontSize = 14.sp,
                        color = if (showIncoming) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        // Secure shield badge
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Message is safe",
                            tint = if (showIncoming) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(11.dp)
                        )
                        
                        Text(
                            text = formattedTime,
                            fontSize = 9.sp,
                            color = if (showIncoming) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            }
                        )

                        // Sent checkmarks status
                        if (!showIncoming) {
                            Icon(
                                imageVector = when (message.deliveryStatus) {
                                    "DELIVERED", "READ" -> Icons.Default.DoneAll
                                    "FAILED" -> Icons.Default.ErrorOutline
                                    "QUEUED" -> Icons.Default.Schedule
                                    else -> Icons.Default.Done
                                },
                                contentDescription = "Delivery check state",
                                tint = if (message.deliveryStatus == "FAILED") {
                                    MaterialTheme.colorScheme.error
                                } else if (message.deliveryStatus == "QUEUED") {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorDotRow(peerName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsMotion")
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 130, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 260, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column {
            Text(
                text = "$peerName is drafting feedback...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).graphicsLayer(translationY = dot1Offset).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(modifier = Modifier.size(6.dp).graphicsLayer(translationY = dot2Offset).background(MaterialTheme.colorScheme.primary, CircleShape))
                Box(modifier = Modifier.size(6.dp).graphicsLayer(translationY = dot3Offset).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

@Composable
fun ComplianceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Policy points",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 14.sp)
        }
    }
}

@Composable
fun EmptyStatusView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BluetoothSearching,
                contentDescription = "Search loops",
                tint = Color.Gray.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.Gray,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun ProfileEditDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Broadcasting Profile Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This friendly nickname will be announced locally to other nearby Bluetooth mesh scanners.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nameInput.isNotBlank()) onConfirm(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EncryptionKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Encryption Pass Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ensure you and your peer set the SAME room passcode key. This key secures the channels dynamically via AES-128 so third parties cannot decrypt your waves.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("AES Key Passphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("passcode_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keyInput) }
            ) {
                Text("Set Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CryptoDetailsDialog(
    message: ChatMessage,
    currentKey: String,
    onDismiss: () -> Unit
) {
    val derivedKey = remember(currentKey) { currentKey.ifEmpty { "DefaultBluetoothChatSecurePasscode" } }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EnhancedEncryption,
                    contentDescription = "Symmetric encryption shield logo",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Secure Crypto Envelope")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This message payload is locked securely over classic Bluetooth air waves using AES-128 symmetrical CBC blocks.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CIPHERTEXT (TRANSMITTED BYTES):",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message.messageText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                        Text(
                            text = "PEER VERIFIED PASSWORD KEY:",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = derivedKey,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Text(
                    text = "Storing text in ciphertext prevents memory dump exposures and protects privacy when compiling local storage histories database.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss Inspect")
            }
        }
    )
}
