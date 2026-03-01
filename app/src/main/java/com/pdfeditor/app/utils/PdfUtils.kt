package com.pdfeditor.app.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.pdfeditor.app.model.Annotation
import com.pdfeditor.app.model.AnnotationType
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    fun copyUriToLocalFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(context, uri) ?: "document.pdf"
            val outputFile = File(context.cacheDir, fileName)
            FileOutputStream(outputFile).use { inputStream.copyTo(it) }
            outputFile
        } catch (e: Exception) { null }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(idx)
            }
        } catch (e: Exception) { "document.pdf" }
    }

    fun createBlankPdf(context: Context): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        page.canvas.drawColor(Color.WHITE)
        doc.finishPage(page)
        val file = File(context.cacheDir, "new_document_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun applyAnnotationsToPdf(
        context: Context,
        pdfFile: File,
        annotations: Map<Int, List<Annotation>>,
        totalPages: Int
    ) {
        // Annotations are shown as overlay in the editor
        // Full PDF writing requires additional libraries
    }

    fun writeFileToUri(context: Context, file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }
}
