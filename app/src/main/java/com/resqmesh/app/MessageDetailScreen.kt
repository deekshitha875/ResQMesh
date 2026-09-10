package com.resqmesh.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Palette ───────────────────────────────────────────────────────────────────
private val DarkBg      = Color(0xFF0A0E1A)
private val CardBg      = Color(0xFF141929)
private val RedAlert    = Color(0xFFE53935)
private val YellowWarn  = Color(0xFFFFA726)
private val GreenSafe   = Color(0xFF4CAF50)
private val BlueAccent  = Color(0xFF42A5F5)
private val TextWhite   = Color(0xFFECEFF1)
private val TextGray    = Color(0xFF78909C)
private val GlassBg     = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x26FFFFFF)

// ── Data models ───────────────────────────────────────────────────────────────

enum class DeliveryStatus { DELIVERED, IN_TRANSIT, FAILED }

data class MessageDetail(
    val messageId: String,
    val senderId: String,
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMs: Long,
    val priority: Priority,
    val hopCount: Int,
    val deliveryStatus: DeliveryStatus,
    val messageText: String
)

private val dummyMessageDetail = MessageDetail(
    messageId        = "msg_001",
    senderId         = "DEVICE-A3F9",
    latitude         = 28.6139,
    longitude        = 77.2090,
    timestampEpochMs = 1_720_000_800_000L,
    priority         = Priority.HIGH,
    hopCount         = 1,
    deliveryStatus   = DeliveryStatus.DELIVERED,
    messageText      = "Trapped under rubble near Connaught Place. Need urgent rescue. 3 people injured."
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun MessageDetailScreen(
    messageId: String,
    viewModel: MeshViewModel = viewModel()
) {
    var detail by remember { mutableStateOf<MessageDetail?>(null) }

    LaunchedEffect(messageId) {
        val msg = viewModel.getMessageById(messageId)
        detail = msg?.toDetail() ?: dummyMessageDetail
    }

    val d = detail ?: dummyMessageDetail

    val priorityColor = when (d.priority) {
        Priority.HIGH   -> RedAlert
        Priority.MEDIUM -> YellowWarn
        Priority.LOW    -> GreenSafe
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF0A0F1E))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color  = priorityColor.copy(alpha = 0.06f),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.9f, 0f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Text(
                text = "SOS Detail",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite
            )

            // ── Priority hero banner ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(priorityColor, priorityColor.copy(alpha = 0.6f))
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val emoji = when (d.priority) {
                        Priority.HIGH   -> "🚨"
                        Priority.MEDIUM -> "⚠️"
                        Priority.LOW    -> "ℹ️"
                    }
                    Text(text = emoji, fontSize = 36.sp)
                    Column {
                        Text(
                            text = when (d.priority) {
                                Priority.HIGH   -> "CRITICAL EMERGENCY"
                                Priority.MEDIUM -> "MODERATE PRIORITY"
                                Priority.LOW    -> "LOW PRIORITY"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Immediate attention required",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Message body ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(18.dp))
                    .border(1.dp, priorityColor.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💬  Message",
                    fontSize = 12.sp,
                    color = TextGray,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text  = d.messageText,
                    fontSize = 16.sp,
                    color = TextWhite,
                    lineHeight = 24.sp
                )
            }

            // ── Location card ─────────────────────────────────────────────────
            DetailSection(title = "📍  Location") {
                DetailInfoRow(label = "Latitude",  value = "%.6f°".format(d.latitude))
                DetailInfoRow(label = "Longitude", value = "%.6f°".format(d.longitude))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF1B5E20).copy(alpha = 0.2f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, GreenSafe.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text  = "${d.latitude}, ${d.longitude}",
                        fontSize = 12.sp,
                        color = GreenSafe,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Transmission card ─────────────────────────────────────────────
            DetailSection(title = "📡  Transmission") {
                DetailInfoRow(label = "Message ID", value = d.messageId)
                DetailInfoRow(label = "Sender ID",  value = d.senderId)
                DetailInfoRow(label = "Hop Count",  value = "${d.hopCount} hops")
                DetailInfoRow(
                    label = "Delivery",
                    value = d.deliveryStatus.label(),
                    valueColor = when (d.deliveryStatus) {
                        DeliveryStatus.DELIVERED  -> GreenSafe
                        DeliveryStatus.IN_TRANSIT -> YellowWarn
                        DeliveryStatus.FAILED     -> RedAlert
                    }
                )
                DetailInfoRow(label = "Timestamp", value = d.timestampEpochMs.toFormattedDateTime())
            }
        }
    }
}

// ── Detail section ────────────────────────────────────────────────────────────

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = TextGray,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        content()
    }
}

// ── Info row ──────────────────────────────────────────────────────────────────

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    valueColor: Color = TextWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            fontSize = 13.sp,
            color = TextGray,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text  = value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// ── Legacy composables (referenced by other files) ────────────────────────────

@Composable
fun PriorityBadge(priority: Priority) {
    val (bg, label) = when (priority) {
        Priority.HIGH   -> RedAlert   to "HIGH PRIORITY"
        Priority.MEDIUM -> YellowWarn to "MEDIUM PRIORITY"
        Priority.LOW    -> GreenSafe  to "LOW PRIORITY"
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextGray, modifier = Modifier.weight(0.38f))
        Text(text = value, fontSize = 14.sp, color = TextWhite, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun MessageBodySection(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Message", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextGray)
        Box(modifier = Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text(text = text, fontSize = 15.sp, color = TextWhite, lineHeight = 22.sp)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.toFormattedDateTime(): String {
    val formatter = DateTimeFormatter
        .ofPattern("dd MMM yyyy  HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}

private fun DeliveryStatus.label(): String = when (this) {
    DeliveryStatus.DELIVERED  -> "✓ Delivered"
    DeliveryStatus.IN_TRANSIT -> "⏳ In Transit"
    DeliveryStatus.FAILED     -> "✗ Failed"
}
