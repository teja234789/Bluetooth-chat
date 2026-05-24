package com.example.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val knownPeers: Flow<List<BluetoothPeer>> = chatDao.getKnownPeers()
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getMessagesForPeer(peerAddress: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForPeer(peerAddress)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message)
    }

    suspend fun getQueuedMessagesForPeer(peerAddress: String): List<ChatMessage> {
        return chatDao.getQueuedMessagesForPeer(peerAddress)
    }

    suspend fun isMessageExisting(sender: String, receiver: String, timestamp: Long, messageText: String): Boolean {
        return chatDao.isMessageExisting(sender, receiver, timestamp, messageText)
    }

    suspend fun updateMessageStatus(messageId: Long, status: String) {
        chatDao.updateMessageStatus(messageId, status)
    }

    suspend fun deleteMessagesWithPeer(peerAddress: String) {
        chatDao.deleteMessagesWithPeer(peerAddress)
    }

    suspend fun insertPeer(peer: BluetoothPeer) {
        chatDao.insertPeer(peer)
    }

    suspend fun updatePeerBlocked(peerAddress: String, blocked: Boolean) {
        chatDao.updatePeerBlocked(peerAddress, blocked)
    }
}
