package com.resqmesh.app

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MeshViewModel(app: Application) : AndroidViewModel(app) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(app)
    private val bleManager  = BleManager(app)

    init {
        // Prune old messages from previous sessions
        MessageStore.pruneOld()
    }

    // ── Exposed state ─────────────────────────────────────────────────────────

    /** Live list of all received SOS messages — drives RescueDashboardScreen. */
    val receivedMessages: StateFlow<List<MeshMessage>> =
        MessageStore.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _locationStatus = MutableStateFlow("Acquiring…")
    val locationStatus: StateFlow<String> = _locationStatus.asStateFlow()

    private val _nearbyRelays = MutableStateFlow(0)
    val nearbyRelays: StateFlow<Int> = _nearbyRelays.asStateFlow()

    private val _sentMessages = MutableStateFlow<List<SosMessage>>(emptyList())
    val sentMessages: StateFlow<List<SosMessage>> = _sentMessages.asStateFlow()

    private val _sosSending = MutableStateFlow(false)
    val sosSending: StateFlow<Boolean> = _sosSending.asStateFlow()

    /** Message IDs that received an ACK back from the gateway. */
    private val _ackedIds = MutableStateFlow<Set<String>>(emptySet())
    val ackedIds: StateFlow<Set<String>> = _ackedIds.asStateFlow()

    // ── GPS ───────────────────────────────────────────────────────────────────

    private var lastLat = 0.0
    private var lastLon = 0.0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLat = loc.latitude
            lastLon = loc.longitude
            _locationStatus.value =
                "${"%.5f".format(lastLat)}° N, ${"%.5f".format(lastLon)}° E"
        }
    }

    // ── Start mesh ────────────────────────────────────────────────────────────

    /**
     * Call this when the user picks a role on HomeScreen.
     *
     * [asGateway] = true  → Rescue Team phone (GATEWAY: sinks SOS, sends ACK)
     * [asGateway] = false → Survivor / bystander phone (originates or relays SOS)
     *
     * Relay chain:
     *   Survivor --BLE hop--> Relay1 --BLE hop--> ... --BLE hop--> Gateway (rescue team)
     */
    @SuppressLint("MissingPermission")
    fun startMesh(asGateway: Boolean) {

        // Start GPS for SOS coordinate attachment
        val req = LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5_000L
        ).setMinUpdateIntervalMillis(2_000L).build()
        fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())

        // Set role BEFORE start() so BleManager advertises the right role byte
        if (asGateway) bleManager.setAsGateway() else bleManager.setAsCitizenRelay()

        bleManager.start()

        // Reflect nearby peer count in UI
        viewModelScope.launch {
            bleManager.nearbyPeerCount.collect { _nearbyRelays.value = it }
        }

        // Every SOS that arrives over BLE is persisted — this drives the
        // RescueDashboard live feed on the gateway device automatically.
        viewModelScope.launch {
            bleManager.incomingMessages.collect { msg ->
                MessageStore.upsert(msg)
            }
        }

        // ACK from gateway → update survivor's sent-message log to "Delivered"
        viewModelScope.launch {
            bleManager.deliveryAcks.collect { msgId ->
                _ackedIds.value = _ackedIds.value + msgId
                _sentMessages.value = _sentMessages.value.map { sos ->
                    if (sos.text.contains(msgId.takeLast(8))) {
                        SosMessage("✅", "SOS Delivered to Rescue Team!")
                    } else sos
                }
            }
        }
    }

    fun stopMesh() {
        fusedClient.removeLocationUpdates(locationCallback)
        bleManager.stop()
    }

    // ── Send SOS ──────────────────────────────────────────────────────────────

    /**
     * Originate a new SOS from this survivor device.
     * The packet starts at hopCount=0 and each relay increments it toward the gateway.
     */
    fun sendSos(senderName: String) {
        if (_sosSending.value) return
        viewModelScope.launch {
            _sosSending.value = true

            val now = System.currentTimeMillis()
            val id  = UUID.randomUUID().toString()

            val packet = MeshMessage(
                id                = id,
                senderName        = senderName,
                senderId          = android.os.Build.MODEL,
                latitude          = lastLat,
                longitude         = lastLon,
                messageText       = "SOS — I need rescue! GPS: ${"%.5f".format(lastLat)}, ${"%.5f".format(lastLon)}",
                priority          = "HIGH",
                hopCount          = 0,
                timestampEpochMs  = now,
                receivedAtEpochMs = now,
                deliveryStatus    = "IN_TRANSIT"
            )

            // Store locally so survivor can see their own sent SOS
            MessageStore.upsert(packet)

            // Hand to BLE layer — it will advertise and relay hops to gateway
            bleManager.broadcastSos(packet)

            _sentMessages.value = listOf(
                SosMessage("⏳", "SOS Sent — relaying to rescue… (ID: …${id.takeLast(8)})")
            ) + _sentMessages.value

            _sosSending.value = false
        }
    }

    suspend fun getMessageById(id: String): MeshMessage? = MessageStore.getById(id)

    override fun onCleared() {
        super.onCleared()
        stopMesh()
    }
}
