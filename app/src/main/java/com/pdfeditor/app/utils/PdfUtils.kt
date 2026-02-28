package com.pdfeditor.app.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.pdfeditor.app.model.Annotation
import com.pdfeditor.app.model.AnnotationType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PdfUtils {

    fun copyUriToLocalFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileName(context, uri) ?: "document.pdf"
            val outputFile = File(context.cacheDir, fileName)
            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            "document.pdf"
        }
    }

    fun createBlankPdf(context: Context): File {
        PDFBoxResourceLoader.init(context)
        val document = PDDocument()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)

        val outputFile = File(context.cacheDir, "new_document_${System.currentTimeMillis()}.pdf")
        document.save(outputFile)
        document.close()
        return outputFile
    }

    /**
     * Applies in-memory annotations (drawn on top of rendered pages) back into the PDF file.
     * For each page that has annotations, it renders the page to a bitmap, draws annotations,
     * then embeds the result as a new page image.
     */
    fun applyAnnotationsToPdf(
        context: Context,
        pdfFile: File,
        annotations: Map<Int, List<Annotation>>,
        totalPages: Int
    ) {
        if (annotations.isEmpty()) return

        PDFBoxResourceLoader.init(context)
        val document = PDDocument.load(pdfFile)

        for ((pageIndex, pageAnnotations) in annotations) {
            if (pageIndex >= document.numberOfPages || pageAnnotations.isEmpty()) continue

            val page = document.getPage(pageIndex)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height

            try {
                val contentStream = PDPageContentStream(
                    document, page,
                    PDPageContentStream.AppendMode.APPEND, true
                )

                for (annotation in pageAnnotations) {
                    when (annotation.type) {
                        AnnotationType.TEXT -> {
                            val r = ((annotation.color shr 16) and 0xFF) / 255f
                            val g = ((annotation.color shr 8) and 0xFF) / 255f
                            val b = (annotation.color and 0xFF) / 255f
                            contentStream.setNonStrokingColor(r, g, b)
                            contentStream.beginText()
                            contentStream.setFont(PDType1Font.HELVETICA, 12f)
                            // Convert from bitmap coords to PDF coords (PDF origin is bottom-left)
                            val pdfX = annotation.x
                            val pdfY = pageHeight - annotation.y - 12
                            contentStream.newLineAtOffset(pdfX, pdfY)
                            contentStream.showText(annotation.text ?: "")
                            contentStream.endText()
                        }
                        AnnotationType.DRAW, AnnotationType.HIGHLIGHT -> {
                            val r = ((annotation.color shr 16) and 0xFF) / 255f
                            val g = ((annotation.color shr 8) and 0xFF) / 255f
                            val b = (annotation.color and 0xFF) / 255f
                            contentStream.setStrokingColor(r, g, b)
                            contentStream.setLineWidth(annotation.strokeWidth)
                            if (annotation.type == AnnotationType.HIGHLIGHT) {
                                // Transparency via graphics state is complex; use a thick stroke with color
                                contentStream.setLineWidth(annotation.strokeWidth * 5)
                            }
                            // Path drawing via PDFBox requires iterating path segments
                            annotation.path?.let { path ->
                                drawPathToPdf(contentStream, path, pageHeight)
                                contentStream.stroke()
                            }
                        }
                        AnnotationType.RECTANGLE -> {
                            val r = ((annotation.color shr 16) and 0xFF) / 255f
                            val g = ((annotation.color shr 8) and 0xFF) / 255f
                            val b = (annotation.color and 0xFF) / 255f
                            contentStream.setStrokingColor(r, g, b)
                            contentStream.setLineWidth(annotation.strokeWidth)
                            val x = annotation.x
                            val y = pageHeight - annotation.y2
                            val w = annotation.x2 - annotation.x
                            val h = annotation.y2 - annotation.y
                            contentStream.addRect(x, y, w, h)
                            contentStream.stroke()
                        }
                        AnnotationType.ARROW -> {
                            val r = ((annotation.color shr 16) and 0xFF) / 255f
                            val g = ((annotation.color shr 8) and 0xFF) / 255f
                            val b = (annotation.color and 0xFF) / 255f
                            contentStream.setStrokingColor(r, g, b)
                            contentStream.setLineWidth(annotation.strokeWidth)
                            val sy = pageHeight - annotation.y
                            val ey = pageHeight - annotation.y2
                            contentStream.moveTo(annotation.x, sy)
                            contentStream.lineTo(annotation.x2, ey)
                            contentStream.stroke()
                        }
                    }
                }
                contentStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        document.save(pdfFile)
        document.close()
    }

    private fun drawPathToPdf(
        contentStream: PDPageContentStream,
        path: Path,
        pageHeight: Float
    ) {
        // Extract path points using PathMeasure
        val pathMeasure = PathMeasure(path, false)
        val coords = FloatArray(2)
        val pos = FloatArray(2)
        var isFirst = true
        var distance = 0f
        val step = 5f
        val length = pathMeasure.length

        while (distance <= length) {
            pathMeasure.getPosTan(distance, pos, null)
            val x = pos[0]
            val y = pageHeight - pos[1]
            if (isFirst) {
                contentStream.moveTo(x, y)
                isFirst = false
            } else {
                contentStream.lineTo(x, y)
            }
            distance += step
        }
    }

    fun writeFileToUri(context: Context, file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }
}
