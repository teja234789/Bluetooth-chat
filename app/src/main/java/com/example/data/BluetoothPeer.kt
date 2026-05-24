package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bluetooth_peers")
data class BluetoothPeer(
    @PrimaryKey val macAddress: String,
    val name: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false
)
