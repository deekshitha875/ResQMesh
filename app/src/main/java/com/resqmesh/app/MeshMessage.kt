package com.resqmesh.app

/**
 * A single SOS message passed through the BLE mesh.
 * Room annotations removed — MessageStore now uses in-memory storage.
 */
data class MeshMessage(
    val id: String,                  // UUID generated at origin device
    val senderName: String,          // human-readable name or device ID
    val senderId: String,            // hardware device ID
    val latitude: Double,            // GPS lat at time of send
    val longitude: Double,           // GPS lon at time of send
    val messageText: String,         // free-text SOS body
    val priority: String,            // "HIGH" / "MEDIUM" / "LOW"
    val hopCount: Int,               // number of BLE hops to reach us
    val timestampEpochMs: Long,      // origination time (epoch ms)
    val receivedAtEpochMs: Long,     // time this device saw the packet
    val deliveryStatus: String       // "DELIVERED" / "IN_TRANSIT" / "FAILED"
) {
    /** Converts the stored priority string to the Priority enum. */
    fun priorityEnum(): Priority = when {
        hopCount <= 2  -> Priority.HIGH
        hopCount <= 10 -> Priority.MEDIUM
        else           -> Priority.LOW
    }

    /** Converts to MessageDetail used by MessageDetailScreen. */
    fun toDetail(): MessageDetail = MessageDetail(
        messageId        = id,
        senderId         = senderId,
        latitude         = latitude,
        longitude        = longitude,
        timestampEpochMs = timestampEpochMs,
        priority         = priorityEnum(),
        hopCount         = hopCount,
        deliveryStatus   = when (deliveryStatus) {
            "DELIVERED" -> DeliveryStatus.DELIVERED
            "FAILED"    -> DeliveryStatus.FAILED
            else        -> DeliveryStatus.IN_TRANSIT
        },
        messageText      = messageText
    )

    /** Converts to SosEntry used by RescueDashboardScreen. */
    fun toSosEntry(): SosEntry = SosEntry(
        id         = id,
        senderName = senderName,
        location   = "%.4f° N, %.4f° E".format(latitude, longitude),
        latitude   = latitude,
        longitude  = longitude,
        hopCount   = hopCount,
        priority   = priorityEnum()
    )
}
