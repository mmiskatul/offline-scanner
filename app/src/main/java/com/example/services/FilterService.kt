package com.example.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object FilterService {

    enum class FilterType(val displayName: String) {
        ORIGINAL("Original"),
        AUTO_ENHANCE("Auto Enhance"),
        BLACK_WHITE("Black & White"),
        HIGH_CONTRAST("High Contrast"),
        GRAYSCALE("Grayscale"),
        LIGHTEN("Lighten"),
        DARKEN("Darken"),
        SHARP_TEXT("Sharp Text"),
        SHADOW_REMOVE("Shadow Remove"),
        RECEIPT_MODE("Receipt Mode"),
        INVOICE_MODE("Invoice Mode"),
        ID_CARD_MODE("ID Card Mode"),
        HANDWRITING_MODE("Handwriting Mode"),
        LOW_LIGHT_FIX("Low Light Fix"),
        SOFT_CLEAN("Soft Clean"),
        STRONG_CLEAN("Strong Clean"),
        COLOR_DOCUMENT("Color Document"),
        BLUE_INK_BOOST("Blue Ink Boost"),
        PENCIL_NOTE_BOOST("Pencil Note Boost"),
        PRINT_TEXT_BOOST("Print Text Boost")
    }

    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        val colorMatrix = when (filterType) {
            FilterType.ORIGINAL -> return bitmap
            FilterType.AUTO_ENHANCE -> getAutoEnhanceMatrix()
            FilterType.BLACK_WHITE -> getBlackWhiteMatrix()
            FilterType.HIGH_CONTRAST -> getHighContrastMatrix(1.5f)
            FilterType.GRAYSCALE -> getGrayscaleMatrix()
            FilterType.LIGHTEN -> getBrightnessMatrix(40f)
            FilterType.DARKEN -> getBrightnessMatrix(-40f)
            FilterType.SHARP_TEXT -> getSharpTextMatrix()
            FilterType.SHADOW_REMOVE -> getShadowRemoveMatrix()
            FilterType.RECEIPT_MODE -> getReceiptMatrix()
            FilterType.INVOICE_MODE -> getInvoiceMatrix()
            FilterType.ID_CARD_MODE -> getIdCardMatrix()
            FilterType.HANDWRITING_MODE -> getHandwritingMatrix()
            FilterType.LOW_LIGHT_FIX -> getLowLightFixMatrix()
            FilterType.SOFT_CLEAN -> getSoftCleanMatrix()
            FilterType.STRONG_CLEAN -> getStrongCleanMatrix()
            FilterType.COLOR_DOCUMENT -> getColorDocumentMatrix()
            FilterType.BLUE_INK_BOOST -> getBlueInkBoostMatrix()
            FilterType.PENCIL_NOTE_BOOST -> getPencilNoteMatrix()
            FilterType.PRINT_TEXT_BOOST -> getPrintTextMatrix()
        }

        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return resultBitmap
    }

    private fun getGrayscaleMatrix(): ColorMatrix {
        return ColorMatrix().apply { setSaturation(0f) }
    }

    private fun getHighContrastMatrix(contrast: Float): ColorMatrix {
        val scale = contrast
        val translate = -128f * scale + 128f
        return ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getBrightnessMatrix(brightness: Float): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getAutoEnhanceMatrix(): ColorMatrix {
        val contrast = 1.25f
        val translate = -128f * contrast + 128f
        val matrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 15f,
            0f, contrast, 0f, 0f, translate + 15f,
            0f, 0f, contrast, 0f, translate + 15f,
            0f, 0f, 0f, 1f, 0f
        ))
        val sat = ColorMatrix()
        sat.setSaturation(1.3f)
        matrix.postConcat(sat)
        return matrix
    }

    private fun getBlackWhiteMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 2.8f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getSharpTextMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 2.2f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate - 10f,
            0f, contrast, 0f, 0f, translate - 10f,
            0f, 0f, contrast, 0f, translate - 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getShadowRemoveMatrix(): ColorMatrix {
        // High brightness boost to remove shadows, small contrast boost
        return ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 45f,
            0f, 1.1f, 0f, 0f, 45f,
            0f, 0f, 1.1f, 0f, 45f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getReceiptMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 3.0f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate - 20f,
            0f, contrast, 0f, 0f, translate - 20f,
            0f, 0f, contrast, 0f, translate - 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getInvoiceMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0.1f)
        val contrast = 2.4f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 10f,
            0f, contrast, 0f, 0f, translate + 10f,
            0f, 0f, contrast, 0f, translate + 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getIdCardMatrix(): ColorMatrix {
        val contrast = 1.3f
        val translate = -128f * contrast + 128f
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 10f,
            0f, contrast, 0f, 0f, translate + 10f,
            0f, 0f, contrast, 0f, translate + 10f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getHandwritingMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        // Boost blue channel, reduce red/green to make blue ink pop on white
        val colorMatrix = ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 20f,
            0f, 0.9f, 0f, 0f, 20f,
            0f, 0f, 1.4f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(colorMatrix)
        return matrix
    }

    private fun getLowLightFixMatrix(): ColorMatrix {
        // Brightness and highlight lift
        return ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 50f,
            0f, 1.2f, 0f, 0f, 50f,
            0f, 0f, 1.2f, 0f, 50f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getSoftCleanMatrix(): ColorMatrix {
        val contrast = 1.15f
        val translate = -128f * contrast + 128f
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 25f,
            0f, contrast, 0f, 0f, translate + 25f,
            0f, 0f, contrast, 0f, translate + 25f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getStrongCleanMatrix(): ColorMatrix {
        val contrast = 1.4f
        val translate = -128f * contrast + 128f
        return ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 50f,
            0f, contrast, 0f, 0f, translate + 50f,
            0f, 0f, contrast, 0f, translate + 50f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getColorDocumentMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(1.6f)
        val contrast = 1.1f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 5f,
            0f, contrast, 0f, 0f, translate + 5f,
            0f, 0f, contrast, 0f, translate + 5f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getBlueInkBoostMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            0.7f, 0f, 0f, 0f, 10f,
            0f, 0.7f, 0f, 0f, 10f,
            0f, 0f, 1.7f, 0f, 50f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun getPencilNoteMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 1.6f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate + 35f,
            0f, contrast, 0f, 0f, translate + 35f,
            0f, 0f, contrast, 0f, translate + 35f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }

    private fun getPrintTextMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 3.5f
        val translate = -128f * contrast + 128f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate - 30f,
            0f, contrast, 0f, 0f, translate - 30f,
            0f, 0f, contrast, 0f, translate - 30f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)
        return matrix
    }
}
