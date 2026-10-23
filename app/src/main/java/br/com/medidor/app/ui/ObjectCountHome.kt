package br.com.medidor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ObjectCountHome(onSave: (String, String) -> Unit) {
    var live by remember { mutableStateOf(true) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = live, onClick = { live = true },
                label = { Text("Câmera ao vivo") })
            FilterChip(selected = !live, onClick = { live = false },
                label = { Text("Contar por foto") })
        }
        if (live) LiveObjectCountScreen() else ObjectCountScreen(onSave)
    }
}
