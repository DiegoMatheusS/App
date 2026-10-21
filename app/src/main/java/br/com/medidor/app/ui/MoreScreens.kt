package br.com.medidor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.medidor.app.data.MeasurementRecord

@Composable
fun HistoryScreen(records: List<MeasurementRecord>, onDeleteAll: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Histórico local", style = MaterialTheme.typography.titleLarge)
        Text("Seus cálculos ficam salvos neste dispositivo. Ainda não há sincronização ou backup.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (records.isEmpty()) {
            ResultCard("Sem medições", "Faça um cálculo e toque em Salvar.")
        } else {
            records.forEach { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.title, fontWeight = FontWeight.SemiBold)
                        Text(record.details)
                        Text(record.createdAt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = onDeleteAll) { Text("Apagar histórico deste aparelho") }
        }
    }
}

@Composable
fun ProScreen() {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pro Completo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ResultCard("Preço de lançamento previsto", "R$ 129,90 • Compra única")
        Text("Funcionalidades previstas para desbloqueio completo:", style = MaterialTheme.typography.titleMedium)
        listOf(
            "Scanner 3D completo",
            "Modo Arquiteto com AR e vários andares",
            "Contador automático de objetos por IA local",
            "Ferramentas industriais avançadas",
            "Relatórios técnicos e exportação 3D"
        ).forEach { Text("• $it") }
        HorizontalDivider()
        Text("Em desenvolvimento — compras ainda não estão disponíveis.",
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("O aplicativo não cobra e não simula acesso Pro. A compra única será integrada à Google Play com validação e restauração.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlannedFeatureScreen(title: String, description: String, stages: List<String>) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Planejado para próxima fase", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description)
        Text("Etapas previstas:", style = MaterialTheme.typography.titleMedium)
        stages.forEachIndexed { index, stage -> Text("${index + 1}. $stage") }
        Text("Essa ferramenta ainda não realiza medições nesta versão.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
