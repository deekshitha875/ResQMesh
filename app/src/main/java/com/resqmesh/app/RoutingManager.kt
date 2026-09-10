

package com.resqmesh.app

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * RoutingManager — ResQMesh Mesh Routing Logic
 *
 * Connects to:
 *  - BleManager       (peer discovery, advertise, scan)
 *  - MessageStore     (local message repository)
 *  - MeshViewModel    (exposes state to UI)
 *
 * Node Roles:
 *  SURVIVOR  → originates SOS packets, does NOT relay
 *  RELAY     → forwards packets toward gateway, does NOT originate
 *  GATEWAY   → final destination, sinks packets, never re-forwards
 */

// ── Node Role ─────────────────────────────────────────────────────────────────

enum class NodeRole {
    SURVIVOR,   // Person in distress — sends SOS
    RELAY,      // Bystander phone — relays packets
    GATEWAY     // Rescue Team phone — receives and displays SOS
}

// ── Peer info learned from BLE advertisements ─────────────────────────────────

data class PeerInfo(
    val deviceAddress: String,
    val role: NodeRole,
    val gatewayDistance: Int,   // hops to gateway (0 if this peer IS gateway)
    val lastSeenMs: Long = System.currentTimeMillis()
)

// ── Well-known Gateway Broadcast ID ──────────────────────────────────────────
// For MVP with one gateway: all survivors/relays target this constant.
// For multiple gateways: replace with a Set<String> of known gateway IDs.

const val GATEWAY_BROADCAST_ID = "RESQMESH_GATEWAY_ANY"

// ── Routing Manager ───────────────────────────────────────────────────────────

class RoutingManager(
    private val localRole: NodeRole,
    private val localDeviceId: String
) {
    companion object {
        private const val TAG = "RoutingManager"
        private const val PEER_TIMEOUT_MS = 30_000L  // 30 seconds
    }

    // Known peers discovered via BLE scan
    private val knownPeers = ConcurrentHashMap<String, PeerInfo>()

    // ── Peer registry ─────────────────────────────────────────────────────────

    /**
     * Call this when BLE scanner discovers an advertisement from a ResQMesh peer.
     * BleManager should call this for every scan result containing SERVICE_UUID.
     */
    fun onPeerDiscovered(peer: PeerInfo) {
        knownPeers[peer.deviceAddress] = peer
        Log.d(TAG, "Peer discovered: ${peer.deviceAddress} role=${peer.role} dist=${peer.gatewayDistance}")
        cleanStalePeers()
    }

    fun onPeerLost(deviceAddress: String) {
        knownPeers.remove(deviceAddress)
        Log.d(TAG, "Peer lost: $deviceAddress")
    }

    private fun cleanStalePeers() {
        val now = System.currentTimeMillis()
        knownPeers.entries.removeIf { now - it.value.lastSeenMs > PEER_TIMEOUT_MS }
    }

    // ── Routing decision ──────────────────────────────────────────────────────

    /**
     * Given a packet that needs to be forwarded, returns the best peer to
     * forward it to, or null if no peer is available yet (store-carry-forward).
     *
     * Priority order:
     *  1. Peer with role = GATEWAY (direct delivery — highest priority)
     *  2. Peer with lowest gatewayDistance (gradient routing)
     *  3. Any available RELAY peer (flooding fallback)
     */
    fun selectForwardTarget(packet: RoutablePacket): PeerInfo? {
        if (localRole == NodeRole.GATEWAY) {
            // Gateway never forwards — it's the sink
            return null
        }

        val activePeers = knownPeers.values
            .filter { System.currentTimeMillis() - it.lastSeenMs < PEER_TIMEOUT_MS }
            .filter { it.deviceAddress != localDeviceId }

        if (activePeers.isEmpty()) {
            Log.d(TAG, "No active peers — store-carry-forward for ${packet.messageId}")
            return null
        }

        // Priority 1: Direct gateway nearby
        val gatewayPeer = activePeers.firstOrNull { it.role == NodeRole.GATEWAY }
        if (gatewayPeer != null) {
            Log.i(TAG, "GATEWAY in range! Forwarding ${packet.messageId} directly to ${gatewayPeer.deviceAddress}")
            return gatewayPeer
        }

        // Priority 2: Peer with lowest hop distance to gateway
        val bestRelay = activePeers
            .filter { it.role == NodeRole.RELAY }
            .minByOrNull { it.gatewayDistance }

        if (bestRelay != null) {
            Log.d(TAG, "Gradient routing ${packet.messageId} via ${bestRelay.deviceAddress} dist=${bestRelay.gatewayDistance}")
            return bestRelay
        }

        // Priority 3: Any available peer (flood)
        val anyPeer = activePeers.firstOrNull()
        Log.d(TAG, "Flood routing ${packet.messageId} to ${anyPeer?.deviceAddress}")
        return anyPeer
    }

    /**
     * Should this node process/store a received packet, or is it the final hop?
     *
     * Returns true if packet should be handed to MessageRepository (we are gateway).
     * Returns false if packet should be relayed further.
     */
    fun isTerminalNode(): Boolean = localRole == NodeRole.GATEWAY

    /**
     * Should this node relay a received packet?
     */
    fun shouldRelay(packet: RoutablePacket): Boolean {
        if (localRole == NodeRole.GATEWAY) return false  // gateway never relays
        if (packet.hopCount >= packet.ttl) {
            Log.d(TAG, "Packet ${packet.messageId} TTL expired — dropping")
            return false
        }
        return true
    }

    // ── Gateway distance advertised by this node ──────────────────────────────

    /**
     * What gateway distance should this node advertise in its BLE beacon?
     * Gateway = 0, relay with known gateway = 1+, unknown = 255
     */
    fun advertisedGatewayDistance(): Int {
        return when (localRole) {
            NodeRole.GATEWAY  -> 0
            NodeRole.RELAY, NodeRole.SURVIVOR -> {
                knownPeers.values
                    .filter { System.currentTimeMillis() - it.lastSeenMs < PEER_TIMEOUT_MS }
                    .minOfOrNull { it.gatewayDistance + 1 }
                    ?: 255
            }
        }
    }

    // ── Peer summary ──────────────────────────────────────────────────────────

    fun nearbyPeerCount(): Int = knownPeers.values
        .count { System.currentTimeMillis() - it.lastSeenMs < PEER_TIMEOUT_MS }

    fun isGatewayInRange(): Boolean = knownPeers.values
        .any { it.role == NodeRole.GATEWAY && System.currentTimeMillis() - it.lastSeenMs < PEER_TIMEOUT_MS }

    fun getNearbyPeers(): List<PeerInfo> = knownPeers.values
        .filter { System.currentTimeMillis() - it.lastSeenMs < PEER_TIMEOUT_MS }
        .toList()
}

// ── Routable packet interface ─────────────────────────────────────────────────

data class RoutablePacket(
    val messageId:     String,
    val originId:      String,
    val destinationId: String = GATEWAY_BROADCAST_ID,  // null = any gateway
    val hopCount:      Int,
    val ttl:           Int,
    val priority:      Int,
    val payloadJson:   String
)

// ── How to extend for multiple gateways ──────────────────────────────────────
// 1. Replace GATEWAY_BROADCAST_ID with a Set<String> knownGatewayIds
// 2. In selectForwardTarget(), match any peer whose deviceId is in knownGatewayIds
// 3. BleManager.start() should accept a list of gateway IDs from config
// 4. RoutingManager.onPeerDiscovered() already handles any number of GATEWAY peers
