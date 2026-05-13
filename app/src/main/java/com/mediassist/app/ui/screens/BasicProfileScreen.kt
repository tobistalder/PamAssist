package com.mediassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.R
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun BasicProfileScreen(
    viewModel: ProfileViewModel,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Blob teal arriba a la izquierda
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = (-50).dp, y = (-50).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.18f))
        )

        // Onda sage abajo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.wave_sage),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // Botón volver + label
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
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "MI PERFIL",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TealDark,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Avatar decorativo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(TealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = TealDark,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Título
            Text(
                text = "Tu Perfil",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Línea decorativa
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TealPrimary))
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(4.dp)).background(TealDark))
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TealPrimary))
            }

            Text(
                text = "Por favor completá tu información básica.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Campos ──────────────────────────────────────────────────────

            // Nombre
            ProfileInputField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                placeholder = "Nombre",
                icon = Icons.Outlined.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Apellido
            ProfileInputField(
                value = viewModel.surname,
                onValueChange = { viewModel.surname = it },
                placeholder = "Apellido",
                icon = Icons.Outlined.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Fecha de nacimiento
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val calendar = Calendar.getInstance()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val formattedDay = dayOfMonth.toString().padStart(2, '0')
                                val formattedMonth = (month + 1).toString().padStart(2, '0')
                                viewModel.birthDate = "$formattedDay/$formattedMonth/$year"
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = TealDark,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (viewModel.birthDate.isEmpty()) "Fecha de nacimiento" else viewModel.birthDate,
                        fontSize = 14.sp,
                        color = if (viewModel.birthDate.isEmpty()) TextSecondary else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Peso
            ProfileInputField(
                value = viewModel.weight,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                        viewModel.weight = newValue
                    }
                },
                placeholder = "Peso (kg)",
                icon = Icons.Outlined.MonitorWeight,
                suffix = "kg",
                keyboardType = KeyboardType.Decimal
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón continuar
            Button(
                onClick = {
                    if (viewModel.validateBasicProfile()) {
                        onContinue()
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Por favor completá todos los campos correctamente."
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Continuar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun ProfileInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    suffix: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TealDark,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = TextPrimary,
                    fontFamily = PamFontFamily
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, fontSize = 14.sp, color = TextSecondary.copy(alpha = 0.5f))
                    }
                    innerTextField()
                }
            )
            if (suffix.isNotEmpty()) {
                Text(text = suffix, fontSize = 13.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
