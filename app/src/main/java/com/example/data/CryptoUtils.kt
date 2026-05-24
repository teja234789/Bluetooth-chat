package com.example.data

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    
    // Static 16-bye initialization vector for simplicity of demonstration,
    // though in a production app random IV or GCM mode is preferred.
    private val ivBytes = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16
    )

    /**
     * Derives a 16-byte (128-bit) secret key from any passcode using SHA-256
     */
    private fun deriveKey(passcode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val cleanPass = passcode.ifEmpty { "DefaultBluetoothChatSecurePasscode" }
        val hash = digest.digest(cleanPass.toByteArray(Charsets.UTF_8))
        // Take the first 16 bytes for AES-128
        val keyBytes = hash.copyOfRange(0, 16)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext message using AES-128 with SHA-256 derived key from passcode.
     */
    fun encrypt(plainText: String, passcode: String): String {
        return try {
            val keySpec = deriveKey(passcode)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple obfuscation if exception occurs
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    /**
     * Decrypts base64 encoded ciphertext using AES-128 with SHA-256 derived key from passcode.
     */
    fun decrypt(cipherText: String, passcode: String): String {
        return try {
            val keySpec = deriveKey(passcode)
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (e.g. wrong key), return a clean fallback or indication
            try {
                // Check if it's base64 encoded and we can at least show base64 raw text or original text
                val devVal = String(Base64.decode(cipherText, Base64.NO_WRAP), Charsets.UTF_8)
                "[Decrypt Error - Wrong Pass Key! Metadata: $devVal]"
            } catch (ex: Exception) {
                "[Encrypted Message: $cipherText]"
            }
        }
    }
}
