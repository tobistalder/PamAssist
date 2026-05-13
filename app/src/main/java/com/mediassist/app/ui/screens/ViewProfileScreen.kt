package com.mediassist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.R
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.ProfileViewModel

@Composable
fun ViewProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToEdit: () -> Unit,
    onBack: () -> Unit
) {
    val profileState by viewModel.latestProfile.collectAsState(initial = null)

    if (profileState == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No hay perfil cargado aún",
                fontSize = 18.sp,
                color = TextSecondary
            )
        }
        return
    }

    val profile = profileState!!

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Blob decorativo teal arriba a la izquierda
        Box(
            modifier = Modifier
                .size(130.dp)
                .offset(x = (-30).dp, y = (-30).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.18f))
        )

        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── Header ──────────────────────────────────────────────────────
            Icon(
                painter = painterResource(id = R.drawable.ic_pam_logo2),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "MI PERFIL",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TealDark,
                letterSpacing = 2.sp
            )
            // Línea decorativa
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp).height(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TealDark)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TealPrimary)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Card datos personales ────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(TealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = TealDark,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Datos
                    Column {
                        Text(
                            text = "${profile.name} ${profile.surname}".uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ProfileDataRow(
                            icon = Icons.Outlined.CalendarMonth,
                            label = "Fecha de Nacimiento:",
                            value = profile.birthDate
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ProfileDataRow(
                            icon = Icons.Outlined.Person,
                            label = "Edad:",
                            value = "${profile.calculateAge()} años"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ProfileDataRow(
                            icon = Icons.Outlined.MonitorWeight,
                            label = "Peso:",
                            value = "${profile.weight} kg"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Card historial médico ────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Título historial
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SageLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Favorite,
                                contentDescription = null,
                                tint = SageDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "HISTORIAL MÉDICO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Estado vacío o lista
                    if (profile.medicalHistory.isBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AssignmentLate,
                                contentDescription = null,
                                tint = SageLight,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aún no tienes registros médicos.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tu historial aparecerá aquí.",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = profile.medicalHistory,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Botón editar ─────────────────────────────────────────────────
            Button(
                onClick = onNavigateToEdit,
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
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EDITAR PERFIL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Botón volver
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 52.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
                .clickable { onBack() }
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Volver",
                tint = TealDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProfileDataRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                lineHeight = 18.sp
            )
        }
    }
}
