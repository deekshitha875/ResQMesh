package com.resqmesh.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BleManager — BLE mesh transport layer
 *
 * Roles:
 *  GATEWAY (rescue team phone) — scans + GATT-serves, sinks SOS, emits ACK
 *  RELAY   (bystander phone)   — scans + GATT-serves, re-advertises packets
 *  SURVIVOR (victim phone)     — originates SOS, also relays if not gateway
 *
 * Relay chain:
 *  [Survivor] --BLE--> [Relay1] --BLE--> [Relay2] --...-- [Gateway]
 *
 * Each hop: scanner sees advertisement → connectGatt → readCharacteristic →
 *           handleIncomingBytes → if relay: update payload + re-advertise
 *
 * MAX_HOPS is set high (50) so a 2 km chain of bystander phones works fine.
 * Deduplication (seenIds) ensures each node only relays a given packet ONCE.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CHAR_UUID: UUID    = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        // 50 hops × ~50 m/hop ≈ 2.5 km effective range through a crowd
        private const val MAX_HOPS = 50
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Flows exposed to ViewModel ────────────────────────────────────────────
    private val _incomingMessages = MutableSharedFlow<MeshMessage>(replay = 0, extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MeshMessage> = _incomingMessages

    private val _nearbyPeerCount = MutableStateFlow(0)
    val nearbyPeerCount: StateFlow<Int> = _nearbyPeerCount

    private val _deliveryAcks = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 32)
    val deliveryAcks: SharedFlow<String> = _deliveryAcks

    // ── Node role ─────────────────────────────────────────────────────────────
    private var isGateway = false

    fun setAsGateway() {
        isGateway = true
        Log.i(TAG, "Role → RESCUE_GATEWAY")
    }

    fun setAsCitizenRelay() {
        isGateway = false
        Log.i(TAG, "Role → CITIZEN_RELAY / SURVIVOR")
    }

    // ── RoutingManager — wired in for smart gradient routing ──────────────────
    private lateinit var routingManager: RoutingManager

    // ── Deduplication ─────────────────────────────────────────────────────────
    // seenIds tracks packet UUIDs this node has ALREADY relayed.
    // A packet is relayed at most ONCE per node — prevents exponential flooding.
    private val seenIds = LinkedHashSet<String>(1024)
    private val seenIdsLock = Any()

    /**
     * Returns true if this is the first time we've seen [id] — safe to relay.
     * Returns false if we've already processed it — drop silently.
     */
    private fun markSeenAndCheck(id: String): Boolean {
        synchronized(seenIdsLock) {
            if (seenIds.contains(id)) return false
            // Evict oldest entry when cache is full
            if (seenIds.size >= 1024) seenIds.iterator().apply { next(); remove() }
            seenIds.add(id)
            return true
        }
    }

    // ── BLE primitives ────────────────────────────────────────────────────────
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var gattServer: BluetoothGattServer? = null
    private var leScanner: BluetoothLeScanner? = null

    // address → (isGateway, lastSeenMs)
    private val visiblePeers = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    // The bytes currently being advertised via GATT characteristic
    private var currentPayload: ByteArray? = null

    // ── GATT server — serves currentPayload to any connecting scanner ─────────

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val payload = currentPayload
            if (characteristic.uuid == CHAR_UUID && payload != null) {
                val slice = if (offset < payload.size)
                    payload.copyOfRange(offset, payload.size)
                else
                    byteArrayOf()
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    // ── BLE scan — discovers peers advertising SERVICE_UUID ───────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val uuids   = result.scanRecord?.serviceUuids
            if (uuids?.any { it.uuid == SERVICE_UUID } != true) return

            // Read role byte from manufacturer data (0x01 = gateway, 0x00 = relay)
            val mfgData       = result.scanRecord?.getManufacturerSpecificData(0x0059)
            val peerIsGateway = mfgData?.isNotEmpty() == true && mfgData[0] == 0x01.toByte()

            // Read gateway-distance byte from manufacturer data index 1 (0xFF = unknown)
            val peerGwDist    = if (mfgData != null && mfgData.size >= 2)
                (mfgData[1].toInt() and 0xFF) else 255

            val isNew = !visiblePeers.containsKey(address)
            visiblePeers[address] = Pair(peerIsGateway, System.currentTimeMillis())

            // Feed into RoutingManager for smart next-hop selection
            if (::routingManager.isInitialized) {
                routingManager.onPeerDiscovered(
                    PeerInfo(
                        deviceAddress    = address,
                        role             = when {
                            peerIsGateway -> NodeRole.GATEWAY
                            else          -> NodeRole.RELAY
                        },
                        gatewayDistance  = peerGwDist
                    )
                )
            }

            if (isNew) {
                _nearbyPeerCount.value = visiblePeers.size
                Log.d(TAG, "New peer $address isGW=$peerIsGateway dist=$peerGwDist total=${visiblePeers.size}")
            }

            // Connect and pull whatever packet it's currently advertising
            connectAndRead(result.device)
        }

        override fun onScanFailed(errorCode: Int) { Log.e(TAG, "Scan failed: $errorCode") }
    }

    // ── GATT client — connects to a peer and reads its characteristic ─────────

    private fun connectAndRead(device: BluetoothDevice) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
                else gatt.close()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                gatt.getService(SERVICE_UUID)
                    ?.getCharacteristic(CHAR_UUID)
                    ?.let { gatt.readCharacteristic(it) }
                    ?: gatt.disconnect()
            }

            // API 33+ override — receives value directly
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                    handleIncomingBytes(value, gatt.device.address)
                }
                gatt.disconnect()
            }

            // API < 33 fallback — reads value from the characteristic object
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val bytes = characteristic.value
                        if (bytes != null && bytes.isNotEmpty()) {
                            handleIncomingBytes(bytes, gatt.device.address)
                        }
                    }
                    gatt.disconnect()
                }
            }

        }, BluetoothDevice.TRANSPORT_LE)
    }

    // ── Packet handling ───────────────────────────────────────────────────────

    private fun handleIncomingBytes(bytes: ByteArray, fromAddress: String) {
        try {
            val json    = JSONObject(String(bytes, Charsets.UTF_8))
            val msgType = json.optString("type", "SOS")

            // ── ACK packet ────────────────────────────────────────────────────
            if (msgType == "ACK") {
                val ackedId = json.optString("ackedId", "")
                if (ackedId.isNotEmpty()) {
                    Log.i(TAG, "ACK received for $ackedId")
                    scope.launch { _deliveryAcks.emit(ackedId) }
                    // Relay the ACK backwards toward the survivor (same dedup logic)
                    if (markSeenAndCheck("ACK_$ackedId")) {
                        relayAck(ackedId)
                    }
                }
                return
            }

            // ── SOS packet ────────────────────────────────────────────────────
            val id       = json.getString("id")
            val hopCount = json.getInt("hopCount")

            // Drop if already processed OR TTL exceeded
            if (!markSeenAndCheck(id)) {
                Log.d(TAG, "Dropping already-relayed packet $id")
                return
            }
            if (hopCount > MAX_HOPS) {
                Log.d(TAG, "Dropping TTL-expired packet $id hopCount=$hopCount")
                return
            }

            val priority = when {
                hopCount <= 2  -> "HIGH"
                hopCount <= 10 -> "MEDIUM"
                else           -> "LOW"
            }

            val msg = MeshMessage(
                id                = id,
                senderName        = json.optString("senderName", fromAddress.takeLast(5)),
                senderId          = fromAddress,
                latitude          = json.optDouble("latitude", 0.0),
                longitude         = json.optDouble("longitude", 0.0),
                messageText       = json.optString("messageText", "SOS"),
                priority          = priority,
                hopCount          = hopCount,
                timestampEpochMs  = json.optLong("timestampEpochMs", System.currentTimeMillis()),
                receivedAtEpochMs = System.currentTimeMillis(),
                deliveryStatus    = if (isGateway) "DELIVERED" else "IN_TRANSIT"
            )

            Log.i(TAG, "SOS from ${msg.senderName} hop=$hopCount isGW=$isGateway")
            scope.launch { _incomingMessages.emit(msg) }

            if (isGateway) {
                // Terminal node — confirm receipt with ACK
                if (markSeenAndCheck("ACK_$id")) {
                    broadcastAck(id)
                }
            } else {
                // Relay node — forward packet one hop further toward gateway
                relay(json, hopCount + 1)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Packet parse error: ${e.message}")
        }
    }

    // ── Relay: re-advertise the packet with incremented hop count ─────────────

    private fun relay(original: JSONObject, newHopCount: Int) {
        original.put("hopCount", newHopCount)
        val bytes = original.toString().toByteArray(Charsets.UTF_8)
        setPayloadAndAdvertise(bytes)
        Log.d(TAG, "Relayed SOS hopCount=$newHopCount")
    }

    // ── ACK relay: flood ACK back toward survivor ─────────────────────────────

    private fun relayAck(messageId: String) {
        val ack = JSONObject().apply {
            put("type",    "ACK")
            put("ackedId", messageId)
        }.toString().toByteArray(Charsets.UTF_8)
        setPayloadAndAdvertise(ack)
        Log.d(TAG, "Relayed ACK for $messageId")
    }

    private fun broadcastAck(messageId: String) {
        val ack = JSONObject().apply {
            put("type",    "ACK")
            put("ackedId", messageId)
        }.toString().toByteArray(Charsets.UTF_8)
        setPayloadAndAdvertise(ack)
        Log.i(TAG, "ACK broadcast for $messageId")
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Originate and broadcast a new SOS from this device (survivor role).
     * Marks the packet as seen so we don't relay our own packet back to ourselves.
     */
    fun broadcastSos(msg: MeshMessage) {
        markSeenAndCheck(msg.id) // mark so we don't process our own rebroadcast
        val json = JSONObject().apply {
            put("type",             "SOS")
            put("id",               msg.id)
            put("senderName",       msg.senderName)
            put("senderId",         msg.senderId)
            put("latitude",         msg.latitude)
            put("longitude",        msg.longitude)
            put("messageText",      msg.messageText)
            put("priority",         msg.priority)
            put("hopCount",         0)
            put("timestampEpochMs", msg.timestampEpochMs)
        }.toString().toByteArray(Charsets.UTF_8)

        setPayloadAndAdvertise(json)
        Log.i(TAG, "SOS originated for ${msg.id}")
    }

    // ── BLE advertise ─────────────────────────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(s: AdvertiseSettings) { Log.d(TAG, "Advertising OK") }
        override fun onStartFailure(e: Int) { Log.e(TAG, "Advertising failed: $e") }
    }

    private fun setPayloadAndAdvertise(bytes: ByteArray) {
        currentPayload = bytes
        gattServer?.getService(SERVICE_UUID)
            ?.getCharacteristic(CHAR_UUID)
            ?.value = bytes
        advertise()
    }

    private fun advertise() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        try { advertiser.stopAdvertising(advertiseCallback) } catch (_: Exception) {}

        // mfg data: byte[0] = role (0x01=gateway, 0x00=relay)
        //           byte[1] = gateway distance (0=gateway, 1..N=relay hops to GW, 0xFF=unknown)
        val gwDist: Int = if (::routingManager.isInitialized)
            routingManager.advertisedGatewayDistance().coerceAtMost(254)
        else if (isGateway) 0 else 255

        val roleData = byteArrayOf(
            if (isGateway) 0x01 else 0x00,
            gwDist.toByte()
        )

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addManufacturerData(0x0059, roleData)
            .setIncludeDeviceName(false)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        val btAdapter = adapter ?: run { Log.e(TAG, "No BT adapter"); return }

        // Wire RoutingManager
        val localId   = android.os.Build.MODEL
        val localRole = if (isGateway) NodeRole.GATEWAY else NodeRole.RELAY
        routingManager = RoutingManager(localRole, localId)

        startGattServer()
        leScanner = btAdapter.bluetoothLeScanner

        val filter   = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner?.startScan(listOf(filter), settings, scanCallback)
        Log.i(TAG, "BLE mesh started — role: ${if (isGateway) "GATEWAY" else "RELAY/SURVIVOR"}")

        // If we are the gateway, start advertising our beacon immediately
        // so survivors/relays can detect us and do gradient routing toward us.
        if (isGateway) advertiseGatewayBeacon()
    }

    /** Gateway continuously advertises a zero-hop-distance beacon so nearby
     *  relays can learn they are close to the gateway and prefer forwarding toward it. */
    private fun advertiseGatewayBeacon() {
        val beacon = JSONObject().apply {
            put("type", "BEACON")
        }.toString().toByteArray(Charsets.UTF_8)
        setPayloadAndAdvertise(beacon)
        Log.i(TAG, "Gateway beacon started")
    }

    private fun startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val char = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(char)
        gattServer?.addService(service)
    }

    fun stop() {
        try { leScanner?.stopScan(scanCallback) }           catch (_: Exception) {}
        try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        try { gattServer?.close() }                         catch (_: Exception) {}
        Log.i(TAG, "BLE mesh stopped")
    }
}
