package com.example.bluetooth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BluetoothPacket(
    val type: String,          // "CHAT" | "TYPING" | "PING" | "ACK"
    val senderName: String,
    val senderAddress: String,
    val textContent: String,    // Encrypted string (AES-128 ciphertext in Base64)
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
