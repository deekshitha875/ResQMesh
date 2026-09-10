package com.resqmesh.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.navigation.NavHostController

// ── Required permissions ──────────────────────────────────────────────────────
//
// BLUETOOTH_SCAN / BLUETOOTH_ADVERTISE / BLUETOOTH_CONNECT were introduced in
// Android 12 (API 31).  On API ≤ 30 the older BLUETOOTH / BLUETOOTH_ADMIN
// permissions (declared in the manifest as normal permissions) cover the same
// functionality and do NOT require a runtime request — so we only add the new
// ones to the runtime list when running on API 31+.
//
// ACCESS_FINE_LOCATION is required on all API levels for BLE scanning.

private val REQUIRED_PERMISSIONS: Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {   // API 31+
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

// ── Per-permission explanations shown to the user ─────────────────────────────

data class PermissionInfo(val name: String, val reason: String)

private val PERMISSION_EXPLANATIONS = listOf(
    PermissionInfo(
        name   = "Bluetooth Scan",
        reason = "Discovers nearby devices in the BLE mesh network so your SOS can be relayed."
    ),
    PermissionInfo(
        name   = "Bluetooth Advertise",
        reason = "Broadcasts your device as a mesh relay node so others can reach you."
    ),
    PermissionInfo(
        name   = "Bluetooth Connect",
        reason = "Opens connections to nearby relay devices for two-way SOS message delivery."
    ),
    PermissionInfo(
        name   = "Precise Location",
        reason = "Required by Android to perform BLE scanning and attaches GPS coordinates to your SOS."
    )
)

// ── Screen composable ─────────────────────────────────────────────────────────

/**
 * PermissionsScreen
 *
 * Shown on first launch before any other screen. Requests all permissions
 * needed for the BLE mesh. Navigates to "home" once every permission is
 * granted, or shows a denial message with a retry path.
 *
 * Wire this into your root NavHost as the start destination, then replace it
 * with "home" after permissions have been stored (see TODO below).
 */
@Composable
fun PermissionsScreen(navController: NavHostController) {

    val context = LocalContext.current

    // ── PERMISSION STATE ─────────────────────────────────────────────────────
    // allGranted  → true when every permission in REQUIRED_PERMISSIONS is held
    // anyDenied   → true after at least one request has come back denied
    //
    // TODO: Persist the granted state (e.g. DataStore / SharedPreferences) so
    //   you can skip this screen on subsequent launches and go straight to
    //   "home" when all permissions are already held.
    var allGranted by remember {
        mutableStateOf(REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PermissionChecker.PERMISSION_GRANTED
        })
    }
    var anyDenied by remember { mutableStateOf(false) }
    // ─────────────────────────────────────────────────────────────────────────

    // ── PERMISSION LAUNCHER ──────────────────────────────────────────────────
    // rememberLauncherForActivityResult fires the system permission dialog for
    // all requested permissions at once and returns a Map<String, Boolean>.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        allGranted = granted
        anyDenied  = !granted
    }
    // ─────────────────────────────────────────────────────────────────────────

    // Auto-navigate as soon as all permissions are confirmed granted
    LaunchedEffect(allGranted) {
        if (allGranted) {
            // TODO: Also write a "permissions_granted = true" flag to
            //   DataStore here so the next cold start skips this screen.
            navController.navigate("home") {
                // Remove PermissionsScreen from the back stack so the user
                // can't navigate back to it after reaching home.
                popUpTo("permissions") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top)
    ) {

        // Icon
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = "Bluetooth mesh",
            tint = Color(0xFF1565C0),
            modifier = Modifier.size(72.dp)
        )

        // Title
        Text(
            text = "Permissions Required",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Intro paragraph
        Text(
            text = "ResQMesh uses Bluetooth Low Energy to form an offline mesh network. " +
                    "These permissions are essential — without them the app cannot relay SOS messages.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Divider(color = Color.LightGray)

        // Per-permission explanation rows
        // On API ≤ 30 the three BLE permissions are not requested at runtime,
        // so we only show explanations for permissions that are actually in
        // REQUIRED_PERMISSIONS (plus Location, which is always needed).
        val visibleExplanations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PERMISSION_EXPLANATIONS          // all four
        } else {
            PERMISSION_EXPLANATIONS.takeLast(1)   // Location only
        }

        visibleExplanations.forEach { info ->
            PermissionExplanationRow(info = info)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── DENIED STATE ─────────────────────────────────────────────────────
        if (anyDenied) {
            DenialMessage()
        }

        // ── PRIMARY ACTION BUTTON ─────────────────────────────────────────────
        // Label changes to "Try Again" after a denial so the user understands
        // they can re-attempt without hunting through system settings.
        Button(
            onClick = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (anyDenied) Color(0xFF757575) else Color(0xFF1565C0)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = if (anyDenied) "Try Again" else "Grant Permissions",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // Note: if the user has ticked "Don't ask again", Android will not show
        // the dialog on subsequent launches. In that case you should detect
        // shouldShowRequestPermissionRationale() == false after a denial and
        // direct the user to Settings.
        // TODO: Add a "Open Settings" button when permanent denial is detected.
    }
}

// ── Permission explanation row ────────────────────────────────────────────────

@Composable
fun PermissionExplanationRow(info: PermissionInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "•", fontSize = 16.sp, color = Color(0xFF1565C0))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = info.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            Text(
                text = info.reason,
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Denial message ────────────────────────────────────────────────────────────

@Composable
fun DenialMessage() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEBEE),          // light red background
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp
    ) {
        Text(
            text = "One or more permissions were denied. ResQMesh cannot discover " +
                    "or relay SOS messages without them. Please grant all permissions " +
                    "to continue.",
            fontSize = 13.sp,
            color = Color(0xFFB71C1C),      // dark red text
            modifier = Modifier.padding(12.dp),
            lineHeight = 19.sp
        )
    }
}
