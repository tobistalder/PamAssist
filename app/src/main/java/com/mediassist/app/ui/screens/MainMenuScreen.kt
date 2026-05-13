package com.mediassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Person
import com.mediassist.app.R
import com.mediassist.app.ui.theme.*

@Composable
fun MainMenuScreen(
    onChatClick: () -> Unit,
    onScanClick: () -> Unit,
    onMedicationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Decoración blob teal arriba a la izquierda
        Box(
            modifier = Modifier
                .size(130.dp)
                .offset(x = (-30).dp, y = (-30).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.18f))
        )

        Box(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 35.dp, y = 35.dp)
                .clip(CircleShape)
                .background(SagePrimary.copy(alpha = 0.18f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.ic_pam_wordmark),
                contentDescription = "PAM Assist",
                modifier = Modifier.height(90.dp),
                contentScale = ContentScale.Fit
            )

            // Línea decorativa bajo el título
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(32.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(TealPrimary)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Grid 2x2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuCard(
                    modifier = Modifier.weight(1f),
                    label = "CHAT CON PAM",
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = null, tint = TealDark, modifier = Modifier.size(34.dp)) },
                    iconBgColor = TealLight,
                    waveRes = R.drawable.wave_teal,
                    onClick = onChatClick
                )
                MenuCard(
                    modifier = Modifier.weight(1f),
                    label = "ESCANEAR\nPRODUCTO",
                    icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = TealDark, modifier = Modifier.size(34.dp)) },
                    iconBgColor = TealLight,
                    waveRes = R.drawable.wave_teal,
                    onClick = onScanClick
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuCard(
                    modifier = Modifier.weight(1f),
                    label = "MIS\nMEDICAMENTOS",
                    icon = { Icon(Icons.Outlined.Medication, contentDescription = null, tint = SageDark, modifier = Modifier.size(34.dp)) },
                    iconBgColor = SageLight,
                    waveRes = R.drawable.wave_sage,
                    onClick = onMedicationsClick
                )
                MenuCard(
                    modifier = Modifier.weight(1f),
                    label = "MI PERFIL",
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = SageDark, modifier = Modifier.size(34.dp)) },
                    iconBgColor = SageLight,
                    waveRes = R.drawable.wave_sage,
                    onClick = onProfileClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Banner inferior
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TealLight),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ícono hoja
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pam_logo2),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Texto
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Bienvenido a PAM Assist",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tu compañero para un envejecimiento saludable y pleno.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }

                    // Imagen paisaje
                    Image(
                        painter = painterResource(id = R.drawable.banner_landscape),
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MenuCard(
    modifier: Modifier = Modifier,
    label: String,
    icon: @Composable () -> Unit,
    iconBgColor: Color,
    waveRes: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.4.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Image(
                painter = painterResource(id = waveRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}
