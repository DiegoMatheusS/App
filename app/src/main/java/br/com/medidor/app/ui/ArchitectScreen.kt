package br.com.medidor.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.com.medidor.app.data.ArchitectPlanStore
import br.com.medidor.app.domain.ArchitectPlan
import br.com.medidor.app.domain.FloorPlanMath
import br.com.medidor.app.domain.PlanFloor
import br.com.medidor.app.domain.PlanPoint
import br.com.medidor.app.domain.PlanWall
import android.graphics.Bitmap
import android.graphics.Paint
import java.util.Locale
import kotlin.math.min

/**
 * Non-AI floor editor. No automatic floorplan interpretation/AR tracking here:
 * walls and reference line are traced by the user on a perspective-correct plan.
 */
@Composable
fun ArchitectScreen() {
    val context = LocalContext.current
    val repository = remember(context) { ArchitectPlanStore(context.applicationContext) }
    var project by remember { mutableStateOf(repository.load()) }
    var editingMode by remember { mutableStateOf("reference") }
    var pending by remember { mutableStateOf<PlanPoint?>(null) }
    var showLines by rememberSaveable { mutableStateOf(true) }
    var showMeasurements by rememberSaveable { mutableStateOf(true) }
    var showAllFloors by rememberSaveable { mutableStateOf(true) }
    var preview3D by rememberSaveable { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selected = project.selectedFloor
    val current = project.floors[selected]
    var referenceText by remember(selected) { mutableStateOf(current.knownMeters.toString()) }
    var wallHeightText by remember(selected) { mutableStateOf(current.wallHeightMeters.toString()) }

    fun persist(next: ArchitectPlan) {
        try {
            repository.save(next)
            project = next
            error = null
        } catch (e: Exception) {
            error = "Não foi possível salvar o projeto: ${e.localizedMessage}"
        }
    }
    fun editFloor(next: PlanFloor) {
        val updated = project.floors.toMutableList().apply { set(selected, next) }
        persist(project.copy(floors = updated))
    }
    fun chooseFloor(index: Int) {
        if (index == selected) return
        pending = null
        editingMode = "reference"
        persist(project.copy(selectedFloor = index))
    }

    val openImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val photo = SampledPhotos.fromUri(context.contentResolver, uri)
                val name = repository.saveImage(photo)
                editFloor(current.copy(
                    imageFile = name,
                    imageWidth = photo.width,
                    imageHeight = photo.height,
                    referenceStart = null,
                    referenceEnd = null,
                    walls = emptyList()
                ))
                editingMode = "reference"
                pending = null
            } catch (e: Exception) {
                error = "Falha ao importar planta: ${e.localizedMessage}"
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Apagar paredes deste andar?") },
            text = { Text("A planta e a referência de escala serão mantidas.") },
            confirmButton = {
                TextButton(onClick = {
                    editFloor(current.copy(walls = emptyList()))
                    pending = null
                    confirmErase = false
                }) { Text("Apagar paredes") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } }
        )
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Editor de plantas — prévia", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Importe uma planta vista de cima, marque uma referência real e desenhe as paredes tocando nos extremos. " +
            "O projeto é salvo offline. Ainda não há leitura automática, PDF ou AR.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Pavimentos", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            project.floors.forEachIndexed { index, f ->
                FilterChip(
                    selected = index == selected,
                    onClick = { chooseFloor(index) },
                    label = { Text(f.title) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = project.floors.size < 6, onClick = {
                val count = project.floors.size
                persist(project.copy(
                    floors = project.floors + PlanFloor("Andar ${count + 1}"),
                    selectedFloor = count
                ))
                pending = null
                editingMode = "reference"
            }) { Text("+ Andar") }
            OutlinedButton(enabled = project.floors.size < 6, onClick = {
                val count = project.floors.size
                persist(project.copy(
                    floors = project.floors + current.copy(title = "Andar ${count + 1}"),
                    selectedFloor = count
                ))
                pending = null
                editingMode = "walls"
            }) { Text("Duplicar") }
        }
        Text("Cada pavimento pode ter sua própria planta e escala. O alinhamento entre pavimentos " +
            "na prévia é esquemático; escadas e deslocamentos ainda não são modelados.",
            style = MaterialTheme.typography.bodySmall)

        OutlinedButton(onClick = { openImage.launch("image/*") }) {
            Text(if (current.imageFile == null) "Importar planta (imagem)" else "Trocar planta deste andar")
        }
        val bitmap = remember(current.imageFile) { repository.readImage(current.imageFile) }
        if (bitmap != null) {
            Text("Escala: informe uma medida conhecida na planta (metros).")
            OutlinedTextField(
                value = referenceText,
                onValueChange = { referenceText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Distância da linha de referência (m)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedButton(onClick = {
                val meters = referenceText.trim().replace(',', '.').toDoubleOrNull()
                if (meters != null && meters.isFinite() && meters > 0) {
                    editFloor(current.copy(knownMeters = meters))
                } else {
                    error = "Informe uma medida positiva em metros."
                }
            }) { Text("Aplicar medida de referência") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = editingMode == "reference", onClick = {
                    editingMode = "reference"; pending = null
                }, label = { Text("Marcar escala") })
                FilterChip(
                    selected = editingMode == "walls",
                    onClick = { editingMode = "walls"; pending = null },
                    enabled = current.calibrated,
                    label = { Text("Desenhar paredes") }
                )
            }
            Text(
                when {
                    pending == null && editingMode == "reference" ->
                        "Toque nos dois extremos da distância conhecida."
                    pending != null && editingMode == "reference" ->
                        "Agora toque no segundo extremo da referência."
                    !current.calibrated -> "Calibre a escala antes de desenhar paredes."
                    pending == null -> "Toque no início de uma parede."
                    else -> "Toque no fim da parede. Repita para as demais."
                },
                color = MaterialTheme.colorScheme.primary
            )
            FloorPlanCanvas(
                bitmap = bitmap,
                floor = current,
                pending = pending,
                showLines = showLines,
                showMeasurements = showMeasurements,
                onTap = { point ->
                    val first = pending
                    if (first == null) {
                        pending = point
                    } else if (first != point) {
                        if (editingMode == "reference") {
                            editFloor(current.copy(referenceStart = first, referenceEnd = point))
                            editingMode = "walls"
                        } else if (current.calibrated) {
                            val wall = PlanWall(first, point)
                            if ((FloorPlanMath.wallLengthMeters(current, wall) ?: 0.0) > 0.01) {
                                editFloor(current.copy(walls = current.walls + wall))
                            }
                        }
                        pending = null
                    }
                }
            )
            if (pending != null) {
                OutlinedButton(onClick = { pending = null }) { Text("Cancelar ponto atual") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = current.walls.isNotEmpty(), onClick = {
                    editFloor(current.copy(walls = current.walls.dropLast(1)))
                    pending = null
                }) { Text("Desfazer parede") }
                OutlinedButton(enabled = current.walls.isNotEmpty(), onClick = {
                    confirmErase = true
                }) { Text("Limpar paredes") }
            }
            OutlinedTextField(
                value = wallHeightText,
                onValueChange = { wallHeightText = it },
                label = { Text("Pé-direito / altura das paredes (m)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedButton(onClick = {
                val height = wallHeightText.trim().replace(',', '.').toDoubleOrNull()
                if (height != null && height.isFinite() && height > 0 && height <= 20) {
                    editFloor(current.copy(wallHeightMeters = height))
                } else error = "A altura deve estar entre 0 e 20 m."
            }) { Text("Aplicar altura") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showLines, onClick = { showLines = !showLines },
                    label = { Text("Linhas") })
                FilterChip(selected = showMeasurements, onClick = { showMeasurements = !showMeasurements },
                    label = { Text("Cotas") })
            }
            val total = FloorPlanMath.totalWallMeters(current)
            Text("Paredes: ${current.walls.size} • Total desenhado: " +
                (if (total == null) "escala pendente" else "${formatMeters(total)} m"))
            Text("Medidas estimadas, válidas somente para planta plana e sem distorção de perspectiva.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Importe uma imagem da planta neste andar para iniciar a marcação.")
        }

        Text("Visualização", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !preview3D, onClick = { preview3D = false }, label = { Text("Planta 2D") })
            FilterChip(selected = preview3D, onClick = { preview3D = true }, label = { Text("Prévia 3D") })
        }
        if (preview3D) {
            FilterChip(selected = showAllFloors, onClick = { showAllFloors = !showAllFloors },
                label = { Text(if (showAllFloors) "Exibindo todos os andares" else "Somente este andar") })
            IsometricFloorPreview(
                plan = project,
                allFloors = showAllFloors,
                showLines = showLines
            )
            Text("Prévia isométrica simplificada das paredes marcadas; não é um scanner 3D, " +
                "modelo CAD nem realidade aumentada. Portas, janelas e telhado ainda não são gerados.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (showMeasurements && current.walls.isNotEmpty()) {
            Text("Comprimento de cada parede", style = MaterialTheme.typography.titleMedium)
            current.walls.forEachIndexed { index, wall ->
                Text("Parede ${index + 1}: ${FloorPlanMath.wallLengthMeters(current, wall)?.let {
                    "${formatMeters(it)} m"
                } ?: "sem escala"}")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun formatMeters(meters: Double): String =
    String.format(Locale("pt", "BR"), "%.2f", meters)

private data class PlanRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fittedRect(view: IntSize, bitmap: Bitmap): PlanRect {
    val scale = min(view.width.toFloat() / bitmap.width, view.height.toFloat() / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    return PlanRect((view.width - w) / 2, (view.height - h) / 2, w, h)
}

private fun planTouch(touch: Offset, size: IntSize, bitmap: Bitmap): PlanPoint? {
    val r = fittedRect(size, bitmap)
    if (r.width <= 0f || r.height <= 0f) return null
    val x = (touch.x - r.left) / r.width
    val y = (touch.y - r.top) / r.height
    return if (x in 0f..1f && y in 0f..1f) PlanPoint(x, y) else null
}

@Composable
private fun FloorPlanCanvas(
    bitmap: Bitmap,
    floor: PlanFloor,
    pending: PlanPoint?,
    showLines: Boolean,
    showMeasurements: Boolean,
    onTap: (PlanPoint) -> Unit
) {
    Box(Modifier.fillMaxWidth().height(350.dp).background(Color(0xFF26333A))) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Planta original importada",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap, floor, pending, onTap) {
                detectTapGestures { position ->
                    planTouch(position, size, bitmap)?.let(onTap)
                }
            }
        ) {
            val r = fittedRect(IntSize(size.width.toInt(), size.height.toInt()), bitmap)
            fun center(point: PlanPoint) =
                Offset(r.left + point.x * r.width, r.top + point.y * r.height)
            val refA = floor.referenceStart
            val refB = floor.referenceEnd
            if (refA != null && refB != null) {
                drawLine(Color(0xFFFF9800), center(refA), center(refB), 3.dp.toPx())
            }
            if (showLines) {
                floor.walls.forEach { wall ->
                    drawLine(Color(0xFF14AD9C), center(wall.start), center(wall.end), 4.dp.toPx())
                }
            }
            if (showMeasurements) {
                drawIntoCanvas { surface ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(2, 80, 79)
                        textSize = 12.dp.toPx()
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(3f, 0f, 1f, android.graphics.Color.WHITE)
                    }
                    floor.walls.forEach { wall ->
                        val value = FloorPlanMath.wallLengthMeters(floor, wall) ?: return@forEach
                        val a = center(wall.start)
                        val b = center(wall.end)
                        surface.nativeCanvas.drawText(
                            "${formatMeters(value)} m",
                            (a.x + b.x) / 2,
                            (a.y + b.y) / 2 - 8.dp.toPx(),
                            paint
                        )
                    }
                }
            }
            pending?.let { drawCircle(Color(0xFFF44336), 7.dp.toPx(), center(it)) }
        }
    }
}

/** Schematic isometric projection of individually traced wall segments. */
@Composable
private fun IsometricFloorPreview(plan: ArchitectPlan, allFloors: Boolean, showLines: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFFECF2F5))) {
        data class Shape(val corners: List<Offset>, val floorIndex: Int)
        fun project(x: Double, y: Double, z: Double) = Offset(
            ((x - y) * 0.8).toFloat(),
            ((x + y) * 0.42 - z).toFloat()
        )
        val shapes = mutableListOf<Shape>()
        val floors = if (allFloors) plan.floors.withIndex().toList()
            else listOf(plan.floors.withIndex().elementAt(plan.selectedFloor))
        for ((index, floor) in floors) {
            val baseHeight = if (allFloors) plan.floors.take(index).sumOf { it.wallHeightMeters } else 0.0
            for (wall in floor.walls) {
                val a = FloorPlanMath.worldPointMeters(floor, wall.start) ?: continue
                val b = FloorPlanMath.worldPointMeters(floor, wall.end) ?: continue
                shapes += Shape(listOf(
                    project(a.first, a.second, baseHeight),
                    project(b.first, b.second, baseHeight),
                    project(b.first, b.second, baseHeight + floor.wallHeightMeters),
                    project(a.first, a.second, baseHeight + floor.wallHeightMeters)
                ), index)
            }
        }
        val coords = shapes.flatMap { it.corners }
        if (coords.isNotEmpty()) {
            val lowX = coords.minOf { it.x }; val highX = coords.maxOf { it.x }
            val lowY = coords.minOf { it.y }; val highY = coords.maxOf { it.y }
            val ratio = min(
                (size.width - 32.dp.toPx()) / (highX - lowX).coerceAtLeast(1f),
                (size.height - 32.dp.toPx()) / (highY - lowY).coerceAtLeast(1f)
            ).coerceAtMost(100f)
            val dx = (size.width - (highX - lowX) * ratio) / 2
            val dy = (size.height - (highY - lowY) * ratio) / 2
            shapes.forEach { shape ->
                val vertices = shape.corners.map { p ->
                    Offset(dx + (p.x - lowX) * ratio, dy + (p.y - lowY) * ratio)
                }
                val polygon = Path().apply {
                    moveTo(vertices.first().x, vertices.first().y)
                    vertices.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                val fill = if (shape.floorIndex % 2 == 0) Color(0xFF3799A9) else Color(0xFF8BB5A4)
                drawPath(polygon, fill.copy(alpha = 0.75f))
                if (showLines) {
                    drawLine(Color(0xFF234C59), vertices[0], vertices[1], 1.3.dp.toPx())
                    drawLine(Color(0xFF234C59), vertices[2], vertices[3], 1.3.dp.toPx())
                    drawLine(Color(0xFF234C59), vertices[0], vertices[3], 1.3.dp.toPx())
                    drawLine(Color(0xFF234C59), vertices[1], vertices[2], 1.3.dp.toPx())
                }
            }
        }
    }
}
