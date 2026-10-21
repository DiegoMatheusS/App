package br.com.medidor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import br.com.medidor.app.domain.Geometry
import br.com.medidor.app.domain.TankCalculator
import br.com.medidor.app.domain.TankShape
import java.text.NumberFormat
import java.util.Locale

internal fun parseDimension(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
internal fun parseLevel(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
internal fun decimal(value: Double): String =
    NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 3 }.format(value)

@Composable
internal fun DimensionField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun BoxCalculatorScreen(onSave: (String, String) -> Unit) {
    var length by rememberSaveable { mutableStateOf("0,50") }
    var width by rememberSaveable { mutableStateOf("0,40") }
    var height by rememberSaveable { mutableStateOf("0,30") }
    var saved by remember { mutableStateOf(false) }
    val l = parseDimension(length)
    val w = parseDimension(width)
    val h = parseDimension(height)
    val valid = l != null && w != null && h != null
    val volume = if (valid) Geometry.rectangularVolume(l!!, w!!, h!!) else null
    val area = if (valid) Geometry.rectangularFloorArea(l!!, w!!) else null
    val surface = if (valid) Geometry.rectangularSurfaceArea(l!!, w!!, h!!) else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Caixa ou ambiente retangular", style = MaterialTheme.typography.titleLarge)
        Text("Informe as medidas internas em metros para calcular a capacidade útil.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DimensionField("Comprimento (m)", length) { length = it; saved = false }
        DimensionField("Largura (m)", width) { width = it; saved = false }
        DimensionField("Altura / profundidade (m)", height) { height = it; saved = false }
        if (!valid) {
            Text("Preencha todas as dimensões com números positivos.", color = MaterialTheme.colorScheme.error)
        } else {
            ResultCard("Volume", "${decimal(volume!!)} m³  •  ${decimal(Geometry.liters(volume))} L")
            ResultCard("Área da base", "${decimal(area!!)} m²")
            ResultCard("Área externa ideal", "${decimal(surface!!)} m²")
        }
        Button(enabled = valid, onClick = {
            onSave("Caixa / ambiente", "Volume: ${decimal(volume!!)} m³ | Base: ${decimal(area!!)} m²")
            saved = true
        }) { Text("Salvar medição") }
        if (saved) Text("Medição salva neste aparelho.", color = MaterialTheme.colorScheme.primary)
        Text("Valores geométricos. Para capacidade interna real, use dimensões internas.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TankCalculatorScreen(onSave: (String, String) -> Unit) {
    var shape by rememberSaveable { mutableStateOf(TankShape.VERTICAL.name) }
    var diameter by rememberSaveable { mutableStateOf("8") }
    var size by rememberSaveable { mutableStateOf("12") }
    var level by rememberSaveable { mutableStateOf("5") }
    var saved by remember { mutableStateOf(false) }
    val selected = TankShape.valueOf(shape)
    val d = parseDimension(diameter)
    val s = if (selected == TankShape.SPHERE) d else parseDimension(size)
    val lv = parseLevel(level)
    val maxLevel = if (selected == TankShape.VERTICAL) s else d
    val valid = d != null && s != null && lv != null && maxLevel != null && lv <= maxLevel
    val result = if (valid) TankCalculator.calculate(selected, d!!, s!!, lv!!) else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Geometria do reservatório", style = MaterialTheme.typography.titleLarge)
        Text("Use dimensões INTERNAS em metros. Tanques ideais, sem acessórios e sem perdas.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TankShape.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { shape = option.name; saved = false },
                label = { Text(option.label) }
            )
        }
        DimensionField("Diâmetro interno (m)", diameter) { diameter = it; saved = false }
        if (selected != TankShape.SPHERE) {
            DimensionField(if (selected == TankShape.VERTICAL) "Altura útil (m)" else "Comprimento interno (m)",
                size) { size = it; saved = false }
        }
        DimensionField("Nível do líquido a partir do fundo (m)", level) { level = it; saved = false }
        if (!valid) {
            Text("Verifique as medidas. O nível deve ficar entre zero e ${maxLevel?.let(::decimal) ?: "a altura"} m.",
                color = MaterialTheme.colorScheme.error)
        } else {
            ResultCard("Capacidade geométrica", "${decimal(result!!.capacityM3)} m³ • ${decimal(Geometry.liters(result.capacityM3))} L")
            ResultCard("Volume ocupado", "${decimal(result.occupiedM3)} m³ • ${decimal(Geometry.liters(result.occupiedM3))} L")
            ResultCard("Disponível", "${decimal(result.freeM3)} m³ • ${decimal(result.fillPercent)}% preenchido")
        }
        Button(enabled = valid, onClick = {
            onSave(selected.label, "Capacidade: ${decimal(result!!.capacityM3)} m³ | Ocupado: ${decimal(result.occupiedM3)} m³")
            saved = true
        }) { Text("Salvar cálculo") }
        if (saved) Text("Cálculo salvo neste aparelho.", color = MaterialTheme.colorScheme.primary)
        Text("Não utilizar como laudo de arqueação ou medição fiscal de tanque.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ResultCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
