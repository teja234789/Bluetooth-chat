package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("""
        SELECT * FROM chat_messages 
        WHERE (senderAddress = :peerAddress AND receiverAddress = 'self') 
           OR (senderAddress = 'self' AND receiverAddress = :peerAddress) 
        ORDER BY timestamp ASC
    """)
    fun getMessagesForPeer(peerAddress: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): ChatMessage?

    @Query("SELECT * FROM chat_messages WHERE receiverAddress = :peerAddress AND deliveryStatus = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getQueuedMessagesForPeer(peerAddress: String): List<ChatMessage>

    @Query("SELECT EXISTS(SELECT 1 FROM chat_messages WHERE senderAddress = :sender AND receiverAddress = :receiver AND timestamp = :timestamp AND messageText = :messageText LIMIT 1)")
    suspend fun isMessageExisting(sender: String, receiver: String, timestamp: Long, messageText: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    @Query("DELETE FROM chat_messages WHERE (senderAddress = :peerAddress AND receiverAddress = 'self') OR (senderAddress = 'self' AND receiverAddress = :peerAddress)")
    suspend fun deleteMessagesWithPeer(peerAddress: String)

    @Query("SELECT * FROM bluetooth_peers ORDER BY lastSeen DESC")
    fun getKnownPeers(): Flow<List<BluetoothPeer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: BluetoothPeer)

    @Query("UPDATE bluetooth_peers SET isBlocked = :blocked WHERE macAddress = :peerAddress")
    suspend fun updatePeerBlocked(peerAddress: String, blocked: Boolean)
}
