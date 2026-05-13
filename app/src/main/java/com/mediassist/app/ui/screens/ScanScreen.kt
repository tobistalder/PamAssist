package com.mediassist.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mediassist.app.ui.theme.*
import com.mediassist.app.ui.viewmodel.ScanState
import com.mediassist.app.ui.viewmodel.ScanViewModel
import java.io.File
import java.util.concurrent.Executors

@Composable
fun ScanScreen(viewModel: ScanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanState by viewModel.scanState.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val capturedImagePath by viewModel.capturedImagePath.collectAsState()
    val isLoadingModel by viewModel.isLoadingModel.collectAsState()
    val modelLoadError by viewModel.modelLoadError.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) onBack()
    }
    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(flashEnabled) { camera?.cameraControl?.enableTorch(flashEnabled) }

    val canCapture = !isLoadingModel && modelLoadError == null

    Box(modifier = Modifier.fillMaxSize()) {
        when (scanState) {
            ScanState.PREVIEWING -> CameraPreviewState(
                hasCameraPermission, lifecycleOwner, imageCapture, flashEnabled,
                onFlashToggle = { flashEnabled = !flashEnabled },
                onCameraReady = { camera = it },
                onCapture = {
                    if (!canCapture) return@CameraPreviewState
                    val tempDir = File(context.filesDir, "temp").apply { mkdirs() }
                    val photoFile = File(tempDir, "scan_${System.currentTimeMillis()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) { viewModel.analyzeImageWithOCR(photoFile.absolutePath) }
                            override fun onError(exc: ImageCaptureException) { Log.e("ScanScreen", "Capture error", exc) }
                        }
                    )
                },
                onBack = onBack,
                isLoadingModel = isLoadingModel,
                modelLoadError = modelLoadError,
                onRetryModelLoad = { viewModel.retryModelLoad() }
            )
            ScanState.ANALYZING -> AnalysisState(capturedImagePath, analysisResult, onStop = { viewModel.stopAnalysis() }, onDiscard = { viewModel.resetScanner() }, onRetake = { viewModel.resetScanner() }, onBack = onBack)
            ScanState.CANCELLED -> CancelledState(capturedImagePath, onRetry = { viewModel.retryAnalysis() }, onDiscard = { viewModel.resetScanner() }, onBack = onBack)
            ScanState.RESULT -> ResultState(capturedImagePath, analysisResult, onScanAnother = { viewModel.resetScanner() }, onBack = onBack)
            ScanState.ERROR -> ErrorState(capturedImagePath, errorMessage, onRetry = { viewModel.resetScanner() }, onBack = onBack)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CAMERA PREVIEW
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CameraPreviewState(
    hasPermission: Boolean, lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture, flashEnabled: Boolean,
    onFlashToggle: () -> Unit, onCameraReady: (Camera) -> Unit,
    onCapture: () -> Unit, onBack: () -> Unit,
    isLoadingModel: Boolean = false, modelLoadError: String? = null,
    onRetryModelLoad: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            AndroidView(factory = { ctx ->
                val previewView = PreviewView(ctx).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    try {
                        provider.unbindAll()
                        onCameraReady(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture))
                    } catch (e: Exception) { Log.e("ScanScreen", "Camera bind error", e) }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        // Top bar
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = TealDark, modifier = Modifier.size(20.dp))
            }
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable { onFlashToggle() }, contentAlignment = Alignment.Center) {
                Icon(if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash", tint = if (flashEnabled) WarningAmber else TealDark, modifier = Modifier.size(20.dp))
            }
        }
        // Bottom area: capture button + model loading status
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            // Model loading indicator
            if (isLoadingModel) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Cargando modelo de análisis...", color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (modelLoadError != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error al cargar modelo", color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRetryModelLoad, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = TealPrimary), modifier = Modifier.height(36.dp)) {
                            Text("Reintentar", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Capture button
            val captureEnabled = !isLoadingModel && modelLoadError == null
            Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onCapture,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    enabled = captureEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealPrimary,
                        disabledContainerColor = TealPrimary.copy(alpha = 0.4f)
                    )
                ) {
                    if (isLoadingModel) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Outlined.CameraAlt, "Capturar", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ANALYZING STATE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AnalysisState(imagePath: String?, result: String, onStop: () -> Unit, onDiscard: () -> Unit, onRetake: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Box(modifier = Modifier.size(110.dp).offset((-35).dp, (-35).dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.10f)))
        Box(modifier = Modifier.size(80.dp).align(Alignment.TopEnd).offset(25.dp, (-20).dp).clip(CircleShape).background(SagePrimary.copy(alpha = 0.12f)))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScanTopBar(onBack = onBack)
            ImagePreviewBox(imagePath, onDiscard = onDiscard, onRetake = onRetake)
            ProcessingStatusCard()
            if (result.isBlank()) SkeletonResultCard() else StreamingResultCard(result)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)) {
                Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("DETENER INFERENCIA", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CANCELLED STATE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CancelledState(imagePath: String?, onRetry: () -> Unit, onDiscard: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Box(modifier = Modifier.size(110.dp).offset((-35).dp, (-35).dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.10f)))
        Box(modifier = Modifier.size(80.dp).align(Alignment.TopEnd).offset(25.dp, (-20).dp).clip(CircleShape).background(SagePrimary.copy(alpha = 0.12f)))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScanTopBar(onBack = onBack)
            ImagePreviewBox(imagePath, onDiscard = onDiscard, onRetake = onDiscard)
            CancelledStatusCard()
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("REINTENTAR ANÁLISIS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RESULT STATE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ResultState(imagePath: String?, result: String, onScanAnother: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Box(modifier = Modifier.size(110.dp).offset((-35).dp, (-35).dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.10f)))
        Box(modifier = Modifier.size(80.dp).align(Alignment.TopEnd).offset(25.dp, (-20).dp).clip(CircleShape).background(SagePrimary.copy(alpha = 0.12f)))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScanTopBar(onBack = onBack)
            ImagePreviewBox(imagePath, onDiscard = null, onRetake = null)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(TealLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Resultado del análisis", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(result, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary), colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)) {
                    Text("Volver", fontWeight = FontWeight.Bold, color = TealPrimary)
                }
                Button(onClick = onScanAnother, modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)) {
                    Text("Escanear otro", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ERROR STATE
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ErrorState(imagePath: String?, errorMsg: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Box(modifier = Modifier.size(110.dp).offset((-35).dp, (-35).dp).clip(CircleShape).background(TealPrimary.copy(alpha = 0.10f)))
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScanTopBar(onBack = onBack)
            if (imagePath != null) ImagePreviewBox(imagePath, onDiscard = null, onRetake = null)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ErrorRedLight)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column { Text("Error en el análisis", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp); Text(errorMsg, fontSize = 13.sp, color = TextSecondary) }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = TealPrimary, contentColor = Color.White)) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("REINTENTAR", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ScanTopBar(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = TealDark, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text("Escanear producto", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
    }
}

@Composable
private fun ImagePreviewBox(imagePath: String?, onDiscard: (() -> Unit)?, onRetake: (() -> Unit)?) {
    imagePath?.let { path ->
        val correctedBitmap = remember(path) {
            val original = BitmapFactory.decodeFile(path) ?: return@remember null
            try {
                val exif = ExifInterface(path)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                }
                android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } catch (e: Exception) {
                original
            }
        }
        correctedBitmap?.let { bmp ->
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()).clip(RoundedCornerShape(20.dp)).background(TealLight)) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Imagen capturada", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Fit)
                if (onDiscard != null) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).clickable { onDiscard() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, "Descartar", tint = TealDark, modifier = Modifier.size(18.dp))
                    }
                }
                if (onRetake != null) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).clickable { onRetake() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Refresh, "Retomar", tint = TealDark, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingStatusCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TealLight), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = TealPrimary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Pam está procesando tu imagen...", fontWeight = FontWeight.Bold, color = TealDark, fontSize = 14.sp)
                Text("Esto puede tardar unos segundos. Gracias por tu paciencia.", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun CancelledStatusCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = WarningAmberLight), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Pause, null, tint = WarningAmber, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("Análisis detenido", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text("Puedes volver a intentarlo cuando estés listo.", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SkeletonResultCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("El producto parece", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(Surface.copy(alpha = alpha)))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Surface.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun StreamingResultCard(result: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Resultado parcial", fontSize = 14.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(result, fontSize = 14.sp, color = TextPrimary, lineHeight = 21.sp)
        }
    }
}
