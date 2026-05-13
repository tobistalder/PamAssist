package com.mediassist.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.ChatMessage
import com.mediassist.app.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages = viewModel.messages
    val inputText by viewModel.inputText
    val isGenerating by viewModel.isGenerating
    val isListening by viewModel.isListening
    val isLoadingModel by viewModel.isLoadingModel
    val modelLoadError by viewModel.modelLoadError
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.startListening()
    }

    // Auto-scroll to bottom on new messages / streaming tokens
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val inputEnabled = !isLoadingModel && modelLoadError == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── Decorative blobs ──────────────────────────────────────────────
        // Top-left teal wave
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = (-45).dp, y = (-45).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f))
        )
        // Top-right sage leaves
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 35.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(SagePrimary.copy(alpha = 0.14f))
        )
        // Bottom-right teal blob
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 40.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.08f))
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──────────────────────────────────────────────────
            ChatTopBar(onBack = onBack)

            // ── Messages ─────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    if (index == 0 && !message.isUser) {
                        // First bot message → render as WelcomeCard
                        WelcomeMessageCard()
                    } else {
                        ChatBubble(
                            message = message,
                            isLastBotMessage = !message.isUser && index == messages.lastIndex,
                            isGenerating = isGenerating,
                            onStop = { viewModel.stop() }
                        )
                    }
                }
            }

            // ── Bottom Input ─────────────────────────────────────────────
            ChatBottomBar(
                text = inputText,
                onTextChange = { if (inputEnabled) viewModel.onInputTextChanged(it) },
                onSend = { if (inputEnabled) viewModel.sendMessage() },
                onMicClick = {
                    if (!inputEnabled) return@ChatBottomBar
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(context, permission)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (isListening) viewModel.stopListening() else viewModel.startListening()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                onStopClick = { viewModel.stop() },
                isGenerating = isGenerating,
                isListening = isListening
            )
        }

        // ── Model Loading Overlay ────────────────────────────────────────
        if (isLoadingModel || modelLoadError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (modelLoadError != null) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(modelLoadError!!, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.retryModelLoad() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                            ) {
                                Text("Reintentar", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            CircularProgressIndicator(color = TealPrimary, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Cargando a Pam...", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Esto solo toma unos segundos", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TOP BAR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Volver",
                tint = TealDark,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "Asistente médico",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TealDark
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  WELCOME CARD
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun WelcomeMessageCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "¡Hola! Soy Pam, tu asistente médica personal.",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Puedo ayudarte con:",
                fontSize = 14.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Capabilities list
            WelcomeCapabilityItem(
                icon = Icons.AutoMirrored.Outlined.Chat,
                title = "Consultas médicas",
                description = "Preguntame cualquier duda sobre tu salud"
            )
            HorizontalDivider(
                color = DividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            WelcomeCapabilityItem(
                icon = Icons.Outlined.Medication,
                title = "Agregar medicamentos",
                description = "Decime \"quiero agregar un medicamento\" y te guío paso a paso"
            )
            HorizontalDivider(
                color = DividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            WelcomeCapabilityItem(
                icon = Icons.Outlined.Notifications,
                title = "Recordatorios",
                description = "Te aviso cuando es hora de tomar tus medicamentos"
            )
            HorizontalDivider(
                color = DividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            WelcomeCapabilityItem(
                icon = Icons.Outlined.CameraAlt,
                title = "Escanear productos",
                description = "Analizamos si un alimento o medicamento es adecuado para vos"
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "¿En qué te puedo ayudar hoy?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WelcomeCapabilityItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TealLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealDark,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TealDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CHAT BUBBLE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChatBubble(
    message: ChatMessage,
    isLastBotMessage: Boolean,
    isGenerating: Boolean,
    onStop: () -> Unit
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleColor = if (isUser) TealPrimary else Color.White
    val textColor = if (isUser) Color.White else TextPrimary

    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = shape,
                color = bubbleColor,
                shadowElevation = if (isUser) 0.dp else 1.dp
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    if (message.isLoading) {
                        LoadingDots()
                    } else {
                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Stop button — shown on the last bot message while generating
            if (!isUser && isLastBotMessage && isGenerating) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.12f))
                        .clickable { onStop() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = "Detener",
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Detener",
                            fontSize = 12.sp,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LOADING DOTS ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alphas = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 800
                    0.25f at (i * 200)
                    1f at (i * 200 + 200)
                    0.25f at (i * 200 + 400)
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot$i"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = alpha.value))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BOTTOM INPUT BAR
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ChatBottomBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    isListening: Boolean
) {
    // Curved teal background at the very bottom
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TealLight,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text field in pill shape
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    // Attachment icon (OCR placeholder)
                    IconButton(
                        onClick = { /* OCR / camera trigger */ },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Adjuntar",
                            tint = TealDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = {
                            Text(
                                "Escribí tu consulta...",
                                color = TextHint,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = TealPrimary
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                    )

                    // Send button inside pill when there is text
                    if (text.isNotBlank() && !isGenerating) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TealPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            // Mic / Stop FAB
            if (isGenerating) {
                FloatingActionButton(
                    onClick = onStopClick,
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Detener generación",
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = onMicClick,
                    containerColor = if (isListening) ErrorRed else TealPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Micrófono",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
