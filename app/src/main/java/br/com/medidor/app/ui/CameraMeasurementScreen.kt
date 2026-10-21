package br.com.medidor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import br.com.medidor.app.domain.BrazilianCoin
import br.com.medidor.app.domain.NormalizedPoint
import br.com.medidor.app.domain.PhotoCalibration
import java.io.File
import kotlin.math.min

/**
 * MVP camera-based planar measurement:
 * 2 taps define a coin's known diameter, 2 taps define the target distance.
 * Never use this for depth, distant tanks or non-coplanar objects.
 */
@Composable
fun CameraMeasurementScreen(onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var coin by remember { mutableStateOf(BrazilianCoin.TWENTY_FIVE_CENTS) }
    val points = remember { mutableStateListOf<NormalizedPoint>() }
    var saved by remember { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Foto calibrada por moeda", style = MaterialTheme.typography.titleLarge)
        Text("Coloque uma moeda ao lado do objeto, no MESMO PLANO. Capture a foto e toque nas extremidades.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bitmap == null) {
            if (granted) {
                CameraPreviewAndCapture(
                    onPhoto = { bitmap = it; points.clear(); saved = false },
                    onError = { error -> TextErrorSink.lastError = error }
                )
                TextErrorSink.lastError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Permitir câmera")
                }
                Text("A permissão é usada somente durante a captura, sem upload de imagens.")
            }
        } else {
            val photo = bitmap!!
            Text("1. Escolha a moeda utilizada:", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BrazilianCoin.entries) { item ->
                    FilterChip(
                        selected = coin == item,
                        onClick = { coin = item; saved = false },
                        label = { Text(item.label) }
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Foto capturada para medição",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(
                    Modifier.fillMaxSize().pointerInput(photo) {
                        detectTapGestures { touch ->
                            val mapped = photoPointFromTouch(touch, size, photo.width, photo.height)
                            if (mapped != null && points.size < 4) {
                                points.add(mapped)
                                saved = false
                            }
                        }
                    }
                ) {
                    val rect = imageRect(size.width, size.height, photo.width, photo.height)
                    points.forEachIndexed { index, point ->
                        val center = Offset(rect.left + rect.width * point.x, rect.top + rect.height * point.y)
                        drawCircle(
                            color = if (index < 2) Color(0xFFFFB443) else Color(0xFF4EE0C1),
                            radius = 7.dp.toPx(),
                            center = center
                        )
                    }
                    if (points.size >= 2) {
                        val a = points[0]; val b = points[1]
                        drawLine(Color(0xFFFFB443),
                            Offset(rect.left + rect.width * a.x, rect.top + rect.height * a.y),
                            Offset(rect.left + rect.width * b.x, rect.top + rect.height * b.y),
                            strokeWidth = 3.dp.toPx())
                    }
                    if (points.size >= 4) {
                        val a = points[2]; val b = points[3]
                        drawLine(Color(0xFF4EE0C1),
                            Offset(rect.left + rect.width * a.x, rect.top + rect.height * a.y),
                            Offset(rect.left + rect.width * b.x, rect.top + rect.height * b.y),
                            strokeWidth = 3.dp.toPx())
                    }
                }
            }
            val prompt = when (points.size) {
                0 -> "2. Toque em uma extremidade do diâmetro da moeda."
                1 -> "3. Toque na outra extremidade do diâmetro da moeda."
                2 -> "4. Toque no ponto inicial do objeto."
                3 -> "5. Toque no ponto final do objeto."
                else -> "Medição concluída. Confira os pontos."
            }
            Text(prompt, fontWeight = FontWeight.Medium)
            val mm = if (points.size == 4) {
                val reference = PhotoCalibration.distancePixels(points[0], points[1], photo.width, photo.height)
                val target = PhotoCalibration.distancePixels(points[2], points[3], photo.width, photo.height)
                if (reference > 0.0) PhotoCalibration.millimetersFromReference(
                    coin.diameterMm, reference, target) else null
            } else null
            if (points.size == 4 && mm == null) {
                Text("A referência precisa ter dois pontos diferentes.", color = MaterialTheme.colorScheme.error)
            }
            if (mm != null) {
                ResultCard("Distância estimada no plano da moeda", "${decimal(mm)} mm • ${decimal(mm / 10)} cm")
                Button(onClick = {
                    onSave("Foto com moeda: ${coin.label}", "${decimal(mm)} mm (estimado, mesma superfície)")
                    saved = true
                }) { Text("Salvar resultado") }
                if (saved) Text("Resultado salvo neste aparelho.")
            }
            OutlinedButton(onClick = { points.clear(); saved = false }) {
                Text("Recomeçar marcações")
            }
            OutlinedButton(onClick = { bitmap = null; points.clear(); saved = false }) {
                Text("Tirar outra foto")
            }
        }
        Text("ATENÇÃO: escala válida somente para medidas no mesmo plano da moeda, sem inclinação relevante. " +
            "Não mede profundidade nem substitui paquímetro, trena ou arqueação.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Temporary UI error holder (not persisted and never shared externally). */
private object TextErrorSink {
    var lastError by mutableStateOf<String?>(null)
}

@Composable
private fun CameraPreviewAndCapture(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = context as? ComponentActivity
    val view = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(owner, view) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        if (owner != null) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().apply {
                            surfaceProvider = view.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        boundProvider = provider
                        imageCapture = capture
                    } catch (error: Exception) {
                        onError(error.localizedMessage ?: "Não foi possível inicializar a câmera.")
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        } else {
            onError("Activity incompatível para abrir a câmera.")
        }
        onDispose {
            disposed = true
            boundProvider?.unbindAll()
            imageCapture = null
        }
    }

    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(340.dp))
    Button(enabled = imageCapture != null, onClick = {
        val camera = imageCapture ?: return@Button
        val file = File(context.cacheDir, "medicao_${System.currentTimeMillis()}.jpg")
        camera.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                            ?: error("Arquivo de imagem inválido")
                        val corrected = orientBitmap(decoded, file)
                        onPhoto(corrected)
                    } catch (error: Exception) {
                        onError(error.localizedMessage ?: "Não foi possível ler a fotografia.")
                    } finally {
                        file.delete()
                    }
                }
                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    file.delete()
                    onError(exception.localizedMessage ?: "Falha na captura.")
                }
            }
        )
    }) { Text("Capturar fotografia") }
}

private fun orientBitmap(bitmap: Bitmap, file: File): Bitmap {
    val angle = when (ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (angle == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
        postRotate(angle)
    }, true).also { if (it !== bitmap) bitmap.recycle() }
}

private data class ImageRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun imageRect(viewWidth: Float, viewHeight: Float, photoWidth: Int, photoHeight: Int): ImageRect {
    val scale = min(viewWidth / photoWidth, viewHeight / photoHeight)
    val width = photoWidth * scale
    val height = photoHeight * scale
    return ImageRect((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
}

private fun photoPointFromTouch(touch: Offset, viewSize: IntSize, width: Int, height: Int): NormalizedPoint? {
    val rect = imageRect(viewSize.width.toFloat(), viewSize.height.toFloat(), width, height)
    if (rect.width <= 0f || rect.height <= 0f) return null
    val x = (touch.x - rect.left) / rect.width
    val y = (touch.y - rect.top) / rect.height
    return if (x in 0f..1f && y in 0f..1f) NormalizedPoint(x, y) else null
}
