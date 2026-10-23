package br.com.medidor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.com.medidor.app.domain.CountRegion
import br.com.medidor.app.domain.LiveObjectCounter
import br.com.medidor.app.domain.LiveCountStabilizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private data class CountFrame(val width: Int, val height: Int, val regions: List<CountRegion>)

@Composable
fun LiveObjectCountScreen() {
    val context = LocalContext.current
    val owner = context as? ComponentActivity
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CountFrame?>(null) }
    var sensitivity by remember { mutableStateOf(45) }
    var stableCount by remember { mutableStateOf<Int?>(null) }
    val stabilizer = remember { LiveCountStabilizer() }
    val threshold = remember { AtomicInteger(45) }
    val preview = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    if (!granted) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Contagem automática ao vivo", style = MaterialTheme.typography.titleLarge)
            Text("A câmera analisa localmente os objetos visíveis, sem gravar ou enviar vídeo.")
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                Text("Permitir câmera")
            }
        }
        return
    }
    DisposableEffect(owner, preview) {
        val disposed = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        var bound = false
        val future = if (owner != null) ProcessCameraProvider.getInstance(context) else null
        fun bind() {
            val camera = provider ?: return
            val viewport = preview.viewPort ?: return
            if (disposed.get() || bound || owner == null) return
            try {
                val previewCase = Preview.Builder().build().apply {
                    setSurfaceProvider(preview.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                var last = 0L
                analysis.setAnalyzer(worker) { frame ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (!disposed.get() && now - last >= 300) {
                            last = now
                            val sampled = LiveCameraFrameSampler.sample(frame)
                            if (sampled != null) {
                                val regions = LiveObjectCounter.detect(sampled.pixels,
                                    sampled.width, sampled.height, threshold.get())
                                val confirmed = stabilizer.update(regions.size)
                                main.execute {
                                    if (!disposed.get()) {
                                        result = CountFrame(sampled.width, sampled.height, regions)
                                        stableCount = confirmed
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // An invalid frame is dropped; the next frame can recover.
                    } finally { frame.close() }
                }
                val group = UseCaseGroup.Builder().addUseCase(previewCase)
                    .addUseCase(analysis).setViewPort(viewport).build()
                camera.unbindAll()
                camera.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                bound = true
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Não foi possível iniciar a câmera."
            }
        }
        val listener = View.OnLayoutChangeListener {
                _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            bind()
        }
        preview.addOnLayoutChangeListener(listener)
        future?.addListener({
            if (!disposed.get()) try {
                provider = future.get()
                bind()
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Não foi possível abrir a câmera."
            }
        }, main)
        if (future == null) error = "Activity de câmera indisponível."
        onDispose {
            disposed.set(true)
            preview.removeOnLayoutChangeListener(listener)
            provider?.unbindAll()
            worker.shutdown()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contagem automática ao vivo — experimental",
            style = MaterialTheme.typography.titleMedium)
        Text("Aponte para objetos separados, com fundo uniforme e boa iluminação. Contagem aproximada; itens encostados podem ser unidos.")
        Text("Contagem estabilizada: ${stableCount?.toString() ?: "Analisando…"}",
            style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val frame = result ?: return@Canvas
                frame.regions.forEach { region ->
                    val color = Color(0xFF44E8B5)
                    region.outline.forEach { point ->
                        drawCircle(color, 1.5.dp.toPx(), Offset(
                            size.width * point.x / frame.width,
                            size.height * point.y / frame.height))
                    }
                    val left = size.width * region.left / frame.width
                    val top = size.height * region.top / frame.height
                    val right = size.width * (region.right + 1f) / frame.width
                    val bottom = size.height * (region.bottom + 1f) / frame.height
                    drawLine(color, Offset(left, top), Offset(right, top), 2.dp.toPx())
                    drawLine(color, Offset(right, top), Offset(right, bottom), 2.dp.toPx())
                    drawLine(color, Offset(right, bottom), Offset(left, bottom), 2.dp.toPx())
                    drawLine(color, Offset(left, bottom), Offset(left, top), 2.dp.toPx())
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "Sensível", 45 to "Normal", 65 to "Restrito").forEach { (value, label) ->
                OutlinedButton(enabled = sensitivity != value, onClick = {
                    sensitivity = value
                    threshold.set(value)
                    stabilizer.reset()
                    stableCount = null
                    result = null
                }) { Text(label) }
            }
        }
        Text("Processamento local, sem captura nem upload. Detecta regiões visuais, não classes de objetos. Fundo irregular, movimento e sombras podem causar falsos positivos.",
            style = MaterialTheme.typography.bodySmall)
    }
}
