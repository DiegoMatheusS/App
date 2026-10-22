package br.com.medidor.app.domain

/**
 * Conservative ASCII DXF preview importer. LINE entities are drawn as
 * reference geometry only; they are NOT automatically classified as walls.
 * Binary DXF, DWG, IFC and RVT need separate parsers/importers.
 */
data class DxfLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

object DxfPreview {
    fun parse(text: String): List<DxfLine> {
        require(text.length <= 4_000_000) { "Arquivo DXF muito grande (limite 4 MB)." }
        val rows = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        require(rows.size % 2 == 0) { "Estrutura DXF ASCII inválida." }
        val lines = mutableListOf<DxfLine>()
        var inEntities = false
        var sectionPending = false
        var entity: String? = null
        var values = mutableMapOf<Int, String>()
        fun flush() {
            if (inEntities && entity == "LINE") {
                val x1 = values[10]?.toDoubleOrNull()
                val y1 = values[20]?.toDoubleOrNull()
                val x2 = values[11]?.toDoubleOrNull()
                val y2 = values[21]?.toDoubleOrNull()
                if (x1 != null && y1 != null && x2 != null && y2 != null &&
                    listOf(x1,y1,x2,y2).all { it.isFinite() && kotlin.math.abs(it) <= 1e9 } &&
                    kotlin.math.hypot(x2-x1,y2-y1) > 1e-7) {
                    require(lines.size < 1500) { "DXF com mais de 1500 linhas." }
                    lines += DxfLine(x1,y1,x2,y2)
                }
            }
            values = mutableMapOf()
        }
        for (i in rows.indices step 2) {
            val code = rows[i].toIntOrNull()
                ?: throw IllegalArgumentException("Grupo DXF inválido.")
            val value = rows[i+1]
            if (code == 0) {
                flush()
                when (value.uppercase()) {
                    "SECTION" -> { sectionPending = true; entity = null }
                    "ENDSEC" -> { inEntities = false; entity = null }
                    "EOF" -> { entity = null }
                    else -> entity = value.uppercase()
                }
            } else if (sectionPending && code == 2) {
                inEntities = value.equals("ENTITIES",ignoreCase = true)
                sectionPending = false
            } else if (inEntities && entity == "LINE") {
                if (code in listOf(10,20,11,21)) values[code] = value
            }
        }
        flush()
        require(lines.isNotEmpty()) {
            "Nenhuma entidade LINE encontrada. Exporte o desenho como DXF ASCII com linhas."
        }
        return lines
    }
}
