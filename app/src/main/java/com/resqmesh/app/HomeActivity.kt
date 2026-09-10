package com.resqmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

private val DarkBg      = Color(0xFF0A0E1A)
private val NavyDark    = Color(0xFF0D1321)
private val CardBg      = Color(0xFF141929)
private val RedPrimary  = Color(0xFFE53935)
private val RedDark     = Color(0xFFB71C1C)
private val BluePrimary = Color(0xFF1565C0)
private val BlueAccent  = Color(0xFF42A5F5)
private val TextWhite   = Color(0xFFECEFF1)
private val TextGray    = Color(0xFF78909C)
private val GlassWhite  = Color(0x1AFFFFFF)
private val GlassBorder = Color(0x33FFFFFF)

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val meshViewModel: MeshViewModel = viewModel()

    NavHost(navController = navController, startDestination = "permissions") {

        composable("permissions") {
            PermissionsScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(
                navController = navController,
                onMeshStart   = { asGateway -> meshViewModel.startMesh(asGateway) }
            )
        }
        composable("survivor") {
            SurvivorScreen(viewModel = meshViewModel)
        }
        composable("rescue") {
            RescueDashboardScreen(navController = navController, viewModel = meshViewModel)
        }
        composable(
            route = "detail/{messageId}",
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            MessageDetailScreen(messageId = messageId, viewModel = meshViewModel)
        }
        composable("about") {
            AboutScreen()
        }
        composable(
            route = "map/{lat}/{lon}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lon") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lon = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            OfflineMapScreen(latitude = lat, longitude = lon)
        }
    }
}

@Composable
fun HomeScreen(
    navController: NavHostController,
    onMeshStart: (asGateway: Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBg, Color(0xFF0A0F1E), NavyDark)
                )
            )
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color  = RedPrimary.copy(alpha = 0.06f),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.85f, size.height * 0.15f)
            )
            drawCircle(
                color  = BluePrimary.copy(alpha = 0.06f),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.1f, size.height * 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = RedPrimary.copy(alpha = glowAlpha * 0.15f),
                            shape = RoundedCornerShape(60.dp)
                        )
                        .blur(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            Brush.radialGradient(listOf(RedPrimary, RedDark)),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🆘", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ResQMesh",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                letterSpacing = 1.sp
            )

            Text(
                text = "Offline Emergency Mesh Network",
                fontSize = 14.sp,
                color = BlueAccent,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFF1B5E20).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "BLE Mesh Ready",
                        fontSize = 12.sp,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(52.dp))

            EmergencyRoleCard(
                emoji       = "🚨",
                title       = "I NEED HELP",
                subtitle    = "Send SOS to nearby rescue teams",
                gradient    = Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFC62828))),
                borderColor = Color(0xFFEF9A9A),
                onClick     = {
                    onMeshStart(false)
                    navController.navigate("survivor")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            EmergencyRoleCard(
                emoji       = "🛡️",
                title       = "RESCUE TEAM",
                subtitle    = "Monitor & respond to SOS signals",
                gradient    = Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF0D47A1))),
                borderColor = Color(0xFF90CAF9),
                onClick     = {
                    onMeshStart(true)
                    navController.navigate("rescue")
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { navController.navigate("about") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text  = "ℹ️  About ResQMesh",
                    color = TextGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun EmergencyRoleCard(
    emoji: String,
    title: String,
    subtitle: String,
    gradient: Brush,
    borderColor: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable {
                pressed = true
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = emoji, fontSize = 32.sp)
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "→", fontSize = 22.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun RoleButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
