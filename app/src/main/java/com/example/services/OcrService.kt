package com.example.services

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrService {

    // Custom helper to await Play Services Task results in Kotlin Coroutines
    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

    suspend fun extractTextFromImage(context: Context, imagePath: String): String {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return ""
            
            val inputImage = InputImage.fromFilePath(context, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(inputImage).awaitTask()
            
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun extractTextFromPages(context: Context, imagePaths: List<String>): List<String> {
        val pageTexts = mutableListOf<String>()
        for (path in imagePaths) {
            val text = extractTextFromImage(context, path)
            pageTexts.add(text)
        }
        return pageTexts
    }

    fun searchOcrText(pageTexts: List<String>, query: String): List<Int> {
        if (query.isEmpty()) return emptyList()
        val matchingPages = mutableListOf<Int>()
        for ((index, text) in pageTexts.withIndex()) {
            if (text.contains(query, ignoreCase = true)) {
                matchingPages.add(index)
            }
        }
        return matchingPages
    }

    fun exportTextFile(context: Context, documentName: String, text: String): String? {
        return try {
            val ocrDir = ScannerService.getSubDir(context, "OCR")
            val sanitizedName = documentName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val txtFile = File(ocrDir, "${sanitizedName}_${UUID.randomUUID().toString().take(6)}.txt")
            
            FileOutputStream(txtFile).use { fos ->
                fos.write(text.toByteArray())
            }
            txtFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
