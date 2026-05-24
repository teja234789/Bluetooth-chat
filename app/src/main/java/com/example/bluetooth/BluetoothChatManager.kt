package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.BluetoothPeer
import com.example.data.ChatMessage
import com.example.data.CryptoUtils
import com.example.data.ChatRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.IOException
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothChatManager(
    private val context: Context,
    private val repository: ChatRepository
) {
    private val TAG = "BluetoothChatManager"
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard Serial Port UUID

    // Connection States
    enum class ConnectionState {
        NONE,
        DISCOVERING,
        LISTENING,
        CONNECTING,
        CONNECTED
    }

    // Live variables
    private val _connectionState = MutableStateFlow(ConnectionState.NONE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activePeer = MutableStateFlow<BluetoothPeer?>(null)
    val activePeer: StateFlow<BluetoothPeer?> = _activePeer.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _peerTyping = MutableStateFlow(false)
    val peerTyping: StateFlow<Boolean> = _peerTyping.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    // Simulation Option to test on emulator
    private val _simulationActive = MutableStateFlow(true) // Defaults to true on emulators so users can test immediately!
    val simulationActive: StateFlow<Boolean> = _simulationActive.asStateFlow()

    private val _simulatedDevices = MutableStateFlow<List<BluetoothPeer>>(emptyList())
    val simulatedDevices: StateFlow<List<BluetoothPeer>> = _simulatedDevices.asStateFlow()

    // Sockets & Threads
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    private var receiverRegistered = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Moshi Setup
    private val moshi = Moshi.Builder().build()
    private val packetAdapter = moshi.adapter(BluetoothPacket::class.java)

    // Local profile info
    var myName: String = "User ${Random().nextInt(900) + 100}"
    var passCode: String = "1234" // Default symmetric chat room lock

    private val mockResponses = listOf(
        "Hello! This connection is 100% peer-to-peer and offline.",
        "Your message was received successfully. Decoded from AES-128!",
        "Yes, Android devices scan and advertise local nodes continuously.",
        "That is impressive. Zero internet connections required!",
        "Let's protect our privacy. Ensure our Pass Keys match to read chats.",
        "Nice aesthetic design. The text bubble bubbles beautifully!",
        "Let me try typing something long to simulate the live presence typing dots..."
    )

    private var mockChatJob: Job? = null

    fun getSafeDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Unnamed Device"
        } catch (e: SecurityException) {
            "Unnamed Device"
        } catch (e: Exception) {
            "Unnamed Device"
        }
    }

    fun getSafeDeviceAddress(device: BluetoothDevice): String {
        return try {
            device.address ?: "02:00:00:00:00:00"
        } catch (e: SecurityException) {
            "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }

    private fun getSafeAdapterAddress(): String {
        return try {
            bluetoothAdapter?.address ?: "self"
        } catch (e: SecurityException) {
            "self"
        } catch (e: Exception) {
            "self"
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun isDiscovering(): Boolean {
        return try {
            bluetoothAdapter?.isDiscovering ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun cancelDiscovery() {
        try {
            if (bluetoothAdapter != null && isDiscovering()) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on cancelDiscovery: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception on cancelDiscovery: ${e.message}")
        }
    }

    private fun startDiscoverySafe() {
        try {
            bluetoothAdapter?.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on startDiscovery: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception on startDiscovery: ${e.message}")
        }
    }

    init {
        try {
            checkBluetoothAvailability()
            if (bluetoothAdapter == null) {
                _simulationActive.value = true
                loadMockDevices()
            } else {
                // Check status safely
                _isBluetoothEnabled.value = isBluetoothEnabled()
                // Default to simulation mode if real BT is disabled or has no permissions yet
                _simulationActive.value = !_isBluetoothEnabled.value
                if (_simulationActive.value) {
                    loadMockDevices()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in BluetoothChatManager init: ${e.message}")
            _simulationActive.value = true
            loadMockDevices()
            _isBluetoothEnabled.value = false
        }
    }

    private fun checkBluetoothAvailability() {
        _isBluetoothEnabled.value = isBluetoothEnabled()
    }

    fun setSimulation(enabled: Boolean) {
        _simulationActive.value = enabled
        if (enabled) {
            loadMockDevices()
            disconnectCurrent()
        } else {
            _simulatedDevices.value = emptyList()
            disconnectCurrent()
            startListening()
        }
    }

    private fun loadMockDevices() {
        _simulatedDevices.value = listOf(
            BluetoothPeer("MOCK_ADDR_ALICE_1", "Alice (Security Specialist)", System.currentTimeMillis() - 5000, false),
            BluetoothPeer("MOCK_ADDR_BOB_2", "Bob (Developer Node)", System.currentTimeMillis() - 100000, false),
            BluetoothPeer("MOCK_ADDR_CHARLIE_3", "Charlie (Privacy Advocate)", System.currentTimeMillis() - 600000, false)
        )
    }

    /**
     * Start Scanning/Discovery for Nearby Devices.
     */
    fun startDiscovery() {
        if (_simulationActive.value) {
            _connectionState.value = ConnectionState.DISCOVERING
            // In simulation block, keep it scanning for 3s, then return to NONE/LISTENING
            scope.launch {
                delay(3000)
                if (_connectionState.value == ConnectionState.DISCOVERING) {
                    _connectionState.value = ConnectionState.NONE
                }
            }
            return
        }

        if (bluetoothAdapter == null || !isBluetoothEnabled()) return

        _connectionState.value = ConnectionState.DISCOVERING
        _discoveredDevices.value = emptyList()

        cancelDiscovery()

        // Register for broadcasts when a device is discovered
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        receiverRegistered = true

        startDiscoverySafe()
        Log.d(TAG, "Started real Bluetooth discovery scanning.")
    }

    fun stopDiscovery() {
        if (_simulationActive.value) {
            _connectionState.value = ConnectionState.NONE
            return
        }
        cancelDiscovery()
        _connectionState.value = ConnectionState.NONE
        unregisterReceiver()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    val currentList = _discoveredDevices.value.toMutableList()
                    val address = getSafeDeviceAddress(device)
                    if (!currentList.any { getSafeDeviceAddress(it) == address }) {
                        currentList.add(device)
                        _discoveredDevices.value = currentList
                        Log.d(TAG, "Discovered device: ${getSafeDeviceName(device)} [$address]")
                    }
                }
            }
        }
    }

    private fun unregisterReceiver() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
            receiverRegistered = false
        }
    }

    /**
     * Start background server socket to listen for incoming connections.
     */
    fun startListening() {
        if (_simulationActive.value) {
            _connectionState.value = ConnectionState.LISTENING
            return
        }

        if (bluetoothAdapter == null || !isBluetoothEnabled()) return

        cancelThreadsConnected()
        _connectionState.value = ConnectionState.LISTENING

        acceptThread = AcceptThread().apply { start() }
        Log.d(TAG, "Server listening started.")
    }

    /**
     * Connect to a specific remote device.
     */
    fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothAdapter == null || !isBluetoothEnabled()) return

        cancelThreadsConnecting()

        _connectionState.value = ConnectionState.CONNECTING
        val peer = BluetoothPeer(
            getSafeDeviceAddress(device),
            getSafeDeviceName(device),
            System.currentTimeMillis()
        )
        _activePeer.value = peer

        connectThread = ConnectThread(device).apply { start() }
    }

    /**
     * Set active peer for offline messaging without triggering an active connection attempt.
     */
    fun selectPeerOffline(peer: BluetoothPeer) {
        _activePeer.value = peer
    }

    /**
     * Connect to a simulated mock device on Emulator.
     */
    fun connectToSimulatedDevice(peer: BluetoothPeer) {
        _connectionState.value = ConnectionState.CONNECTING
        _activePeer.value = peer

        mockChatJob?.cancel()
        mockChatJob = scope.launch {
            // Record peer in local database
            repository.insertPeer(peer)

            delay(1500) // Simulate pairing latency
            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Mock Peer Connected: ${peer.name}")

            // Automatically transmit locally queued offline messages when the connection is re-established
            syncOfflineQueuedMessages(peer.macAddress)

            // Simulating offline unsent messages sync from peer to us upon reconnecting
            try {
                delay(800)
                val offlineTexts = when (peer.macAddress) {
                    "MOCK_ADDR_ALICE_1" -> listOf(
                        "Hey, are you around? I'm draft sync test offline.",
                        "No worries, I know you are offline. Sending this so it syncs when we reconnect!"
                    )
                    "MOCK_ADDR_BOB_2" -> listOf(
                        "I've written some node drafts for you.",
                        "These should sync automatically as soon as Bluetooth links up."
                    )
                    else -> listOf(
                        "Offline sync is working incredibly well.",
                        "Using the Room local message store is brilliant."
                    )
                }

                for ((index, text) in offlineTexts.withIndex()) {
                    val encrypted = CryptoUtils.encrypt(text, passCode)
                    // Subtract minutes to simulate offline receipt
                    val offlineTimestamp = System.currentTimeMillis() - (offlineTexts.size - index) * 60000
                    val exists = repository.isMessageExisting(
                        sender = peer.macAddress,
                        receiver = "self",
                        timestamp = offlineTimestamp,
                        messageText = encrypted
                    )
                    if (!exists) {
                        val offlineMessage = ChatMessage(
                            senderAddress = peer.macAddress,
                            senderName = peer.name,
                            receiverAddress = "self",
                            messageText = encrypted,
                            timestamp = offlineTimestamp,
                            isIncoming = true,
                            encryptionKeyUsed = passCode,
                            deliveryStatus = "READ"
                        )
                        repository.insertMessage(offlineMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error simulating incoming offline messages sync: ${e.message}")
            }

            // Simulating typing and greeting
            delay(1500)
            _peerTyping.value = true
            delay(2000)
            _peerTyping.value = false

            val greeting = when (peer.macAddress) {
                "MOCK_ADDR_ALICE_1" -> "Hi there! I am Alice. I've set my chat room lock passcode to match yours. Let's try messaging!"
                "MOCK_ADDR_BOB_2" -> "Hey peer! This is Bob. Ready to test secure offline channels."
                else -> "Greetings. Charlie here. Everything in this stream is local and secure."
            }

            val greetingEncrypted = CryptoUtils.encrypt(greeting, passCode)
            val existsGreeting = repository.isMessageExisting(
                sender = peer.macAddress,
                receiver = "self",
                timestamp = System.currentTimeMillis(),
                messageText = greetingEncrypted
            )
            if (!existsGreeting) {
                val chatMessage = ChatMessage(
                    senderAddress = peer.macAddress,
                    senderName = peer.name,
                    receiverAddress = "self",
                    messageText = greetingEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isIncoming = true,
                    encryptionKeyUsed = passCode,
                    deliveryStatus = "READ"
                )
                repository.insertMessage(chatMessage)
            }
        }
    }

    /**
     * Send encrypted text message to the currently active peer.
     */
    fun sendMessage(text: String) {
        val currentPeer = _activePeer.value ?: return
        val encryptedText = CryptoUtils.encrypt(text, passCode)

        val isConnected = _connectionState.value == ConnectionState.CONNECTED
        val status = if (isConnected) "SENT" else "QUEUED"

        // 1. Store in our local message history db as SENT or QUEUED state
        val localMessage = ChatMessage(
            senderAddress = "self",
            senderName = myName,
            receiverAddress = currentPeer.macAddress,
            messageText = encryptedText,
            timestamp = System.currentTimeMillis(),
            isIncoming = false,
            encryptionKeyUsed = passCode,
            deliveryStatus = status
        )

        scope.launch {
            val messageId = repository.insertMessage(localMessage)

            // Update peer's last seen
            repository.insertPeer(BluetoothPeer(currentPeer.macAddress, currentPeer.name, System.currentTimeMillis()))

            if (isConnected) {
                if (_simulationActive.value) {
                    // Simulate sending delay and then automated response
                    delay(400)
                    repository.updateMessageStatus(messageId, "DELIVERED")

                    mockChatJob?.cancel()
                    mockChatJob = scope.launch {
                        delay(2500)
                        _peerTyping.value = true
                        delay(1500)
                        _peerTyping.value = false

                        val randomReplyMsg = mockResponses[Random().nextInt(mockResponses.size)]
                        val encryptedReply = CryptoUtils.encrypt(randomReplyMsg, passCode)

                        val replyMsg = ChatMessage(
                            senderAddress = currentPeer.macAddress,
                            senderName = currentPeer.name,
                            receiverAddress = "self",
                            messageText = encryptedReply,
                            timestamp = System.currentTimeMillis(),
                            isIncoming = true,
                            encryptionKeyUsed = passCode,
                            deliveryStatus = "READ"
                        )
                        repository.insertMessage(replyMsg)
                    }
                } else {
                    // Real socket transmission
                    val packet = BluetoothPacket(
                        type = "CHAT",
                        senderName = myName,
                        senderAddress = getSafeAdapterAddress(),
                        textContent = encryptedText,
                        timestamp = localMessage.timestamp
                    )
                    
                    val packetJson = packetAdapter.toJson(packet) + "\n"
                    val sendSuccess = connectedThread?.write(packetJson.toByteArray()) ?: false
                    
                    if (sendSuccess) {
                        repository.updateMessageStatus(messageId, "DELIVERED")
                    } else {
                        repository.updateMessageStatus(messageId, "FAILED")
                    }
                }
            } else {
                Log.d(TAG, "Sent offline message: saved locally as QUEUED")
            }
        }
    }

    /**
     * Inform the peer whether client is currently typing in the textbox.
     */
    fun sendTypingState(typing: Boolean) {
        if (_simulationActive.value) return
        val currentPeer = _activePeer.value ?: return

        scope.launch {
            val packet = BluetoothPacket(
                type = "TYPING",
                senderName = myName,
                senderAddress = getSafeAdapterAddress(),
                textContent = "",
                isTyping = typing,
                timestamp = System.currentTimeMillis()
            )
            val packetJson = packetAdapter.toJson(packet) + "\n"
            connectedThread?.write(packetJson.toByteArray())
        }
    }

    /**
     * Closes current socket and returns to listen state.
     */
    fun disconnectCurrent() {
        mockChatJob?.cancel()
        _peerTyping.value = false
        _activePeer.value = null

        cancelThreadsConnected()
        _connectionState.value = ConnectionState.NONE

        if (!_simulationActive.value) {
            startListening()
        }
    }

    private fun cancelThreadsConnecting() {
        connectThread?.cancel()
        connectThread = null
    }

    private fun cancelThreadsConnected() {
        connectedThread?.cancel()
        connectedThread = null
    }

    /**
     * Managed thread that runs after connection establishes to listen for incoming bytes.
     */
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private val reader = BufferedReader(InputStreamReader(inputStream))
        private var isActive = true

        init {
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun run() {
            var line: String?
            while (isActive) {
                try {
                    line = reader.readLine()
                    if (line == null) {
                        Log.d(TAG, "Socket stream ended. Disconnecting socket thread.")
                        connectionLost()
                        break
                    }
                    parseReceivedLine(line)
                } catch (e: IOException) {
                    Log.e(TAG, "Socket communication exception: ${e.message}")
                    connectionLost()
                    break
                }
            }
        }

        private fun parseReceivedLine(line: String) {
            try {
                val packet = packetAdapter.fromJson(line) ?: return
                when (packet.type) {
                    "TYPING" -> {
                        _peerTyping.value = packet.isTyping
                    }
                    "CHAT" -> {
                        _peerTyping.value = false
                        scope.launch {
                            val exists = repository.isMessageExisting(
                                sender = packet.senderAddress,
                                receiver = "self",
                                timestamp = packet.timestamp,
                                messageText = packet.textContent
                            )
                            if (!exists) {
                                val incomingMessage = ChatMessage(
                                    senderAddress = packet.senderAddress,
                                    senderName = packet.senderName,
                                    receiverAddress = "self",
                                    messageText = packet.textContent,
                                    timestamp = packet.timestamp,
                                    isIncoming = true,
                                    encryptionKeyUsed = passCode, // Assuming the passcode matches
                                    deliveryStatus = "READ"
                                )
                                repository.insertMessage(incomingMessage)
                            }
                            // Log or refresh peer details
                            repository.insertPeer(
                                BluetoothPeer(packet.senderAddress, packet.senderName, System.currentTimeMillis())
                            )
                        }
                    }
                    "PING" -> {
                        // Respond with ACK to show presence
                        val ackPacket = BluetoothPacket(
                            type = "ACK",
                            senderName = myName,
                            senderAddress = getSafeAdapterAddress(),
                            textContent = "",
                            timestamp = System.currentTimeMillis()
                        )
                        val ackJson = packetAdapter.toJson(ackPacket) + "\n"
                        write(ackJson.toByteArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Bluetooth packet JSON line: ${e.message}")
            }
        }

        fun write(buffer: ByteArray): Boolean {
            return try {
                outputStream.write(buffer)
                outputStream.flush()
                true
            } catch (e: IOException) {
                Log.e(TAG, "Write bytes stream failure: ${e.message}")
                false
            }
        }

        fun cancel() {
            isActive = false
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed closing Bluetooth socket: ${e.message}")
            }
        }
    }

    private fun connectionLost() {
        Log.e(TAG, "Connection lost.")
        scope.launch(Dispatchers.Main) {
            _connectionState.value = ConnectionState.NONE
            _activePeer.value = null
            _peerTyping.value = false
            startListening()
        }
    }

    /**
     * Managed thread that listens for incoming connection requests.
     */
    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? = try {
            bluetoothAdapter?.listenUsingRfcommWithServiceRecord("BluetoothChat", MY_UUID)
        } catch (e: Exception) {
            Log.e(TAG, "listen() failed: ${e.message}")
            null
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (_connectionState.value != ConnectionState.CONNECTED) {
                try {
                    socket = serverSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "accept() socket closed: ${e.message}")
                    break
                }

                if (socket != null) {
                    synchronized(this@BluetoothChatManager) {
                        when (_connectionState.value) {
                            ConnectionState.LISTENING, ConnectionState.CONNECTING -> {
                                // Establish connecting
                                val device = socket.remoteDevice
                                val peer = BluetoothPeer(
                                    getSafeDeviceAddress(device),
                                    getSafeDeviceName(device),
                                    System.currentTimeMillis()
                                )
                                _activePeer.value = peer
                                scope.launch {
                                    repository.insertPeer(peer)
                                }
                                startConnected(socket)
                            }
                            ConnectionState.NONE, ConnectionState.CONNECTED, ConnectionState.DISCOVERING -> {
                                // Already connected, or inactive. Terminate excess socket
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Server socket close failed: ${e.message}")
            }
        }
    }

    /**
     * Thread that initiates communication sequence outward to a server node.
     */
    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {
        // Standard serial socket
        private val socket: BluetoothSocket? = try {
            val deviceClass = device::class.java
            val method = deviceClass.getMethod("createRfcommSocketToServiceRecord", UUID::class.java)
            method.invoke(device, UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")) as BluetoothSocket
        } catch (e: Exception) {
            Log.e("ConnectThread", "Instance creation failed: ${e.message}")
            null
        }

        override fun run() {
            try {
                socket?.connect()
            } catch (connectException: IOException) {
                Log.e("ConnectThread", "Connection failed, closing socket: ${connectException.message}")
                try {
                    socket?.close()
                } catch (closeException: IOException) {}
                connectionLost()
                return
            }

            // Connection succeeded. Spin secondary stream loop
            synchronized(this@BluetoothChatManager) {
                socket?.let { 
                    startConnected(it)
                }
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {}
        }
    }

    private fun startConnected(socket: BluetoothSocket) {
        cancelThreadsConnecting()
        connectedThread = ConnectedThread(socket).apply { start() }
        _activePeer.value?.let { peer ->
            syncOfflineQueuedMessages(peer.macAddress)
        }
    }

    /**
     * Synchronize and automatically transmit any locally queued offline messages for the peer.
     */
    fun syncOfflineQueuedMessages(peerAddress: String) {
        scope.launch {
            val queued = repository.getQueuedMessagesForPeer(peerAddress)
            if (queued.isNotEmpty()) {
                Log.d(TAG, "Syncing ${queued.size} queued messages for peer $peerAddress.")
                for (msg in queued) {
                    if (_simulationActive.value) {
                        delay(400)
                        repository.updateMessageStatus(msg.id, "DELIVERED")
                    } else {
                        val packet = BluetoothPacket(
                            type = "CHAT",
                            senderName = myName,
                            senderAddress = getSafeAdapterAddress(),
                            textContent = msg.messageText,
                            timestamp = msg.timestamp
                        )
                        val packetJson = packetAdapter.toJson(packet) + "\n"
                        val sendSuccess = connectedThread?.write(packetJson.toByteArray()) ?: false
                        if (sendSuccess) {
                            repository.updateMessageStatus(msg.id, "DELIVERED")
                        } else {
                            // Stop syncing if the thread fails to write (disconnected again)
                            break
                        }
                    }
                }
            }
        }
    }

    fun onDestroy() {
        unregisterReceiver()
        cancelThreadsConnecting()
        cancelThreadsConnected()
        acceptThread?.cancel()
        acceptThread = null
        mockChatJob?.cancel()
        scope.cancel()
    }
}
