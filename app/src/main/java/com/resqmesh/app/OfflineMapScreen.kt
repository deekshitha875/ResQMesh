package com.resqmesh.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg    = Color(0xFF0A0E1A)
private val CardBg    = Color(0xFF141929)
private val GreenSafe = Color(0xFF4CAF50)
private val TextWhite = Color(0xFFECEFF1)
private val TextGray  = Color(0xFF78909C)

/**
 * OfflineMapScreen
 *
 * Shown when the rescue team taps "MAP" on a SOS card.
 * Since the app targets offline-first operation, we display the coordinates
 * prominently and offer a deep-link button that opens whatever maps app is
 * installed on the device (works with offline-cached Google Maps, OsmAnd, etc.).
 *
 * Route: "map/{lat}/{lon}"  (wired in HomeActivity.AppNavHost)
 */
@Composable
fun OfflineMapScreen(latitude: Double, longitude: Double) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBg, Color(0xFF0D1321)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            Text(text = "📍", fontSize = 64.sp)

            Text(
                text = "Survivor Location",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            // Coordinate card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(18.dp))
                    .border(1.dp, GreenSafe.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoordRow(label = "Latitude",  value = "%.6f°".format(latitude))
                CoordRow(label = "Longitude", value = "%.6f°".format(longitude))
            }

            Text(
                text = "Use the button below to open in your offline map app\n(Google Maps offline cache, OsmAnd, etc.)",
                fontSize = 13.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Open in maps button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))),
                        RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        val uri = Uri.parse(
                            "geo:$latitude,$longitude?q=$latitude,$longitude(SOS+Survivor)"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📍  Open in Maps App",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun CoordRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = TextGray, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 14.sp, color = GreenSafe, fontWeight = FontWeight.Bold)
    }
}
