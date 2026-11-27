package com.paco.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/** Helpers that keep page images upright and a sensible size. */
internal object ImageFiles {
    private const val MAX_SIDE = 3000

    /**
     * Phone cameras (Samsung especially) often save photos sideways and only mark the
     * rotation in EXIF data, which BitmapFactory ignores. This bakes the rotation into the
     * pixels, shrinks very large photos, and rewrites the file as a plain upright JPEG.
     * Returns false if the file isn't a readable image.
     */
    fun normalize(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val matrix = orientationMatrix(file)
        val tooBig = max(bounds.outWidth, bounds.outHeight) > MAX_SIDE
        val isJpeg = bounds.outMimeType == "image/jpeg"
        if (matrix == null && !tooBig && isJpeg) return true

        val sample = max(1, ceil(max(bounds.outWidth, bounds.outHeight) / MAX_SIDE.toDouble()).toInt())
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        val upright = if (matrix != null) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return true
    }

    private fun orientationMatrix(file: File): Matrix? {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }

    /** Decodes an image no larger than [maxSide] on its longest edge, to keep PDFs small. */
    fun decodeForPdf(file: File, maxSide: Int = 2000): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
