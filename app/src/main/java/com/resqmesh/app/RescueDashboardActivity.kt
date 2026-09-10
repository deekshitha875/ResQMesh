package com.resqmesh.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

// ── Palette ───────────────────────────────────────────────────────────────────
private val DarkBg      = Color(0xFF0A0E1A)
private val CardBg      = Color(0xFF141929)
private val RedAlert    = Color(0xFFE53935)
private val YellowWarn  = Color(0xFFFFA726)
private val GreenSafe   = Color(0xFF4CAF50)
private val BluePrim    = Color(0xFF1565C0)
private val BlueAccent  = Color(0xFF42A5F5)
private val TextWhite   = Color(0xFFECEFF1)
private val TextGray    = Color(0xFF78909C)
private val GlassBg     = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x26FFFFFF)

// ── Entry point ───────────────────────────────────────────────────────────────

class RescueDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RescueDashboardScreen(
                    navController = rememberNavController(),
                    viewModel     = viewModel()
                )
            }
        }
    }
}

// ── Data models ───────────────────────────────────────────────────────────────

enum class Priority { HIGH, MEDIUM, LOW }

data class SosEntry(
    val id: String,
    val senderName: String,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val hopCount: Int,
    val priority: Priority
)

// ── Dashboard ─────────────────────────────────────────────────────────────────

@Composable
fun RescueDashboardScreen(
    navController: NavHostController,
    viewModel: MeshViewModel = viewModel()
) {
    val receivedMessages by viewModel.receivedMessages.collectAsState()
    val sosList = remember(receivedMessages) { receivedMessages.map { it.toSosEntry() } }

    val highCount   = sosList.count { it.priority == Priority.HIGH }
    val mediumCount = sosList.count { it.priority == Priority.MEDIUM }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DarkBg, Color(0xFF0A0F1E), Color(0xFF0D1321)))
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color  = BluePrim.copy(alpha = 0.06f),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.9f, size.height * 0.1f)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D1321), Color(0xFF0A0E1A).copy(alpha = 0f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Rescue Command",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextWhite
                    )
                    Text(
                        text = "SIH 2026 • Disaster Management",
                        fontSize = 12.sp,
                        color = BlueAccent
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(value = sosList.size.toString(), label = "Total SOS",  color = BlueAccent, modifier = Modifier.weight(1f))
                StatCard(value = highCount.toString(),    label = "Critical",   color = RedAlert,   modifier = Modifier.weight(1f))
                StatCard(value = mediumCount.toString(),  label = "Moderate",   color = YellowWarn, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Live SOS Feed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(GlassBg, RoundedCornerShape(8.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(GreenSafe, CircleShape))
                        Text(text = "LIVE", fontSize = 10.sp, color = GreenSafe, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (sosList.isEmpty()) {
                EmptyDashboard()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sosList, key = { it.id }) { entry ->
                        RescueSosCard(
                            entry       = entry,
                            onViewClick = { navController.navigate("detail/${entry.id}") }




                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CardBg, RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, fontSize = 11.sp, color = TextGray)
        }
    }
}

// ── SOS card ──────────────────────────────────────────────────────────────────

@Composable
fun RescueSosCard(entry: SosEntry, onViewClick: () -> Unit) {
    val context = LocalContext.current

    val priorityColor = when (entry.priority) {
        Priority.HIGH   -> RedAlert
        Priority.MEDIUM -> YellowWarn
        Priority.LOW    -> GreenSafe
    }
    val priorityLabel = when (entry.priority) {
        Priority.HIGH   -> "CRITICAL"
        Priority.MEDIUM -> "MODERATE"
        Priority.LOW    -> "LOW"
    }
    val priorityEmoji = when (entry.priority) {
        Priority.HIGH   -> "🔴"
        Priority.MEDIUM -> "🟡"
        Priority.LOW    -> "🟢"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(18.dp))
            .border(1.5.dp, priorityColor.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(80.dp)
                .background(
                    Brush.verticalGradient(listOf(priorityColor, priorityColor.copy(alpha = 0.2f))),
                    RoundedCornerShape(topStart = 18.dp, bottomStart = 4.dp)
                )
                .align(Alignment.CenterStart)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(priorityColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = priorityEmoji, fontSize = 20.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.senderName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Box(
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text  = priorityLabel,
                            fontSize = 9.sp,
                            color = priorityColor,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Text(text = "📍 ${entry.location}", fontSize = 12.sp, color = TextGray)
                Text(text = "🔁 ${entry.hopCount} hops", fontSize = 11.sp, color = BlueAccent)
            }

            // Buttons column
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // VIEW button
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(BluePrim, Color(0xFF0D47A1))),
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onViewClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = "VIEW", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }

                // MAP button — opens device maps app offline
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))),
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val uri = Uri.parse(
                                "geo:${entry.latitude},${entry.longitude}?q=${entry.latitude},${entry.longitude}(SOS+Survivor)"
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = "📍 MAP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyDashboard() {
    Box(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "📡", fontSize = 60.sp)
            Text(text = "Scanning for SOS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text(
                text = "BLE mesh is active and listening.\nAny SOS from survivors will appear here.",
                fontSize = 14.sp,
                color = TextGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Priority dot (legacy) ─────────────────────────────────────────────────────

@Composable
fun PriorityDot(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH   -> RedAlert
        Priority.MEDIUM -> YellowWarn
        Priority.LOW    -> GreenSafe
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}
