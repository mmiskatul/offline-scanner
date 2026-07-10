package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pdfPath: String?,
    val pageCount: Int,
    val fileSize: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: Long?,
    val ocrText: String, // Full searchable OCR text across all pages
    val thumbnailPath: String,
    val originalImagePaths: String, // Comma-separated list of local file paths
    val enhancedImagePaths: String  // Comma-separated list of local file paths
) {
    // Helper to get image path lists
    val originalPathsList: List<String>
        get() = if (originalImagePaths.isEmpty()) emptyList() else originalImagePaths.split(",")

    val enhancedPathsList: List<String>
        get() = if (enhancedImagePaths.isEmpty()) emptyList() else enhancedImagePaths.split(",")
}
