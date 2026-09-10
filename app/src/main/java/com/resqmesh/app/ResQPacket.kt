package com.resqmesh.app

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ResQMesh Binary Packet Protocol v1
 *
 * Frame layout (total header = 38 bytes):
 * [MAGIC:2][VERSION:1][MSG_TYPE:1][ORIGIN_ID:16][TARGET_ROLE:1]
 * [PRIORITY:1][TTL:1][HOP_COUNT:1][SEQ_NUM:4][TIMESTAMP:8]
 * [PAYLOAD_LEN:2][IV:12][AES_GCM_PAYLOAD:N][GCM_TAG:16]
 */
object ResQPacket {

    private const val TAG          = "ResQPacket"
    private val     MAGIC          = byteArrayOf(0x52, 0x51)  // "RQ"
    private const val VERSION      = 0x01.toByte()

    // Message types
    const val MSG_SOS              = 0x01.toByte()
    const val MSG_ACK              = 0x02.toByte()
    const val MSG_GATEWAY_BEACON   = 0x03.toByte()

    // Target roles
    const val ROLE_CITIZEN_RELAY   = 0x01.toByte()
    const val ROLE_RESCUE_GATEWAY  = 0xFF.toByte()

    // Shared AES-GCM key — in production, derive per-session via ECDH
    // For SIH demo this is a fixed pre-shared key (16 bytes = AES-128)
    private val PSK = byteArrayOf(
        0x52, 0x65, 0x73, 0x51, 0x4D, 0x65, 0x73, 0x68,  // "ResQMesh"
        0x53, 0x49, 0x48, 0x32, 0x30, 0x32, 0x36, 0x21   // "SIH2026!"
    )

    // ── Encode ────────────────────────────────────────────────────────────────

    fun encode(
        originId    : ByteArray,  // 16-byte device UUID
        targetRole  : Byte,
        priority    : Byte,
        ttl         : Byte,
        hopCount    : Byte,
        seqNum      : Int,
        msgType     : Byte,
        payload     : ByteArray   // plaintext JSON
    ): ByteArray {
        val iv          = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val encPayload  = aesGcmEncrypt(payload, iv)
        val timestamp   = System.currentTimeMillis()

        val buf = ByteBuffer.allocate(
            2 + 1 + 1 + 16 + 1 + 1 + 1 + 1 + 4 + 8 + 2 + 12 + encPayload.size
        ).order(ByteOrder.BIG_ENDIAN)

        buf.put(MAGIC)
        buf.put(VERSION)
        buf.put(msgType)
        buf.put(originId.copyOf(16))
        buf.put(targetRole)
        buf.put(priority)
        buf.put(ttl)
        buf.put(hopCount)
        buf.putInt(seqNum)
        buf.putLong(timestamp)
        buf.putShort(encPayload.size.toShort())
        buf.put(iv)
        buf.put(encPayload)

        return buf.array()
    }

    // ── Decode ────────────────────────────────────────────────────────────────

    data class DecodedPacket(
        val msgType    : Byte,
        val originId   : ByteArray,
        val targetRole : Byte,
        val priority   : Byte,
        val ttl        : Byte,
        val hopCount   : Byte,
        val seqNum     : Int,
        val timestamp  : Long,
        val payload    : ByteArray  // decrypted JSON
    )

    fun decode(bytes: ByteArray): DecodedPacket? {
        try {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            // Validate magic
            val magic = ByteArray(2).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) {
                Log.w(TAG, "Invalid magic bytes")
                return null
            }

            val version     = buf.get()
            val msgType     = buf.get()
            val originId    = ByteArray(16).also { buf.get(it) }
            val targetRole  = buf.get()
            val priority    = buf.get()
            val ttl         = buf.get()
            val hopCount    = buf.get()
            val seqNum      = buf.int
            val timestamp   = buf.long
            val payloadLen  = buf.short.toInt() and 0xFFFF
            val iv          = ByteArray(12).also { buf.get(it) }
            val encPayload  = ByteArray(payloadLen).also { buf.get(it) }

            val plaintext = aesGcmDecrypt(encPayload, iv) ?: run {
                Log.w(TAG, "AES-GCM decryption failed")
                return null
            }

            return DecodedPacket(
                msgType    = msgType,
                originId   = originId,
                targetRole = targetRole,
                priority   = priority,
                ttl        = ttl,
                hopCount   = hopCount,
                seqNum     = seqNum,
                timestamp  = timestamp,
                payload    = plaintext
            )
        } catch (e: Exception) {
            Log.e(TAG, "Packet decode error: ${e.message}")
            return null
        }
    }

    // ── AES-GCM ───────────────────────────────────────────────────────────────

    private fun aesGcmEncrypt(plaintext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(PSK, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)  // includes 16-byte GCM tag appended
    }

    private fun aesGcmDecrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(PSK, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }
}
