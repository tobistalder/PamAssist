package com.mediassist.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediassist.app.data.model.Medication
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.MedicationsViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

val InputBorderColor = Color(0xFFC8BFA8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    viewModel: MedicationsViewModel,
    onBack: () -> Unit
) {
    val medications by viewModel.medications.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show feedback messages
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Formas decorativas
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .clip(CircleShape)
                .background(TealPrimary.copy(alpha = 0.1f))
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(SagePrimary.copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // ── Top Bar ────────────────────────────────────────────────────────
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
                        contentDescription = "Atrás",
                        tint = TealDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Cabecera ───────────────────────────────────────────────────────
            Text(
                text = "Tus Medicamentos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TealDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Gestiona tus medicamentos fácilmente.",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Botón Principal ────────────────────────────────────────────────
            Button(
                onClick = { viewModel.openAddDialog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AGREGAR MEDICAMENTO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Lista de Medicamentos ──────────────────────────────────────────
            if (medications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tienes medicamentos agregados.",
                        fontSize = 16.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = medications,
                        key = { it.id }
                    ) { medication ->
                        MedicationCard(
                            medication = medication,
                            onToggle = { viewModel.toggleMedication(medication) },
                            onDelete = { viewModel.deleteMedication(medication) },
                            onEdit = { viewModel.openAddDialog(medication) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Modal de Agregar/Editar
    if (viewModel.showAddDialog) {
        val scope = rememberCoroutineScope()
        AddMedicationBottomSheet(
            medName = viewModel.medName,
            onMedNameChange = { viewModel.medName = it },
            medDose = viewModel.medDose,
            onMedDoseChange = { viewModel.medDose = it },
            medFrequencyHours = viewModel.medFrequencyHours,
            onMedFrequencyChange = { newValue ->
                if (newValue.all { it.isDigit() }) {
                    viewModel.medFrequencyHours = newValue
                }
            },
            medScheduleTimes = viewModel.medScheduleTimes,
            onMedScheduleChange = { viewModel.medScheduleTimes = it },
            onDismiss = { viewModel.closeAddDialog() },
            onSave = { viewModel.saveMedication() },
            onError = { errorMsg ->
                scope.launch { snackbarHostState.showSnackbar(errorMsg) }
            },
            isEditing = viewModel.editingMedication != null,
            onDelete = {
                viewModel.editingMedication?.let { viewModel.deleteMedication(it) }
            }
        )
    }
}

@Composable
private fun MedicationCard(
    medication: Medication,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Ícono
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(TealLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Info del medicamento
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dosis: ${medication.dose}",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    if (medication.frequencyHours > 0) {
                        Text(
                            text = "Cada ${medication.frequencyHours} horas",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                    if (medication.scheduleTimes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = medication.scheduleTimes.replace(",", ", "),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Switch
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Switch(
                        checked = medication.active,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TealPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                    Text(
                        text = if (medication.active) "Activo" else "Inactivo",
                        fontSize = 12.sp,
                        color = if (medication.active) SagePrimary else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Background, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Eliminar botón
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFEBEE))
                    .clickable { onDelete() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Eliminar",
                        fontSize = 14.sp,
                        color = Color(0xFFE57373),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMedicationBottomSheet(
    medName: String,
    onMedNameChange: (String) -> Unit,
    medDose: String,
    onMedDoseChange: (String) -> Unit,
    medFrequencyHours: String,
    onMedFrequencyChange: (String) -> Unit,
    medScheduleTimes: String,
    onMedScheduleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onError: (String) -> Unit,
    isEditing: Boolean,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTimes by remember {
        mutableStateOf(
            if (medScheduleTimes.isBlank()) emptyList()
            else medScheduleTimes.split(",")
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* No cerrar al tocar fuera o back para evitar pérdida de datos */ },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* No cerrar al tocar fondo */ },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
            // Cabecera del Modal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Editar medicamento" else "Agregar medicamento",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealDark
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TealLight)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cerrar",
                        tint = TealDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Ilustración Central
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(TealLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalPharmacy,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Campos ──────────────────────────────────────────────────────────
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = InputBorderColor,
                focusedLabelColor = TealPrimary,
                unfocusedLabelColor = TextSecondary,
                cursorColor = TealPrimary
            )

            // Nombre
            OutlinedTextField(
                value = medName,
                onValueChange = onMedNameChange,
                label = { Text("Nombre del medicamento") },
                placeholder = { Text("Ej. Gotas", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Outlined.LocalPharmacy, contentDescription = null, tint = TealPrimary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dosis
            OutlinedTextField(
                value = medDose,
                onValueChange = onMedDoseChange,
                label = { Text("Dosis") },
                placeholder = { Text("Ej. 1 gota", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = TealPrimary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Frecuencia
            OutlinedTextField(
                value = medFrequencyHours,
                onValueChange = onMedFrequencyChange,
                label = { Text("Frecuencia (horas)") },
                placeholder = { Text("Ej. 8", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TealPrimary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = textFieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Horarios ────────────────────────────────────────────────────────
            Text(
                text = "Horarios programados",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            val formattedTime = "${hourOfDay.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                            if (!selectedTimes.contains(formattedTime)) {
                                selectedTimes = selectedTimes + formattedTime
                            }
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = TealPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar horario", fontWeight = FontWeight.SemiBold, color = TealPrimary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            selectedTimes.forEach { time ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Background)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = TealDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = time, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remover horario",
                        tint = Color(0xFFE57373),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                selectedTimes = selectedTimes.filter { it != time }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Acciones (Footer) ───────────────────────────────────────────────
            if (isEditing) {
                TextButton(
                    onClick = {
                        onDelete?.invoke()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminar medicamento", color = Color(0xFFE57373), fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold, color = TealPrimary)
                }

                Button(
                    onClick = {
                        if (medName.isBlank()) {
                            onError("El nombre es requerido")
                        } else {
                            onMedScheduleChange(selectedTimes.joinToString(","))
                            onSave()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
            }
        }
    }
}
