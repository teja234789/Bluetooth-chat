package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothChatManager
import com.example.data.BluetoothPeer
import com.example.data.ChatMessage
import com.example.data.ChatDatabase
import com.example.data.ChatRepository
import com.example.data.CryptoUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {

    val chatManager = BluetoothChatManager(application, repository)

    // Configuration / Shared keys (Could be persistent, backing with simple memory/StateFlow)
    private val _userProfileName = MutableStateFlow(chatManager.myName)
    val userProfileName: StateFlow<String> = _userProfileName.asStateFlow()

    private val _customPasscode = MutableStateFlow(chatManager.passCode)
    val customPasscode: StateFlow<String> = _customPasscode.asStateFlow()

    // Observe active presence states
    val connectionState = chatManager.connectionState
    val activePeer = chatManager.activePeer
    val isPeerTyping = chatManager.peerTyping
    val isBluetoothEnabled = chatManager.isBluetoothEnabled
    val isSimulationActive = chatManager.simulationActive

    // Lists of devices
    val discoveredDevices = chatManager.discoveredDevices
    val simulatedDevices = chatManager.simulatedDevices

    // Live message feed for the selected active peer
    val activeChatMessages: StateFlow<List<ChatMessage>> = activePeer
        .flatMapLatest { peer ->
            if (peer == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForPeer(peer.macAddress)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val knownPeers = repository.knownPeers

    init {
        // Shared dynamic check
        viewModelScope.launch {
            _userProfileName.collectLatest { name ->
                chatManager.myName = name
            }
        }
        viewModelScope.launch {
            _customPasscode.collectLatest { key ->
                chatManager.passCode = key
            }
        }
    }

    fun updateProfileName(name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) {
            _userProfileName.value = cleanName
        }
    }

    fun updatePasscode(key: String) {
        _customPasscode.value = key
    }

    fun toggleSimulation(active: Boolean) {
        chatManager.setSimulation(active)
    }

    fun startBluetoothScanning() {
        chatManager.startDiscovery()
    }

    fun stopBluetoothScanning() {
        chatManager.stopDiscovery()
    }

    fun connectToRealDevice(device: android.bluetooth.BluetoothDevice) {
        chatManager.connectToDevice(device)
    }

    fun connectToSimulatedPeer(peer: BluetoothPeer) {
        chatManager.connectToSimulatedDevice(peer)
    }

    fun sendMessageToActivePeer(text: String) {
        if (text.trim().isNotEmpty()) {
            chatManager.sendMessage(text)
        }
    }

    fun sendTypingPresence(isTyping: Boolean) {
        chatManager.sendTypingState(isTyping)
    }

    fun selectOfflinePeer(peer: BluetoothPeer) {
        chatManager.selectPeerOffline(peer)
    }

    fun disconnectSession() {
        chatManager.disconnectCurrent()
    }

    fun clearActiveChatHistory() {
        val peerAddr = activePeer.value?.macAddress ?: return
        viewModelScope.launch {
            repository.deleteMessagesWithPeer(peerAddr)
        }
    }

    fun toggleBlockActivePeer(blocked: Boolean) {
        val peerAddr = activePeer.value?.macAddress ?: return
        viewModelScope.launch {
            repository.updatePeerBlocked(peerAddr, blocked)
            // Disconnect if we block them
            if (blocked) {
                disconnectSession()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatManager.onDestroy()
    }
}

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
