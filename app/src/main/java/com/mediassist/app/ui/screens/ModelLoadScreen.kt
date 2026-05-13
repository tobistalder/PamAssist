package com.mediassist.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.domain.CactusManager
import com.mediassist.app.ui.theme.*
import java.io.File

enum class LoadState {
    CHECKING, DOWNLOADING, EXTRACTING, INITIALIZING, ERROR
}

@Composable
fun ModelLoadScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var retryCount by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf(LoadState.CHECKING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(retryCount) {
        state = LoadState.CHECKING; errorMessage = null; bytesDownloaded = 0L; totalBytes = 0L
        val sharedPrefs = context.getSharedPreferences("MediAssistPrefs", Context.MODE_PRIVATE)
        try {
            if (CactusManager.isModelDownloaded(context)) {
                // Model already downloaded — no need to init here, lazy load later
                sharedPrefs.edit().putBoolean("MODEL_READY", true).apply()
                onSuccess()
            } else {
                val modelDir = File(context.filesDir, "models/${CactusManager.MODEL_DIR_NAME}")
                if (modelDir.exists() && !CactusManager.isModelDownloaded(context)) modelDir.deleteRecursively()
                state = LoadState.DOWNLOADING
                val result = CactusManager.downloadOnly(context) { downloaded, total ->
                    if (total == -1L) state = LoadState.EXTRACTING else { bytesDownloaded = downloaded; totalBytes = total }
                }
                if (result.isSuccess) { sharedPrefs.edit().putBoolean("MODEL_READY", true).apply(); onSuccess() }
                else { state = LoadState.ERROR; errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido" }
            }
        } catch (e: Exception) { state = LoadState.ERROR; errorMessage = e.message ?: "Error inesperado" }
    }

    val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(400), label = "progress")
    val downloadedMB = bytesDownloaded / 1_048_576
    val totalMB = totalBytes / 1_048_576
    val percentText = "${(progress * 100).toInt()}%"

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        // Decorative blobs
        Box(modifier = Modifier.size(130.dp).offset((-40).dp, (-40).dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.10f)))
        Box(modifier = Modifier.size(100.dp).align(Alignment.TopEnd).offset(30.dp, (-25).dp).clip(CircleShape).background(SagePrimary.copy(alpha = 0.12f)))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = TealDark, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text("Asistente médico", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text("Configuración del\nAsistente Médico", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TealDark, textAlign = TextAlign.Center, lineHeight = 32.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Para que el asistente médico funcione sin conexión a internet, necesitamos cargar el modelo de inteligencia artificial.", fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))

            Spacer(modifier = Modifier.height(28.dp))

            // ── Download Card ────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Cloud icon
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(TealLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CloudDownload, null, tint = TealDark, modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when (state) {
                        LoadState.CHECKING -> {
                            CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Verificando modelo...", fontSize = 14.sp, color = TealPrimary, fontWeight = FontWeight.Medium)
                        }
                        LoadState.DOWNLOADING -> {
                            Text(percentText, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TealDark)
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                color = TealPrimary,
                                trackColor = TealLight,
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("$downloadedMB MB / $totalMB MB", fontSize = 15.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Descargando modelo Cactus (~4.7 GB)...", fontSize = 13.sp, color = TextSecondary)
                        }
                        LoadState.EXTRACTING -> {
                            CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Extrayendo modelo...", fontSize = 14.sp, color = TealPrimary, fontWeight = FontWeight.Medium)
                        }
                        LoadState.INITIALIZING -> {
                            CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Iniciando modelo...", fontSize = 14.sp, color = TealPrimary, fontWeight = FontWeight.Medium)
                        }
                        LoadState.ERROR -> {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Error: ${errorMessage ?: "Desconocido"}", fontSize = 13.sp, color = ErrorRed, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val modelDir = File(context.filesDir, "models/${CactusManager.MODEL_DIR_NAME}")
                                    if (modelDir.exists() && !CactusManager.isModelDownloaded(context)) modelDir.deleteRecursively()
                                    File(context.filesDir, "models/${CactusManager.MODEL_DIR_NAME}.zip.tmp").let { if (it.exists()) it.delete() }
                                    retryCount++
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)
                            ) {
                                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reintentar descarga", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Privacy banner
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = TealLight) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.VerifiedUser, null, tint = TealDark, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Privado y seguro", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                Text("El modelo se almacena únicamente en tu dispositivo. Tu información siempre es tuya.", fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── "o" Divider ──────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor, thickness = 1.dp)
                Text("o", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 14.sp, color = TextSecondary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = DividerColor, thickness = 1.dp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Test Mode Button ─────────────────────────────────────────────
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    val sharedPrefs = context.getSharedPreferences("MediAssistPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putBoolean("DEMO_MODE", true).apply()
                    CactusManager.enableDemoMode()
                    onSuccess()
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TealPrimary),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(TealLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Science, null, tint = TealDark, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Modo Prueba (sin IA)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                        Text("Solo para pruebas, sin funciones de IA.", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
        }
    }
}
