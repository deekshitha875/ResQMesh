package com.resqmesh.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * MessageStore — in-memory message repository.
 *
 * No Room / no code generation needed. Works immediately on any build setup.
 * Messages are kept in memory for the session. For a hackathon demo this is
 * perfectly fine — the rescue dashboard repopulates from live BLE traffic anyway.
 */
object MessageStore {

    // Single source of truth — a map keyed by message UUID
    private val _messages = MutableStateFlow<Map<String, MeshMessage>>(emptyMap())

    /** Upsert a message (insert or replace by id). */
    fun upsert(msg: MeshMessage) {
        _messages.value = _messages.value + (msg.id to msg)
    }

    /** Observe all messages ordered newest-first — drives the rescue dashboard. */
    fun observeAll(): Flow<List<MeshMessage>> =
        _messages.map { map ->
            map.values.sortedByDescending { it.receivedAtEpochMs }
        }

    /** Single lookup by UUID. */
    suspend fun getById(id: String): MeshMessage? = _messages.value[id]

    /** Remove messages older than 24 hours to keep memory lean. */
    fun pruneOld() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        _messages.value = _messages.value.filter { (_, msg) ->
            msg.receivedAtEpochMs >= cutoff
        }
    }
}
