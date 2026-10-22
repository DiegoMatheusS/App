package br.com.medidor.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.com.medidor.app.domain.LiveContourTracking
import br.com.medidor.app.domain.NormalizedPoint
import br.com.medidor.app.domain.PlanarContourResult
import br.com.medidor.app.domain.PlanarContourSegmentation
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private data class ContourOverlay(
    val width: Int,
    val height: Int,
    val contour: PlanarContourResult?,
    val message: String
)

/**
 * CameraX preview + frame analyzer, deliberately independent from photo-based
 * calibrated measurements. Tap an object to resample a color region every
 * ~250ms. This is NOT an ML object tracker or a depth sensor.
 */
@Composable
internal fun LiveContourCamera(
    onPhoto: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = context as? ComponentActivity
    val latestPhoto by rememberUpdatedState(onPhoto)
    val latestError by rememberUpdatedState(onError)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val selected = remember { AtomicReference<NormalizedPoint?>(null) }
    val previous = remember { AtomicReference<PlanarContourResult?>(null) }
    var overlay by remember { mutableStateOf<ContourOverlay?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var tolerance by remember { mutableStateOf(32) }
    val threshold = remember { java.util.concurrent.atomic.AtomicInteger(32) }
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType=PreviewView.ScaleType.FIT_CENTER }
    }

    DisposableEffect(owner, previewView) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        if (owner != null) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) try {
                    val provider = future.get()
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                    val imageCapture = ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    var lastFrame = 0L
                    analysis.setAnalyzer(worker) { proxy ->
                        try {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastFrame >= 250) {
                                lastFrame = now
                                val sampled = LiveCameraFrameSampler.sample(proxy)
                                if (sampled != null) {
                                    val point = selected.get()
                                    val detected = if (point != null)
                                        PlanarContourSegmentation.segment(
                                            sampled.pixels, sampled.width, sampled.height,
                                            (point.x*(sampled.width-1)).toInt()
                                                .coerceIn(0,sampled.width-1),
                                            (point.y*(sampled.height-1)).toInt()
                                                .coerceIn(0,sampled.height-1),
                                            threshold.get()
                                        )
                                    else null
                                    val checked = if (point != null)
                                        LiveContourTracking.check(previous.get(),detected)
                                    else null
                                    previous.set(checked?.result)
                                    val frame = ContourOverlay(sampled.width,sampled.height,
                                        checked?.result,checked?.label
                                            ?: "Toque no objeto para tentar isolar o contorno.")
                                    if (!disposed) mainExecutor.execute {
                                        if (!disposed) overlay = frame
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (!disposed) mainExecutor.execute {
                                if (!disposed) latestError(
                                    e.localizedMessage ?: "Falha ao analisar os quadros.")
                            }
                        } finally { proxy.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,imageCapture,analysis)
                    boundProvider = provider
                    capture = imageCapture
                } catch (e: Exception) {
                    latestError(e.localizedMessage ?: "Não foi possível iniciar a câmera.")
                }
            },mainExecutor)
        } else latestError("A câmera exige uma Activity Android compatível.")
        onDispose {
            disposed = true
            boundProvider?.unbindAll()
            worker.shutdown()
            capture = null
            overlay = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contorno ao vivo — experimental",
            style = MaterialTheme.typography.titleMedium)
        Text("Toque no objeto para acompanhar aproximadamente uma região de cor semelhante. " +
            "Mantenha a câmera firme; selecione novamente se perder o contorno.",
            style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            AndroidView(factory = { previewView },modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize().pointerInput(overlay?.width,overlay?.height) {
                detectTapGestures { touch ->
                    val frame = overlay
                    if (frame != null) {
                        val point = liveTouchPoint(touch,size.width.toFloat(),
                            size.height.toFloat(),frame.width,frame.height)
                        if (point != null) {
                            selected.set(point)
                            previous.set(null)
                            overlay = frame.copy(contour = null,
                                message = "Buscando contorno no ponto selecionado…")
                        }
                    }
                }
            }) {
                val frame = overlay ?: return@Canvas
                val box = liveImageBox(size.width.toFloat(),size.height.toFloat(),
                    frame.width,frame.height)
                val c = frame.contour ?: return@Canvas
                val color = Color(0xFF44E8B5)
                val top = box.top + box.height*c.minY/c.height
                val bottom = box.top + box.height*(c.maxY+1)/c.height
                val left = box.left + box.width*c.minX/c.width
                val right = box.left + box.width*(c.maxX+1)/c.width
                drawLine(color,Offset(left,top),Offset(right,top),3.dp.toPx())
                drawLine(color,Offset(right,top),Offset(right,bottom),3.dp.toPx())
                drawLine(color,Offset(right,bottom),Offset(left,bottom),3.dp.toPx())
                drawLine(color,Offset(left,bottom),Offset(left,top),3.dp.toPx())
                c.outline.forEach { p ->
                    drawCircle(color,1.1.dp.toPx(),
                        Offset(box.left+box.width*(p.x.toFloat()/c.width),
                            box.top+box.height*(p.y.toFloat()/c.height)))
                }
            }
        }
        Text(overlay?.message ?: "Preparando câmera…",
            style = MaterialTheme.typography.bodyMedium)
        overlay?.contour?.let { contour ->
            Text("Largura: ${(contour.widthFraction*100).toInt()}% do quadro • " +
                "Altura: ${(contour.heightFraction*100).toInt()}% do quadro " +
                "(não são centímetros).",
                style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in listOf(18,32,55)) {
                OutlinedButton(onClick = {
                    tolerance = choice
                    threshold.set(choice)
                    previous.set(null)
                },enabled = tolerance != choice) {
                    Text(when(choice) {
                        18 -> "Restrito"
                        32 -> "Normal"
                        else -> "Amplo"
                    })
                }
            }
        }
        Button(enabled = capture != null,onClick = {
            val camera = capture ?: return@Button
            val file = File(context.cacheDir,
                "contorno_${SystemClock.elapsedRealtimeNanos()}.jpg")
            camera.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try { latestPhoto(SampledPhotos.fromFile(file)) }
                        catch (e: Exception) {
                            latestError(e.localizedMessage ?: "Falha ao abrir a foto.")
                        } finally { file.delete() }
                    }
                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        file.delete()
                        latestError(exception.localizedMessage ?: "Falha na captura.")
                    }
                }
            )
        }) { Text("Capturar foto para medir em cm") }
        Text("A seleção ao vivo NÃO mede distância, profundidade nem identifica " +
            "automaticamente o objeto. Para estimar centímetros, capture uma foto " +
            "e calibre uma referência física no mesmo plano. O contorno da câmera " +
            "pode não coincidir exatamente com o desenho sobreposto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PreviewBounds(val left:Float,val top:Float,val width:Float,val height:Float)

private fun liveImageBox(viewWidth:Float,viewHeight:Float,imageWidth:Int,imageHeight:Int):PreviewBounds {
    val ratio = min(viewWidth/imageWidth,viewHeight/imageHeight)
    val width = imageWidth*ratio
    val height = imageHeight*ratio
    return PreviewBounds((viewWidth-width)/2,(viewHeight-height)/2,width,height)
}

private fun liveTouchPoint(
    touch:Offset,viewWidth:Float,viewHeight:Float,imageWidth:Int,imageHeight:Int
):NormalizedPoint? {
    val rect=liveImageBox(viewWidth,viewHeight,imageWidth,imageHeight)
    if (rect.width<=0 || rect.height<=0) return null
    val x=(touch.x-rect.left)/rect.width
    val y=(touch.y-rect.top)/rect.height
    return if(x in 0f..1f && y in 0f..1f) NormalizedPoint(x,y) else null
}
