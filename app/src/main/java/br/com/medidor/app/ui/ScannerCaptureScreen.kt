package br.com.medidor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.medidor.app.data.ScannerSessionStore
import br.com.medidor.app.domain.ScanPass
import br.com.medidor.app.domain.ScannerCaptureGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Capture-only first milestone: photos saved locally, with no photogrammetry or
 * misleading measurement/reconstruction button.
 */
@Composable
fun ScannerCaptureScreen() {
    val context = LocalContext.current
    val store = remember(context) { ScannerSessionStore(context.applicationContext) }
    var sessions by remember { mutableStateOf(store.list()) }
    var activeId by remember { mutableStateOf(sessions.firstOrNull()?.id) }
    val active = sessions.firstOrNull { it.id == activeId }
    var position by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    var dialog by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(id: String?) {
        sessions = store.list()
        activeId = id?.takeIf { requested -> sessions.any { it.id == requested } }
            ?: sessions.firstOrNull()?.id
        refresh++
    }

    fun saveShot(bitmap: Bitmap, sessionId: String, slot: Int) {
        showCamera = false
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    try { store.saveShot(sessionId, slot, bitmap) }
                    finally { bitmap.recycle() }
                }
                reload(result.id)
                position = ScannerCaptureGuide.nextMissing(result.frames.keys) ?: slot
                feedback = "Foto salva no armazenamento local."
            } catch (e: Exception) {
                if (!bitmap.isRecycled) bitmap.recycle()
                feedback = "Erro ao salvar foto: ${e.localizedMessage}"
            } finally {
                busy = false
            }
        }
    }

    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) showCamera = true
        else feedback = "Câmera não autorizada. Você pode importar fotos pela galeria."
    }
    var exportingId by remember { mutableStateOf<String?>(null) }
    val exportZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = exportingId
        exportingId = null
        if (uri != null && id != null && !busy) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("Não foi possível criar o ZIP.")
                    store.exportSession(id, output)
                }
                feedback = "Cópia ZIP criada. Guarde o arquivo em local seguro."
            } catch (e: Exception) {
                feedback = "Falha ao exportar sessão: ${e.localizedMessage}"
            } finally {
                busy = false
            }
        }
    }
    val importZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                val restored = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Não foi possível abrir o ZIP.")
                    store.importSession(input)
                }
                reload(restored.id)
                position = ScannerCaptureGuide.nextMissing(restored.frames.keys) ?: 0
                showCamera = false
                feedback = "Sessão recuperada em uma cópia separada, sem apagar as anteriores."
            } catch (e: Exception) {
                feedback = "ZIP não restaurado: ${e.localizedMessage}"
            } finally {
                busy = false
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val sessionId = activeId
        val slot = position
        if (uri != null && sessionId != null && !busy) {
            scope.launch {
                busy = true
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        SampledPhotos.fromUri(context.contentResolver, uri)
                    }
                    val saved = withContext(Dispatchers.IO) {
                        try { store.saveShot(sessionId, slot, bitmap) }
                        finally { bitmap.recycle() }
                    }
                    reload(saved.id)
                    position = ScannerCaptureGuide.nextMissing(saved.frames.keys) ?: slot
                    feedback = "Imagem importada e salva localmente."
                } catch (e: Exception) {
                    feedback = "Não foi possível importar: ${e.localizedMessage}"
                } finally {
                    busy = false
                }
            }
        }
    }

    if (dialog.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(when (dialog) {
                "create" -> "Nova sessão"
                "rename" -> "Renomear sessão"
                "deleteShot" -> "Excluir fotografia?"
                else -> "Excluir toda a sessão?"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (dialog == "delete" || dialog == "deleteShot")
                        "A ação é permanente. Use Exportar sessão ZIP para preservar estas fotos " +
                            "antes de excluir."
                    else "Dê um nome ao objeto que será fotografado.")
                    if (dialog == "create" || dialog == "rename") {
                        OutlinedTextField(
                            value = nameText, onValueChange = { nameText = it },
                            label = { Text("Nome (até 60 caracteres)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && (dialog == "delete" || dialog == "deleteShot" ||
                        nameText.trim().length in 1..60),
                    onClick = {
                        try {
                            when (dialog) {
                                "create" -> {
                                    val session = store.create(nameText)
                                    reload(session.id)
                                    position = 0
                                    showCamera = false
                                }
                                "rename" -> active?.let {
                                    store.rename(it.id, nameText)
                                    reload(it.id)
                                }
                                "delete" -> active?.let {
                                    store.deleteSession(it.id)
                                    reload(null)
                                    position = 0
                                    showCamera = false
                                }
                                "deleteShot" -> active?.let {
                                    store.deleteShot(it.id, position)
                                    reload(it.id)
                                }
                            }
                            feedback = null
                        } catch (e: Exception) {
                            feedback = "Operação não concluída: ${e.localizedMessage}"
                        }
                        dialog = ""
                    }
                ) { Text(if (dialog.startsWith("delete")) "Excluir" else "Salvar") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancelar") } }
        )
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scanner 3D — captura guiada", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Etapa experimental de aquisição de fotografias. Ainda NÃO cria modelos 3D, " +
            "não mede profundidade e não faz fotogrametria automática.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Deixe o objeto imóvel e caminhe ao redor, mantendo boa iluminação. " +
            "Evite objetos transparentes, brilhantes ou em movimento. " +
            "Mantenha sobreposição entre fotos e não use zoom digital.",
            style = MaterialTheme.typography.bodySmall)

        Text("Sessões locais (${sessions.size}/${ScannerCaptureGuide.MAX_SESSIONS})",
            style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sessions.forEach { session ->
                FilterChip(
                    selected = activeId == session.id,
                    onClick = {
                        activeId = session.id
                        position = ScannerCaptureGuide.nextMissing(session.frames.keys) ?: 0
                        showCamera = false
                        feedback = null
                    },
                    label = { Text("${session.name} (${session.capturedCount}/20)") }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy && sessions.size < ScannerCaptureGuide.MAX_SESSIONS,
                onClick = {
                    nameText = "Objeto ${sessions.size + 1}"
                    dialog = "create"
                }
            ) { Text("Nova sessão") }
            if (active != null) {
                OutlinedButton(enabled = !busy, onClick = {
                    nameText = active.name
                    dialog = "rename"
                }) { Text("Renomear") }
                OutlinedButton(enabled = !busy, onClick = {
                    dialog = "delete"
                }) { Text("Excluir sessão") }
            }
        }

        Text("Backup separado das sessões do Scanner:",
            style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy && active != null, onClick = {
                exportingId = active?.id
                exportZip.launch("scanner-medidor.zip")
            }) { Text("Exportar sessão ZIP") }
            OutlinedButton(
                enabled = !busy && sessions.size < ScannerCaptureGuide.MAX_SESSIONS,
                onClick = {
                    importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            ) { Text("Importar sessão ZIP") }
        }
        Text("Cada ZIP guarda uma sessão com suas fotos. A importação cria uma nova sessão, " +
            "verificando tamanho, arquivos e hashes SHA-256 antes de salvar. " +
            "Guarde os ZIPs em local seguro; não são criptografados.",
            style = MaterialTheme.typography.bodySmall)

        if (active == null) {
            Text("Crie uma sessão para começar a fotografar.")
        } else {
            val captured = active.frames.keys
            val step = ScannerCaptureGuide.positions[position]
            Text("Fotografias registradas: ${active.capturedCount}/20",
                style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { active.capturedCount / 20f },
                modifier = Modifier.fillMaxWidth()
            )
            if (active.isComplete) {
                Text("As 20 posições sugeridas foram fotografadas. " +
                    "Os arquivos estão salvos, mas a reconstrução do modelo 3D ainda não está implementada.",
                    color = MaterialTheme.colorScheme.primary)
            }
            Text("Selecione a posição da fotografia:", fontWeight = FontWeight.SemiBold)
            for (pass in ScanPass.entries) {
                Text(pass.label, style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScannerCaptureGuide.positions.filter { it.pass == pass }.forEach { item ->
                        FilterChip(
                            selected = position == item.index,
                            onClick = { position = item.index; showCamera = false; feedback = null },
                            label = { Text("${item.index + 1}${if (item.index in captured) " ✓" else ""}") }
                        )
                    }
                }
            }
            Text("Posição ${step.index + 1}/20 — ${step.label}",
                style = MaterialTheme.typography.titleMedium)
            Text(step.instruction)
            Text("Foto ${if (position in captured) "salva; pode ser refeita" else "pendente"}")
            val thumb = remember(active.id, position, refresh) {
                store.thumbnail(active.frames[position])
            }
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "Fotografia armazenada para a posição ${position + 1}",
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    gallery.launch("image/*")
                }) { Text(if (position in captured) "Trocar pela galeria" else "Importar da galeria") }
                Button(enabled = !busy, onClick = {
                    if (cameraGranted) showCamera = !showCamera
                    else askCamera.launch(Manifest.permission.CAMERA)
                }) { Text(if (showCamera) "Fechar câmera" else "Abrir câmera") }
            }
            if (showCamera && cameraGranted && !busy) {
                CameraPreviewAndCapture(
                    onPhoto = { bitmap ->
                        val id = activeId
                        if (id != null) saveShot(bitmap, id, position)
                        else bitmap.recycle()
                    },
                    onError = { feedback = it }
                )
            }
            if (position in captured) {
                OutlinedButton(enabled = !busy, onClick = { dialog = "deleteShot" }) {
                    Text("Excluir foto desta posição")
                }
            }
            if (!active.isComplete) {
                OutlinedButton(enabled = !busy, onClick = {
                    position = ScannerCaptureGuide.nextMissing(captured) ?: 0
                    showCamera = false
                }) { Text("Ir para a próxima foto pendente") }
            }
            Text("O backup geral do Modo Arquiteto não inclui estas fotos. " +
                "Exporte cada sessão do Scanner em ZIP antes de desinstalar o aplicativo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
        if (busy) Text("Salvando fotografia…")
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
