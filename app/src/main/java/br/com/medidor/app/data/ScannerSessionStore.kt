package br.com.medidor.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.medidor.app.domain.ScannerCaptureGuide
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ScannerSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val frames: Map<Int, String>
) {
    val capturedCount: Int get() = frames.size
    val isComplete: Boolean get() = ScannerCaptureGuide.complete(frames.keys)
}

/**
 * Keeps capture sessions in app-private storage; the prototype has no 3D solver.
 * Photos are unique to their slots. No image is uploaded or sent to third parties.
 */
class ScannerSessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("scanner_capture_v1", Context.MODE_PRIVATE)
    private val folder = File(context.applicationContext.filesDir, "scanner").apply { mkdirs() }

    @Synchronized fun list(): List<ScannerSession> {
        val json = prefs.getString("sessions", "[]") ?: "[]"
        val array = JSONArray(json)
        require(array.length() <= ScannerCaptureGuide.MAX_SESSIONS) { "Sessões além do limite." }
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val photos = obj.optJSONObject("frames") ?: JSONObject()
            val frames = mutableMapOf<Int, String>()
            val keys = photos.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val slot = key.toIntOrNull()
                require(slot != null && slot in 0 until ScannerCaptureGuide.REQUIRED_SHOTS) {
                    "Posição inválida na sessão."
                }
                val filename = photos.getString(key)
                require(isValidFilename(filename)) { "Nome de foto inválido." }
                frames[slot] = filename
            }
            ScannerSession(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAt = obj.getLong("createdAt"),
                frames = frames
            )
        }
    }

    @Synchronized fun create(name: String): ScannerSession {
        val normalized = name.trim()
        require(normalized.length in 1..60) { "Nome deve conter entre 1 e 60 caracteres." }
        val sessions = list()
        require(sessions.size < ScannerCaptureGuide.MAX_SESSIONS) {
            "Limite de 8 sessões. Exclua uma sessão antes de criar outra."
        }
        val session = ScannerSession(UUID.randomUUID().toString(), normalized,
            System.currentTimeMillis(), emptyMap())
        persist(listOf(session) + sessions)
        return session
    }

    @Synchronized fun rename(id: String, name: String) {
        val normalized = name.trim()
        require(normalized.length in 1..60) { "Nome inválido." }
        val sessions = list()
        require(sessions.any { it.id == id }) { "Sessão não encontrada." }
        persist(sessions.map { if (it.id == id) it.copy(name = normalized) else it })
    }

    /**
     * Replace a shot only after the new image is written AND the session index
     * has been committed; previous image stays recoverable until then.
     */
    @Synchronized fun saveShot(id: String, position: Int, bitmap: Bitmap): ScannerSession {
        require(position in 0 until ScannerCaptureGuide.REQUIRED_SHOTS)
        require(bitmap.width in 1..4096 && bitmap.height in 1..4096) {
            "Imagem inválida ou grande demais."
        }
        val sessions = list()
        val session = sessions.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Sessão não encontrada.")
        val filename = "scan_${UUID.randomUUID()}.jpg"
        val target = File(folder, filename)
        try {
            target.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)) {
                    "Não foi possível salvar a foto."
                }
            }
            require(target.length() in 1..MAX_SHOT_BYTES) {
                "Fotografia grande demais (máximo 8 MB)."
            }
            val updated = session.copy(frames = session.frames + (position to filename))
            persist(sessions.map { if (it.id == id) updated else it })
            session.frames[position]?.let { oldName -> safeFile(oldName)?.delete() }
            return updated
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    @Synchronized fun deleteShot(id: String, position: Int): ScannerSession {
        val sessions = list()
        val session = sessions.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Sessão não encontrada.")
        val old = session.frames[position] ?: return session
        val updated = session.copy(frames = session.frames - position)
        persist(sessions.map { if (it.id == id) updated else it })
        safeFile(old)?.delete()
        return updated
    }

    @Synchronized fun deleteSession(id: String) {
        val sessions = list()
        val session = sessions.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Sessão não encontrada.")
        persist(sessions.filterNot { it.id == id })
        session.frames.values.forEach { safeFile(it)?.delete() }
    }

    fun thumbnail(fileName: String?): Bitmap? {
        val file = safeFile(fileName) ?: return null
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > 512) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun safeFile(name: String?): File? =
        name?.takeIf(::isValidFilename)?.let { File(folder, it) }

    private fun isValidFilename(name: String): Boolean =
        name.startsWith("scan_") && name.endsWith(".jpg") &&
            name.length in 12..90 &&
            name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' } &&
            !name.contains("..")

    private fun persist(sessions: List<ScannerSession>) {
        require(sessions.size <= ScannerCaptureGuide.MAX_SESSIONS)
        val array = JSONArray().also { collection ->
            sessions.forEach { session ->
                require(ScannerCaptureGuide.validSlots(session.frames.keys))
                collection.put(JSONObject().apply {
                    put("id", session.id)
                    put("name", session.name)
                    put("createdAt", session.createdAt)
                    put("frames", JSONObject().also { photos ->
                        session.frames.forEach { (position, name) ->
                            photos.put(position.toString(), name)
                        }
                    })
                })
            }
        }
        check(prefs.edit().putString("sessions", array.toString()).commit()) {
            "Falha ao salvar sessão de scanner."
        }
    }

    companion object { private const val MAX_SHOT_BYTES = 8L * 1024 * 1024 }
}
