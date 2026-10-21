package br.com.medidor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import br.com.medidor.app.data.LocalHistory
import br.com.medidor.app.data.MeasurementRecord

enum class Destination(val label: String) {
    HOME("Medidor"),
    BOX("Medir e calcular"),
    TANK("Tanques e reservatórios"),
    PHOTO("Medir por fotografia"),
    HISTORY("Minhas medições"),
    PRO("Pro completo"),
    ARCHITECT("Modo Arquiteto"),
    SCANNER("Scanner 3D"),
    COUNT("Contador de objetos")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val context = LocalContext.current
    val store = remember(context) { LocalHistory(context.applicationContext) }
    var destinationName by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val destination = Destination.entries.firstOrNull { it.name == destinationName } ?: Destination.HOME
    var records by remember { mutableStateOf(store.load()) }
    fun navigate(to: Destination) { destinationName = to.name }
    fun addRecord(title: String, details: String) { records = store.save(title, details) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(destination.label, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                if (destination != Destination.HOME) {
                    TextButton(onClick = { navigate(Destination.HOME) }) { Text("Voltar") }
                }
            }
        )
    }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen(onOpen = ::navigate)
                Destination.BOX -> BoxCalculatorScreen(onSave = ::addRecord)
                Destination.TANK -> TankCalculatorScreen(onSave = ::addRecord)
                Destination.PHOTO -> CameraMeasurementScreen(onSave = ::addRecord)
                Destination.HISTORY -> HistoryScreen(records = records, onDeleteAll = {
                    store.clear()
                    records = emptyList()
                })
                Destination.PRO -> ProScreen()
                Destination.ARCHITECT -> PlannedFeatureScreen(
                    title = "Modo Arquiteto",
                    description = "Leitura assistida de plantas, paredes 3D, maquete AR sobre o papel e vários andares.",
                    stages = listOf("Importar planta e definir escala", "Marcar paredes e gerar maquete", "Ancorar com ARCore em aparelhos compatíveis", "Adicionar andares e cotas")
                )
                Destination.SCANNER -> PlannedFeatureScreen(
                    title = "Scanner 3D completo",
                    description = "Captura por vários ângulos e reconstrução por fotogrametria, sem IA generativa.",
                    stages = listOf("Captura guiada", "Reconstrução geométrica", "Escala e medição 3D", "Exportação GLB/OBJ")
                )
                Destination.COUNT -> PlannedFeatureScreen(
                    title = "Contagem de objetos",
                    description = "Contagem automática de tijolos, caixas e peças: módulo futuro com IA local e correção manual.",
                    stages = listOf("Anotar fotos de treino", "Treinar detector local", "Contar e marcar objetos visíveis", "Estimar pilhas com indicação de incerteza")
                )
            }
        }
    }
}
