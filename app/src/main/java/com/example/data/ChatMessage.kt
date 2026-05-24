package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val senderAddress: String,
    val senderName: String,
    val receiverAddress: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val isEncrypted: Boolean = true,
    val encryptionKeyUsed: String = "Default",
    val deliveryStatus: String = "SENT" // "SENT", "DELIVERED", "READ"
)
