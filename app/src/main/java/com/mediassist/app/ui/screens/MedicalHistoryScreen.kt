package com.mediassist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.ProfileViewModel

@Composable
fun MedicalHistoryScreen(
    viewModel: ProfileViewModel,
    onProfileSaved: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveComplete by viewModel.saveComplete.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()
    val scrollState = rememberScrollState()

    // Show save confirmation message
    LaunchedEffect(saveMessage) {
        saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveMessage()
        }
    }

    // Navigate after successful save
    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            viewModel.resetSaveComplete()
            onProfileSaved()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Blob decorativo teal arriba a la izquierda
        Box(
            modifier = Modifier
                .size(140.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.15f))
        )

        // Blob decorativo teal abajo a la derecha
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f))
        )

        // Ilustración de rama/hoja arriba a la derecha simulada con un blob orgánico
        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(SagePrimary.copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Volver",
                        tint = TealDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = "HISTORIAL MÉDICO",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TealDark,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.size(40.dp)) // Para balancear el Row y centrar el título
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Elemento gráfico central (Corazón con borde y cruz)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TealLight),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TealPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = TealDark,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Título principal
            Text(
                text = "Cuéntanos sobre tu salud",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            // Puntos indicadores
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.3f)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.3f)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TealPrimary))
            }

            // Subtítulo
            Text(
                text = "Describe tus condiciones, medicamentos, alergias, cirugías o cualquier información relevante para tu salud.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Campo de entrada libre
            val maxChars = 2000
            val currentChars = viewModel.medicalHistoryInput.length
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealLight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = viewModel.medicalHistoryInput,
                            onValueChange = { 
                                if (it.length <= maxChars) {
                                    viewModel.medicalHistoryInput = it 
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp),
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = TextPrimary,
                                fontFamily = PamFontFamily
                            ),
                            decorationBox = { innerTextField ->
                                if (viewModel.medicalHistoryInput.isEmpty()) {
                                    Text(
                                        text = "Escribe tu información de salud aquí...",
                                        fontSize = 14.sp,
                                        color = TextSecondary.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    
                    Text(
                        text = "$currentChars/$maxChars",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tarjeta de Privacidad (Security Card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TealLight.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = "Tu información está segura",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark
                        )
                        Text(
                            text = "Tu privacidad es nuestra prioridad. Esta información es confidencial.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Botón Guardar Perfil (Footer)
            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealDark,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GUARDAR PERFIL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}
