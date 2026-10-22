package br.com.medidor.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onOpen: (Destination) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ferramentas de medição", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Calcule medidas e volumes offline. Capture uma foto e calibre com uma moeda conhecida.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolCard("Medir e calcular", "Caixas, áreas e volumes", "GRÁTIS", onClick = { onOpen(Destination.BOX) })
        ToolCard("Medir por fotografia", "Capture uma foto, marque a moeda e o objeto no mesmo plano", "GRÁTIS",
            onClick = { onOpen(Destination.PHOTO) })
        ToolCard("Tanques e reservatórios", "Cilíndricos e esféricos, volume total e ocupado", "GRÁTIS · BASE",
            onClick = { onOpen(Destination.TANK) })
        ToolCard("Minhas medições", "Registros salvos apenas neste aparelho", "GRÁTIS",
            onClick = { onOpen(Destination.HISTORY) })
        Spacer(Modifier.height(6.dp))
        Text("Próximos módulos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        ToolCard("Modo Arquiteto", "Plantas, portas, janelas e maquete 3D interativa", "PRÉVIA · PRO",
            onClick = { onOpen(Destination.ARCHITECT) })
        ToolCard("Scanner 3D completo", "Fotogrametria e visualização do modelo", "PLANEJADO · PRO",
            onClick = { onOpen(Destination.SCANNER) })
        ToolCard("Contador de objetos", "Tijolos, peças e caixas com IA local", "PLANEJADO · PRO",
            onClick = { onOpen(Destination.COUNT) })
        ToolCard("Pro completo", "Compra única planejada: R$ 129,90", "EM DESENVOLVIMENTO",
            onClick = { onOpen(Destination.PRO) })
        Text("Medições por foto são estimativas planas; não substituem instrumentos certificados.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, status: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
