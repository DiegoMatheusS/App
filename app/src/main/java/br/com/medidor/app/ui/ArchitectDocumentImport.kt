package br.com.medidor.app.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import br.com.medidor.app.domain.DxfPreview
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Safely turns a chosen PDF page or ASCII DXF LINE drawing into a plan bitmap. */
internal object ArchitectDocumentImport {
    fun pageCount(resolver: ContentResolver, uri: Uri): Int {
        val handle = resolver.openFileDescriptor(uri, "r") ?: error("PDF não pode ser aberto.")
        PdfRenderer(handle).use { pdf ->
            return pdf.pageCount.coerceAtMost(1000)
        }
    }

    fun pdfPage(resolver: ContentResolver, uri: Uri, pageIndex: Int): Bitmap {
        val handle = resolver.openFileDescriptor(uri, "r") ?: error("PDF não pode ser aberto.")
        PdfRenderer(handle).use { pdf ->
            require(pageIndex in 0 until pdf.pageCount) { "Página não encontrada no PDF." }
            pdf.openPage(pageIndex).use { page ->
                val ratio = min(2048f / page.width, 2048f / page.height).coerceAtMost(3f)
                val w = (page.width * ratio).toInt().coerceIn(1,2048)
                val h = (page.height * ratio).toInt().coerceIn(1,2048)
                val image = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                try {
                    image.eraseColor(Color.WHITE)
                    page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return image
                } catch (error: Exception) {
                    image.recycle()
                    throw error
                }
            }
        }
    }

    fun dxf(resolver: ContentResolver, uri: Uri): Bitmap {
        val input = resolver.openInputStream(uri) ?: error("DXF não pode ser aberto.")
        val text = input.use {
            val data = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n == -1) break
                require(data.size() + n <= 4_000_000) { "DXF acima de 4 MB." }
                data.write(buffer,0,n)
            }
            data.toString(Charsets.UTF_8.name())
        }
        val lines = DxfPreview.parse(text)
        val minX = lines.minOf { min(it.x1,it.x2) }
        val maxX = lines.maxOf { max(it.x1,it.x2) }
        val minY = lines.minOf { min(it.y1,it.y2) }
        val maxY = lines.maxOf { max(it.y1,it.y2) }
        val spanX = maxX-minX
        val spanY = maxY-minY
        require(spanX.isFinite() && spanY.isFinite() && max(spanX,spanY) > 1e-6) {
            "DXF sem área desenhada."
        }
        val scale = 1840.0 / max(spanX,spanY)
        val w = (spanX * scale + 160).toInt().coerceIn(160,2048)
        val h = (spanY * scale + 160).toInt().coerceIn(160,2048)
        val result = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(31,68,99)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
            }
            lines.forEach { line ->
                val x0 = (80+(line.x1-minX)*scale).toFloat()
                val x1 = (80+(line.x2-minX)*scale).toFloat()
                val y0 = (h-80-(line.y1-minY)*scale).toFloat()
                val y1 = (h-80-(line.y2-minY)*scale).toFloat()
                canvas.drawLine(x0,y0,x1,y1,paint)
            }
            return result
        } catch (e: Exception) {
            result.recycle()
            throw e
        }
    }
}
