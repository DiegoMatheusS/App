package br.com.medidor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeasurementRecord(
    val title: String,
    val details: String,
    val createdAt: String
)

/**
 * Local prototype persistence without registration/server.
 * Replace with Room before adding complex project editing and migration.
 */
class LocalHistory(context: Context) {
    private val prefs = context.getSharedPreferences("measurements_v1", Context.MODE_PRIVATE)

    fun load(): List<MeasurementRecord> = try {
        val array = JSONArray(prefs.getString("records", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            MeasurementRecord(
                title = row.optString("title"),
                details = row.optString("details"),
                createdAt = row.optString("createdAt")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(title: String, details: String): List<MeasurementRecord> {
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        val updated = listOf(MeasurementRecord(title, details, timestamp)) + load()
        val limited = updated.take(100)
        persist(limited)
        return limited
    }

    fun clear() = prefs.edit().remove("records").apply()

    fun deleteAt(index: Int): List<MeasurementRecord> {
        val current = load()
        if (index !in current.indices) return current
        val updated = current.toMutableList().apply { removeAt(index) }
        persist(updated)
        return updated
    }

    private fun persist(records: List<MeasurementRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("title", record.title)
                put("details", record.details)
                put("createdAt", record.createdAt)
            })
        }
        prefs.edit().putString("records", array.toString()).apply()
    }
}
