package com.resqmesh.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── About screen ──────────────────────────────────────────────────────────────

/**
 * AboutScreen
 *
 * Static informational screen — no state, no navigation arguments.
 * Mount this on the "about" route in your root NavHost:
 *
 *   composable("about") { AboutScreen() }
 */
@Composable
fun AboutScreen() {
    val dividerColor = Color(0x33FFFFFF)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // ── App identity ──────────────────────────────────────────────────────
        AppIdentitySection()

        Divider(color = dividerColor)

        // ── App description ───────────────────────────────────────────────────
        AppDescriptionSection()

        Divider(color = dividerColor)

        // ── Hackathon metadata ────────────────────────────────────────────────
        HackathonMetadataSection()

        Divider(color = dividerColor)

        // ── How it works ──────────────────────────────────────────────────────
        HowItWorksSection()

        Divider(color = dividerColor)

        // ── Team details ──────────────────────────────────────────────────────
        TeamSection()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── App identity ──────────────────────────────────────────────────────────────

@Composable
private fun AppIdentitySection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // App title — large, bold, brand red
        Text(
            text = "ResQMesh",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFD32F2F),
            textAlign = TextAlign.Center
        )
        // Tagline
        Text(
            text = "Offline Emergency Mesh Network",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1565C0),
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
    }
}

// ── App description ───────────────────────────────────────────────────────────

@Composable
private fun AppDescriptionSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeading(text = "About the App")
        Text(
            text = "ResQMesh is a Bluetooth Low Energy–based offline mesh communication " +
                    "platform designed for disaster scenarios where cellular infrastructure " +
                    "is unavailable or destroyed. Survivors can broadcast SOS alerts that " +
                    "hop peer-to-peer across nearby Android devices until they reach an " +
                    "active rescue team — no internet, no cell towers required.",
            fontSize = 14.sp,
            color = Color(0xFFB0BEC5),
            lineHeight = 21.sp
        )
    }
}

// ── Hackathon metadata ────────────────────────────────────────────────────────

@Composable
private fun HackathonMetadataSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeading(text = "Hackathon Details")
        MetadataRow(label = "Event",          value = "Smart India Hackathon 2026")
        MetadataRow(label = "Problem ID",     value = "SIH26206")
        MetadataRow(label = "Theme",          value = "Disaster Management")
        MetadataRow(label = "Problem Title",  value = "Offline Mesh Communication for Disaster Relief")
    }
}

// ── How it works ──────────────────────────────────────────────────────────────

private data class WorkflowStep(val number: Int, val description: String)

private val HOW_IT_WORKS_STEPS = listOf(
    WorkflowStep(1, "Survivor opens ResQMesh and taps SEND SOS. The device broadcasts an encrypted SOS packet over Bluetooth Low Energy."),
    WorkflowStep(2, "Nearby phones with ResQMesh installed automatically detect and re-broadcast the packet, forming a relay chain that extends the signal range hop by hop."),
    WorkflowStep(3, "The rescue team's dashboard receives the relayed SOS, showing the survivor's GPS coordinates, priority level, and hop count for rapid triage.")
)

@Composable
private fun HowItWorksSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeading(text = "How It Works")
        HOW_IT_WORKS_STEPS.forEach { step ->
            WorkflowStepRow(step = step)
        }
    }
}

@Composable
private fun WorkflowStepRow(step: WorkflowStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Numbered circle badge
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    color = Color(0xFF1565C0),
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.number.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = step.description,
            fontSize = 14.sp,
            color = Color(0xFFB0BEC5),
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Team section ──────────────────────────────────────────────────────────────

@Composable
private fun TeamSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeading(text = "Our Team")
        MetadataRow(label = "Team Name", value = "OG")
        MetadataRow(label = "Team ID",   value = "team 145")
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

/** Bold section heading used across all sections. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1565C0)
    )
}

/** Two-column label / value row used for metadata and team details. */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF78909C),
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color(0xFFECEFF1),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
