package com.resqmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ── Palette (same across all screens) ────────────────────────────────────────
private val DarkBg      = Color(0xFF0A0E1A)
private val CardBg      = Color(0xFF141929)
private val RedPrimary  = Color(0xFFE53935)
private val RedDark     = Color(0xFFB71C1C)
private val BluePrimary = Color(0xFF1565C0)
private val BlueAccent  = Color(0xFF42A5F5)
private val GreenOk     = Color(0xFF4CAF50)
private val TextWhite   = Color(0xFFECEFF1)
private val TextGray    = Color(0xFF78909C)
private val GlassBg     = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x26FFFFFF)

// ── Entry point ───────────────────────────────────────────────────────────────

class SurvivorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SurvivorScreen(viewModel = viewModel())
            }
        }
    }
}

data class SosMessage(val statusIcon: String, val text: String)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SurvivorScreen(viewModel: MeshViewModel = viewModel()) {

    val locationStatus by viewModel.locationStatus.collectAsState()
    val nearbyRelays   by viewModel.nearbyRelays.collectAsState()
    val messages       by viewModel.sentMessages.collectAsState()
    val sosSending     by viewModel.sosSending.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DarkBg, Color(0xFF0A0F1E), Color(0xFF0D1321)))
            )
    ) {
        // Background decorative blob
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color  = RedPrimary.copy(alpha = 0.07f),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.5f, size.height * 0.25f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SOS Mode",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextWhite
                    )
                    Text(
                        text = "Survivor • BLE Mesh Active",
                        fontSize = 13.sp,
                        color = BlueAccent
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(GlassBg, RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = BlueAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── SOS button ────────────────────────────────────────────────────
            PulsingSosButton(
                isSending = sosSending,
                onClick   = { viewModel.sendSos(senderName = android.os.Build.MODEL) }
            )

            // ── Status cards ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusChip(
                    icon    = "📍",
                    label   = "Location",
                    value   = if (locationStatus.startsWith("Acq")) "Acquiring" else "Active",
                    color   = if (locationStatus.startsWith("Acq")) Color(0xFFFFA726) else GreenOk,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(
                    icon    = "📡",
                    label   = "Relays",
                    value   = nearbyRelays.toString(),
                    color   = if (nearbyRelays > 0) GreenOk else Color(0xFFFFA726),
                    modifier = Modifier.weight(1f)
                )
            }

            // GPS coordinates card
            if (!locationStatus.startsWith("Acq")) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = GreenOk,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text  = "Your Coordinates",
                                fontSize = 11.sp,
                                color = TextGray
                            )
                            Text(
                                text  = locationStatus,
                                fontSize = 13.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Sent messages ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transmission Log",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text  = "${messages.size} sent",
                        fontSize = 12.sp,
                        color = TextGray
                    )
                }

                if (messages.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = "No SOS sent yet\nTap the button above when you need help",
                                color = TextGray,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    messages.forEach { message ->
                        SosLogRow(message = message)
                    }
                }
            }
        }
    }
}

// ── Pulsing SOS Button ────────────────────────────────────────────────────────

@Composable
fun PulsingSosButton(isSending: Boolean, onClick: () -> Unit) {

    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center) {

        // Outer pulse ring
        if (!isSending) {
            Box(
                modifier = Modifier
                    .size((180 * pulseScale).dp)
                    .background(
                        color  = RedPrimary.copy(alpha = ringAlpha * 0.3f),
                        shape  = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .background(
                        color  = RedPrimary.copy(alpha = 0.15f),
                        shape  = CircleShape
                    )
            )
        }

        // Main button
        Button(
            onClick  = { if (!isSending) onClick() },
            enabled  = !isSending,
            modifier = Modifier.size(150.dp),
            shape    = CircleShape,
            colors   = ButtonDefaults.buttonColors(
                containerColor        = RedPrimary,
                disabledContainerColor = RedDark
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp),
            border = BorderStroke(
                width = 3.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)
                )
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color  = Color.White,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "SENDING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                } else {
                    Text(text = "🆘", fontSize = 36.sp)
                    Text(
                        text = "SOS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// ── Status chip ───────────────────────────────────────────────────────────────

@Composable
fun StatusChip(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(CardBg, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = icon, fontSize = 14.sp)
                Text(text = label, fontSize = 11.sp, color = TextGray)
            }
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ── SOS log row ───────────────────────────────────────────────────────────────

@Composable
fun SosLogRow(message: SosMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(GlassBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = message.statusIcon, fontSize = 18.sp)
            }
            Column {
                Text(
                    text  = message.text,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text  = "Via BLE mesh relay",
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ── Glass card ────────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(GlassBg, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}
