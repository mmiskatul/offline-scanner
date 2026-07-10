package com.example.services

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ScannerService {

    fun initDirectories(context: Context) {
        val dirs = listOf("Documents", "PDFs", "Images", "Thumbnails", "OCR", "Temp")
        for (dirName in dirs) {
            val dir = File(context.filesDir, dirName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    fun getSubDir(context: Context, dirName: String): File {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyUriToLocal(context: Context, uri: Uri, dirName: String): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val fileDir = getSubDir(context, dirName)
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val targetFile = File(fileDir, fileName)
            
            inputStream?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearCache(context: Context) {
        try {
            val tempDir = File(context.filesDir, "Temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                tempDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCacheSize(context: Context): Long {
        return try {
            val tempDir = File(context.filesDir, "Temp")
            if (tempDir.exists()) {
                getFolderSize(tempDir)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun getAppStorageSize(context: Context): Long {
        return try {
            getFolderSize(context.filesDir)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }
}
