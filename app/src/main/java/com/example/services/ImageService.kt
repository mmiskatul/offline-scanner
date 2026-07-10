package com.example.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageService {

    fun loadBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmap(bitmap: Bitmap, targetFile: File, quality: Int = 85): Boolean {
        return try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun rotateImage(imagePath: String, degrees: Float): String? {
        return try {
            val bitmap = loadBitmap(imagePath) ?: return null
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            
            val file = File(imagePath)
            if (saveBitmap(rotatedBitmap, file)) {
                if (bitmap != rotatedBitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap.recycle()
                imagePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cropImage(imagePath: String, rect: RectF): String? {
        return try {
            val bitmap = loadBitmap(imagePath) ?: return null
            
            // Map normalized coordinates (0..1) to actual pixel bounds
            val left = (rect.left * bitmap.width).toInt().coerceAtLeast(0)
            val top = (rect.top * bitmap.height).toInt().coerceAtLeast(0)
            val width = (rect.width() * bitmap.width).toInt().coerceAtMost(bitmap.width - left)
            val height = (rect.height() * bitmap.height).toInt().coerceAtMost(bitmap.height - top)
            
            if (width <= 0 || height <= 0) return imagePath // Keep original if crop invalid

            val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
            
            // Overwrite or save as new
            val parentDir = File(imagePath).parentFile
            val newFile = File(parentDir, "crop_${UUID.randomUUID()}.jpg")
            
            if (saveBitmap(croppedBitmap, newFile)) {
                bitmap.recycle()
                croppedBitmap.recycle()
                newFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createThumbnail(context: Context, imagePath: String): String? {
        return try {
            val bitmap = loadBitmap(imagePath) ?: return null
            val maxDim = 300
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val w = if (aspect > 1) maxDim else (maxDim * aspect).toInt()
            val h = if (aspect > 1) (maxDim / aspect).toInt() else maxDim
            
            val thumb = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val thumbDir = ScannerService.getSubDir(context, "Thumbnails")
            val thumbFile = File(thumbDir, "thumb_${UUID.randomUUID()}.jpg")
            
            if (saveBitmap(thumb, thumbFile, 60)) {
                bitmap.recycle()
                thumb.recycle()
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun resizeImageForPdf(imagePath: String, maxDimension: Int = 1200, quality: Int = 80): String? {
        return try {
            val bitmap = loadBitmap(imagePath) ?: return null
            if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
                return imagePath // No resize needed
            }
            
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val w = if (aspect > 1) maxDimension else (maxDimension * aspect).toInt()
            val h = if (aspect > 1) (maxDimension / aspect).toInt() else maxDimension
            
            val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val parent = File(imagePath).parentFile
            val resizedFile = File(parent, "resized_${UUID.randomUUID()}.jpg")
            
            if (saveBitmap(resized, resizedFile, quality)) {
                bitmap.recycle()
                resized.recycle()
                resizedFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
