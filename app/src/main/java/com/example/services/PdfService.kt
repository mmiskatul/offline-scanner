package com.example.services

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PdfService {

    fun generatePdfFromImages(
        context: Context,
        imagePaths: List<String>,
        pdfName: String,
        addPageNumbers: Boolean = true,
        watermarkText: String? = null,
        compressBeforePdf: Boolean = true
    ): File? {
        val pdfDir = ScannerService.getSubDir(context, "PDFs")
        // Sanitize name
        val sanitizedName = if (pdfName.endsWith(".pdf", ignoreCase = true)) pdfName else "$pdfName.pdf"
        val pdfFile = File(pdfDir, sanitizedName)

        val pdfDocument = PdfDocument()

        try {
            for ((index, path) in imagePaths.withIndex()) {
                // Determine whether to compress/resize
                val processedPath = if (compressBeforePdf) {
                    ImageService.resizeImageForPdf(path, 1000, 75) ?: path
                } else {
                    path
                }

                val bitmap = ImageService.loadBitmap(processedPath) ?: continue

                // Define standard margins and page sizes
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Draw main scanned bitmap onto PDF page
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                // Draw watermark if configured
                if (!watermarkText.isNullOrEmpty()) {
                    val watermarkPaint = Paint().apply {
                        color = Color.argb(80, 180, 180, 180) // semi-transparent gray
                        textSize = (bitmap.width * 0.04f).coerceAtLeast(24f)
                        isAntiAlias = true
                        style = Paint.Style.FILL
                    }
                    canvas.drawText(watermarkText, bitmap.width * 0.1f, bitmap.height * 0.92f, watermarkPaint)
                }

                // Draw page index numbers if configured
                if (addPageNumbers) {
                    val pageNumPaint = Paint().apply {
                        color = Color.argb(180, 50, 50, 50)
                        textSize = (bitmap.width * 0.035f).coerceAtLeast(20f)
                        isAntiAlias = true
                    }
                    val text = "Page ${index + 1} of ${imagePaths.size}"
                    canvas.drawText(text, bitmap.width * 0.8f, bitmap.height * 0.95f, pageNumPaint)
                }

                pdfDocument.finishPage(page)
                bitmap.recycle()

                // If a temporary resized file was created, clean it up
                if (compressBeforePdf && processedPath != path) {
                    val tempResizedFile = File(processedPath)
                    if (tempResizedFile.exists()) {
                        tempResizedFile.delete()
                    }
                }
            }

            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            return pdfFile

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    fun getPdfFileSize(pdfPath: String): Long {
        val file = File(pdfPath)
        return if (file.exists()) file.length() else 0L
    }

    fun deletePdf(pdfPath: String): Boolean {
        return try {
            val file = File(pdfPath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
