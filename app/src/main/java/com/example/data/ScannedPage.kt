package com.example.data

import android.graphics.RectF
import com.example.services.FilterService

data class ScannedPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalPath: String,
    val enhancedPath: String,
    val filterType: FilterService.FilterType = FilterService.FilterType.ORIGINAL,
    val cropRect: RectF = RectF(0f, 0f, 1f, 1f),
    val rotation: Float = 0f
)
